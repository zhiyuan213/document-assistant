package cn.dengkemiao.docs;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/pdf")
public class PdfToolController {
    private final JdbcTemplate db; private final PdfTools tools; private final TransactionTemplate tx; private final WechatPayService wechatPay; private final ObjectMapper json; private final PdfAiCredits aiCredits; private final PdfVirtualPayments virtualPayments;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private static final Set<String> TOOLS=Set.of("pdf-txt","pdf-word","word-pdf","merge","split","compress","images","pdf-images","watermark","encrypt","decrypt","rotate","extract","remove-pages","reorder","page-numbers","excel-pdf","ppt-pdf","crop","sign","extract-images","ocr","repair","markdown","compare","redact","remove-watermark");
    @Value("${PDF_PAYMENT_PROVIDER:virtual}") private String paymentProvider;
    private boolean virtualProvider(){return "virtual".equals(paymentProvider);}
    private boolean paymentReady(){return virtualProvider()?virtualPayments.enabled():"wechat".equals(paymentProvider)&&wechatPay.enabled();}
    @Value("${PDF_TEMP_DIR:./data/pdf-temp}") private String directory;
    @Value("${PDF_BILLING_ENABLED:false}") private boolean billingEnabled;
    @Value("${PDF_MONTHLY_PRICE_FEN:990}") private int monthlyPriceFen;
    public PdfToolController(JdbcTemplate db,PdfTools tools,PlatformTransactionManager manager,WechatPayService wechatPay,ObjectMapper json,PdfAiCredits aiCredits,PdfVirtualPayments virtualPayments){this.db=db;this.tools=tools;this.tx=new TransactionTemplate(manager);this.wechatPay=wechatPay;this.json=json;this.aiCredits=aiCredits;this.virtualPayments=virtualPayments;}
    private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    static LocalDateTime monthEnd(LocalDateTime purchasedAt){return purchasedAt.plusMonths(1);}
    private Map<String,Object> membership(String id){
        var rows=db.queryForList("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",id);
        LocalDateTime expires=rows.isEmpty()?null:JdbcDates.local(rows.get(0).get("expires_at"));
        boolean active=expires!=null&&expires.isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        var result=new HashMap<String,Object>();result.put("active",active);result.put("expiresAt",expires==null?null:expires.toString()+"+08:00");return result;
    }
    private String visitor(String header) {
        if(header==null||!header.startsWith("Bearer "))throw new ApiError(401,"设备凭证已过期，请重试");
        String hash=hash(header.substring(7));var ids=db.queryForList("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);
        if(ids.isEmpty())throw new ApiError(401,"设备凭证已过期，请重试");return ids.get(0);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    @PostMapping("/visitor") public Map<String,Object> create(){String id=UUID.randomUUID().toString(),token=UUID.randomUUID().toString()+UUID.randomUUID();db.update("INSERT INTO pdf_visitor(id,token_hash) VALUES (?,?)",id,hash(token));return Map.of("token",token);}
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("billingEnabled",billingEnabled,"dailyFree",-1,"retentionMinutes",30,"paymentEnabled",billingEnabled&&paymentReady(),"paymentProvider",paymentProvider,"adEnabled",false,"packages",List.of(Map.of("id","monthly","priceFen",virtualProvider()?990:Math.max(990,monthlyPriceFen),"months",1,"aiDailyLimit",50,"aiMonthlyTokenCredits",aiCredits.monthlyCap()),Map.of("id","ai-topup","priceFen",990,"tokenCredits",PdfAiCredits.TOPUP_CREDITS)));}
    @GetMapping("/quota") public Map<String,Object> quota(@RequestHeader(value="Authorization",required=false)String header){
        String id=aiCredits.owner(visitor(header));var member=membership(id);
        var result=new HashMap<String,Object>(Map.of("billingEnabled",billingEnabled,"paymentEnabled",billingEnabled&&paymentReady(),"paymentProvider",paymentProvider,"unlimited",true,"remaining",-1,"dailyLimit",-1,"monthlyPriceFen",virtualProvider()?990:Math.max(990,monthlyPriceFen),"unit","files","membership",member,"resetTimezone","Asia/Shanghai"));result.put("wechatLoginEnabled",virtualProvider()?virtualPayments.loginEnabled():wechatPay.enabled());return result;
    }
    private void refund(String id,LocalDate day,int files){if(day.equals(LocalDate.ofEpochDay(0)))return;db.update("UPDATE pdf_daily_quota SET used_count=GREATEST(0,used_count-1),used_files=GREATEST(0,used_files-?) WHERE visitor_id=? AND quota_date=?",files,id,day);}
    @PostMapping(value="/jobs",consumes="multipart/form-data") public Map<String,Object> submit(@RequestHeader(value="Authorization",required=false)String header,@RequestParam String tool,@RequestParam("files")List<MultipartFile> files,@RequestParam Map<String,String> params) throws IOException {
        String owner=visitor(header);if(!TOOLS.contains(tool))throw new ApiError(400,"未知PDF工具");
        if(files.isEmpty()||files.size()>20)throw new ApiError(400,"选择1至20个文件");
        List<byte[]> content=new ArrayList<>();long total=0;
        for(var file:files){if(file.isEmpty()||file.getSize()>20*1024*1024)throw new ApiError(400,"每个文件需小于20MB且不能为空");total+=file.getSize();if(total>60*1024*1024)throw new ApiError(400,"总文件大小不能超过60MB");content.add(file.getBytes());}
        if(workers.getQueue().remainingCapacity()==0)throw new ApiError(429,"当前处理繁忙，请稍后重试");
        // Epoch date means no quota was reserved, including when cleaning jobs after a config change.
        int units=files.size()-(tool.equals("sign")||tool.equals("watermark")&&"image".equals(params.get("watermarkMode"))?1:0);
        // The basic PDF tools are acquisition features and remain unlimited/free, regardless of AI VIP billing.
        LocalDate day=LocalDate.ofEpochDay(0);String id=UUID.randomUUID().toString();
        try {
            db.update("INSERT INTO pdf_tool_job(id,visitor_id,tool,status,progress,quota_date,quota_units,expires_at) VALUES (?,?,?,'queued',10,?,?,TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP))",id,owner,tool,day,units);
            String name=Optional.ofNullable(files.get(0).getOriginalFilename()).orElse("input");var options=new HashMap<>(params);options.remove("tool");
            workers.execute(()->process(id,owner,day,units,tool,content,name,options));return Map.of("id",id);
        } catch(Exception e){db.update("DELETE FROM pdf_tool_job WHERE id=?",id);refund(owner,day,units);if(e instanceof RejectedExecutionException)throw new ApiError(429,"当前处理繁忙，请稍后重试");throw e;}
    }
    private final java.util.concurrent.Semaphore watermarkSlots=new java.util.concurrent.Semaphore(2);
    @PostMapping(value="/watermarks/{action}",consumes="multipart/form-data") public Map<String,Object> watermarks(
        @RequestHeader(value="Authorization",required=false)String header,@PathVariable String action,@RequestParam("files")List<MultipartFile> files,@RequestParam(defaultValue="")String candidateIds)throws Exception {
        visitor(header);
        if(!Set.of("inspect","preview").contains(action))throw new ApiError(400,"操作无效");
        if(files.size()!=1||files.get(0).isEmpty()||files.get(0).getSize()>20L*1024*1024)throw new ApiError(400,"请选择一个20MB以内的PDF");
        if(!watermarkSlots.tryAcquire())throw new ApiError(429,"处理繁忙，请稍后重试");
        try{byte[] bytes=files.get(0).getBytes();return action.equals("inspect")?PdfWatermarks.inspect(bytes):PdfWatermarks.preview(bytes,candidateIds);}finally{watermarkSlots.release();}
    }
    private Path resultPath(String id){return Path.of(directory).toAbsolutePath().resolve(id+".bin");}
    private void process(String id,String owner,LocalDate day,int units,String tool,List<byte[]> content,String name,Map<String,String> options){
        try {
            db.update("UPDATE pdf_tool_job SET status='processing',progress=35,expires_at=expires_at WHERE id=?",id);
            var result=tools.run(tool,content,name,options);if(result.bytes().length>100*1024*1024)throw new ApiError(400,"输出文件超过100MB，请拆分处理");
            Files.createDirectories(resultPath(id).getParent());Files.write(resultPath(id),result.bytes());
            db.update("UPDATE pdf_tool_job SET status='completed',progress=100,extension=?,size_bytes=?,expires_at=TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP) WHERE id=?",result.extension(),result.bytes().length,id);
        } catch(Exception e) {try{Files.deleteIfExists(resultPath(id));}catch(IOException ignored){}
            String message=e instanceof ApiError?e.getMessage():"文件处理失败，请检查文件格式或是否损坏";
            db.update("UPDATE pdf_tool_job SET status='failed',error_message=?,expires_at=expires_at WHERE id=?",message,id);refund(owner,day,units);
        } finally {options.clear();content.clear();}
    }
    private Map<String,Object> owned(String id,String owner){var rows=db.queryForList("SELECT id,tool,status,progress,extension,size_bytes,error_message,expires_at,TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP,expires_at) AS remaining_seconds FROM pdf_tool_job WHERE id=? AND visitor_id=? AND expires_at>CURRENT_TIMESTAMP",id,owner);if(rows.isEmpty())throw new ApiError(404,"结果已过期或不存在，请重新处理");var result=rows.get(0);result.put("expiresAt",java.time.Instant.now().plusSeconds(((Number)result.get("remaining_seconds")).longValue()).toString());return result;}
    @GetMapping("/jobs/{id}") public Map<String,Object> state(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header){return owned(id,visitor(header));}
    @GetMapping("/jobs/{id}/file") public ResponseEntity<byte[]> download(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header) throws IOException {
        var job=owned(id,visitor(header));if(!"completed".equals(job.get("status")))throw new ApiError(409,"文件尚未完成");Path file=resultPath(id);if(!Files.isRegularFile(file))throw new ApiError(410,"临时文件已清理，请重新处理");String ext=job.get("extension").toString();return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("Content-Disposition","attachment; filename=\"dengke-result."+ext+"\"").body(Files.readAllBytes(file));
    }
    @DeleteMapping("/jobs/{id}") public Map<String,Object> remove(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header)throws IOException {var job=owned(id,visitor(header));if(Set.of("queued","processing").contains(job.get("status")))throw new ApiError(409,"文件正在处理");Files.deleteIfExists(resultPath(id));db.update("DELETE FROM pdf_tool_job WHERE id=?",id);return Map.of("ok",true);}
    @PostMapping("/login") public Map<String,Object> bindWechat(@RequestHeader(value="Authorization",required=false)String header,@RequestBody Map<String,String> body){
        String id=visitor(header);String code=body.get("code");if(code==null||code.isBlank())throw new ApiError(400,"微信登录凭证缺失");
        if(virtualProvider()){virtualPayments.bind(id,code);return Map.of("ok",true);}
        String openid=wechatPay.openId(code);db.update("UPDATE pdf_visitor SET wechat_openid=? WHERE id=?",openid,id);
        var rows=db.queryForList("SELECT MAX(m.expires_at) AS expires_at FROM pdf_membership m JOIN pdf_visitor v ON v.id=m.visitor_id WHERE v.wechat_openid=?",openid);
        Object expiry=rows.get(0).get("expires_at");
        if(expiry!=null)db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?) ON DUPLICATE KEY UPDATE expires_at=GREATEST(expires_at,VALUES(expires_at))",aiCredits.owner(id),expiry);
        return Map.of("ok",true);
    }
    @PostMapping("/purchase") public Map<String,Object> purchase(@RequestHeader(value="Authorization",required=false)String header,@RequestBody(required=false) Map<String,String> body){
        if(!billingEnabled)throw new ApiError(409,"AI 额度购买暂未开启");
        if(virtualProvider())return virtualPayments.purchase(visitor(header),body==null?"monthly":body.getOrDefault("packageId","monthly"),body==null?"":body.getOrDefault("code",""));
        if(!wechatPay.enabled())throw new ApiError(503,"微信支付配置未完成，暂不收款");
        String owner=aiCredits.owner(visitor(header));var openids=db.queryForList("SELECT wechat_openid FROM pdf_visitor WHERE id=?",String.class,owner);
        if(openids.isEmpty()||openids.get(0)==null||openids.get(0).isBlank())throw new ApiError(401,"请先完成微信身份校验");
        String packageId=body==null?"monthly":body.getOrDefault("packageId","monthly");
        if(!Set.of("monthly","ai-topup").contains(packageId))throw new ApiError(400,"请选择有效套餐");
        boolean topup=packageId.equals("ai-topup");int amount=topup?PdfAiCredits.TOPUP_PRICE_FEN:Math.max(990,monthlyPriceFen);
        String orderId=UUID.randomUUID().toString(),tradeNo="DK"+UUID.randomUUID().toString().replace("-","").substring(0,30);
        tx.executeWithoutResult(s->{
            db.queryForList("SELECT id FROM pdf_visitor WHERE id=? FOR UPDATE",owner);
            if(!topup)aiCredits.requireMonthlyPurchaseAllowed(owner);
    db.update("INSERT INTO pdf_purchase(id,visitor_id,package_id,amount_fen,credits,status,out_trade_no) VALUES (?,?,?,?,?,'pending',?)",orderId,owner,packageId,amount,topup?PdfAiCredits.TOPUP_CREDITS:1,tradeNo);
        });
        var pay=wechatPay.createMiniProgramOrder(tradeNo,openids.get(0),amount,topup?"登科喵 AI 加油包（100万额度）":"登科喵 AI 月卡");var result=new HashMap<String,Object>(pay);result.put("orderId",orderId);result.put("paymentProvider","wechat");return result;
    }
    @GetMapping("/purchase/{id}") public Map<String,Object> purchaseState(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header){
        String owner=aiCredits.owner(visitor(header));var rows=db.queryForList("SELECT status,paid_at FROM pdf_purchase WHERE id=? AND visitor_id=?",id,owner);
        if(rows.isEmpty())throw new ApiError(404,"订单不存在");if(virtualPayments.isVirtual(id)){virtualPayments.refresh(id);rows=db.queryForList("SELECT status,paid_at FROM pdf_purchase WHERE id=? AND visitor_id=?",id,owner);}
        var row=rows.get(0);return Map.of("paid","paid".equals(row.get("status")),"status",row.get("status"));
    }
    @PostMapping("/pay/notify") public ResponseEntity<Map<String,String>> paymentNotify(
            @RequestHeader(value="Wechatpay-Timestamp",required=false)String timestamp,
            @RequestHeader(value="Wechatpay-Nonce",required=false)String nonce,
            @RequestHeader(value="Wechatpay-Serial",required=false)String serial,
            @RequestHeader(value="Wechatpay-Signature",required=false)String signature,
            @RequestBody String body){
        try {
            JsonNode notice=wechatPay.verifyAndDecrypt(timestamp,nonce,serial,signature,body);
            if(!"SUCCESS".equals(notice.path("trade_state").asText())||!"JSAPI".equals(notice.path("trade_type").asText()))throw new SecurityException("payment not successful");
            if(!wechatPay.matchesMerchant(notice.path("appid").asText(),notice.path("mchid").asText()))throw new SecurityException("merchant mismatch");
            String tradeNo=notice.path("out_trade_no").asText(),transactionId=notice.path("transaction_id").asText();int total=notice.path("amount").path("total").asInt(-1);
            tx.executeWithoutResult(status->{var orders=db.queryForList("SELECT id,visitor_id,package_id,credits,amount_fen,status,transaction_id FROM pdf_purchase WHERE out_trade_no=? FOR UPDATE",tradeNo);
                if(orders.isEmpty())throw new SecurityException("unknown order");var order=orders.get(0);
                if(virtualPayments.isVirtual(order.get("id").toString()))throw new SecurityException("wrong payment channel");
                if(((Number)order.get("amount_fen")).intValue()!=total)throw new SecurityException("amount mismatch");
                if("paid".equals(order.get("status"))){if(!transactionId.equals(order.get("transaction_id")))throw new SecurityException("transaction mismatch");return;}
                if(!"pending".equals(order.get("status")))throw new SecurityException("order is not payable");
                LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));String visitorId=aiCredits.owner(order.get("visitor_id").toString());
                db.queryForList("SELECT id FROM pdf_visitor WHERE id=? FOR UPDATE",visitorId);
                if("ai-topup".equals(order.get("package_id"))) {
                    if(total!=PdfAiCredits.TOPUP_PRICE_FEN)throw new SecurityException("invalid topup");
                    aiCredits.grantTopup(visitorId,PdfAiCredits.topupGrant(((Number)order.get("credits")).longValue()));
                } else if("monthly".equals(order.get("package_id"))) {
                    var members=db.queryForList("SELECT expires_at FROM pdf_membership WHERE visitor_id=?",visitorId);
                    LocalDateTime previous=members.isEmpty()?now:JdbcDates.local(members.get(0).get("expires_at"));
                    boolean renewing=previous.isAfter(now);
                    // Initialize the current period before extending an active membership.
                    if(renewing)aiCredits.snapshot(visitorId);
                    LocalDateTime expires=(renewing?previous:now).plusMonths(1);
                    db.update("INSERT INTO pdf_membership(visitor_id,expires_at) VALUES (?,?) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)",visitorId,Timestamp.valueOf(expires));
                    aiCredits.activateMonth(visitorId,expires,renewing);
                } else throw new SecurityException("unknown package");
                db.update("UPDATE pdf_purchase SET status='paid',transaction_id=?,paid_at=? WHERE id=?",transactionId,Timestamp.valueOf(now),order.get("id"));
            });
            return ResponseEntity.ok(Map.of("code","SUCCESS","message","成功"));
        } catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WeChat Pay notification rejected ({})",e.getClass().getSimpleName());return ResponseEntity.badRequest().body(Map.of("code","FAIL","message","通知校验失败"));}
    }
    @PostMapping("/ad-reward") public void reward(){throw new ApiError(503,"激励视频广告尚未开通，暂不发放次数");}
    @Scheduled(fixedDelay=60000) public void cleanup(){
        try {
            for(var row:db.queryForList("SELECT id,visitor_id,quota_date,quota_units,status FROM pdf_tool_job WHERE expires_at<=CURRENT_TIMESTAMP")) {
                String id=row.get("id").toString();
                Files.deleteIfExists(resultPath(id));
                tx.executeWithoutResult(s->{
                    if(Set.of("queued","processing").contains(row.get("status")) && db.update("UPDATE pdf_tool_job SET status='expired' WHERE id=? AND status IN ('queued','processing')",id)==1)
                        refund(row.get("visitor_id").toString(),LocalDate.parse(row.get("quota_date").toString()),((Number)row.get("quota_units")).intValue());
                    db.update("DELETE FROM pdf_tool_job WHERE id=?",id);
                });
            }
        } catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("PDF cleanup deferred ({})",e.getClass().getSimpleName());}
    }
    @EventListener(ApplicationReadyEvent.class) public void recover(){cleanup();}
    @PreDestroy public void stop(){workers.shutdown();}
}
