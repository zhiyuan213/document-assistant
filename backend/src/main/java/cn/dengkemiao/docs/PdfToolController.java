package cn.dengkemiao.docs;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PreDestroy;

@RestController
@RequestMapping("/api/pdf")
public class PdfToolController {
    private final JdbcTemplate db; private final PdfTools tools; private final TransactionTemplate tx;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4));
    private static final Set<String> TOOLS=Set.of("pdf-word","word-pdf","merge","split","compress","images","pdf-images","watermark","encrypt","decrypt");
    @Value("${PDF_TEMP_DIR:./data/pdf-temp}") private String directory;
    @Value("${PDF_BILLING_ENABLED:false}") private boolean billingEnabled;
    public PdfToolController(JdbcTemplate db,PdfTools tools,PlatformTransactionManager manager){this.db=db;this.tools=tools;this.tx=new TransactionTemplate(manager);}
    private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    private String visitor(String header) {
        if(header==null||!header.startsWith("Bearer "))throw new ApiError(401,"设备凭证已过期，请重试");
        String hash=hash(header.substring(7));var ids=db.queryForList("SELECT id FROM pdf_visitor WHERE token_hash=?",String.class,hash);
        if(ids.isEmpty())throw new ApiError(401,"设备凭证已过期，请重试");return ids.get(0);
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    @PostMapping("/visitor") public Map<String,Object> create(){String id=UUID.randomUUID().toString(),token=UUID.randomUUID().toString()+UUID.randomUUID();db.update("INSERT INTO pdf_visitor(id,token_hash) VALUES (?,?)",id,hash(token));return Map.of("token",token);}
    @GetMapping("/config") public Map<String,Object> config(){return Map.of("billingEnabled",billingEnabled,"dailyFree",1,"retentionMinutes",30,"paymentEnabled",false,"adEnabled",false,"packages",List.of(Map.of("id","six","priceFen",600,"credits",6),Map.of("id","eighteen","priceFen",1200,"credits",18)));}
    @GetMapping("/quota") public Map<String,Object> quota(@RequestHeader(value="Authorization",required=false)String header){String id=visitor(header);if(!billingEnabled)return Map.of("billingEnabled",false,"unlimited",true,"remaining",-1,"resetTimezone","Asia/Shanghai");var counts=db.queryForList("SELECT used_count FROM pdf_daily_quota WHERE visitor_id=? AND quota_date=?",Integer.class,id,today());return Map.of("billingEnabled",true,"unlimited",false,"remaining",counts.isEmpty()?1:Math.max(0,1-counts.get(0)),"resetTimezone","Asia/Shanghai");}
    private void reserve(String id,LocalDate day){tx.executeWithoutResult(status->{db.update("INSERT INTO pdf_daily_quota(visitor_id,quota_date,used_count) VALUES (?,?,0) ON DUPLICATE KEY UPDATE visitor_id=VALUES(visitor_id)",id,day);if(db.update("UPDATE pdf_daily_quota SET used_count=used_count+1 WHERE visitor_id=? AND quota_date=? AND used_count<1",id,day)!=1)throw new ApiError(402,"今日免费次数已用完，可购买次数或观看广告；支付与广告暂未开通");});}
    private void refund(String id,LocalDate day){if(day.equals(LocalDate.ofEpochDay(0)))return;db.update("UPDATE pdf_daily_quota SET used_count=GREATEST(0,used_count-1) WHERE visitor_id=? AND quota_date=?",id,day);}
    @PostMapping(value="/jobs",consumes="multipart/form-data") public Map<String,Object> submit(@RequestHeader(value="Authorization",required=false)String header,@RequestParam String tool,@RequestParam("files")List<MultipartFile> files,@RequestParam Map<String,String> params) throws IOException {
        String owner=visitor(header);if(!TOOLS.contains(tool))throw new ApiError(400,"未知PDF工具");
        if(files.isEmpty()||files.size()>20)throw new ApiError(400,"选择1至20个文件");
        List<byte[]> content=new ArrayList<>();long total=0;
        for(var file:files){if(file.isEmpty()||file.getSize()>20*1024*1024)throw new ApiError(400,"每个文件需小于20MB且不能为空");total+=file.getSize();if(total>60*1024*1024)throw new ApiError(400,"总文件大小不能超过60MB");content.add(file.getBytes());}
        if(workers.getQueue().remainingCapacity()==0)throw new ApiError(429,"当前处理繁忙，请稍后重试");
        // Epoch date means no quota was reserved, including when cleaning jobs after a config change.
        LocalDate day=billingEnabled?today():LocalDate.ofEpochDay(0);if(billingEnabled)reserve(owner,day);String id=UUID.randomUUID().toString();
        try {
            db.update("INSERT INTO pdf_tool_job(id,visitor_id,tool,status,progress,quota_date,expires_at) VALUES (?,?,?,'queued',10,?,?)",id,owner,tool,day,java.sql.Timestamp.from(Instant.now().plusSeconds(1800)));
            String name=Optional.ofNullable(files.get(0).getOriginalFilename()).orElse("input");var options=new HashMap<>(params);options.remove("tool");
            workers.execute(()->process(id,owner,day,tool,content,name,options));return Map.of("id",id);
        } catch(Exception e){db.update("DELETE FROM pdf_tool_job WHERE id=?",id);refund(owner,day);if(e instanceof RejectedExecutionException)throw new ApiError(429,"当前处理繁忙，请稍后重试");throw e;}
    }
    private Path resultPath(String id){return Path.of(directory).toAbsolutePath().resolve(id+".bin");}
    private void process(String id,String owner,LocalDate day,String tool,List<byte[]> content,String name,Map<String,String> options){
        try {
            db.update("UPDATE pdf_tool_job SET status='processing',progress=35 WHERE id=?",id);
            var result=tools.run(tool,content,name,options);if(result.bytes().length>100*1024*1024)throw new ApiError(400,"输出文件超过100MB，请拆分处理");
            Files.createDirectories(resultPath(id).getParent());Files.write(resultPath(id),result.bytes());
            db.update("UPDATE pdf_tool_job SET status='completed',progress=100,extension=?,size_bytes=?,expires_at=? WHERE id=?",result.extension(),result.bytes().length,java.sql.Timestamp.from(Instant.now().plusSeconds(1800)),id);
        } catch(Exception e) {try{Files.deleteIfExists(resultPath(id));}catch(IOException ignored){}
            String message=e instanceof ApiError?e.getMessage():"文件处理失败，请检查文件格式或是否损坏";
            db.update("UPDATE pdf_tool_job SET status='failed',error_message=? WHERE id=?",message,id);refund(owner,day);
        } finally {options.clear();content.clear();}
    }
    private Map<String,Object> owned(String id,String owner){var rows=db.queryForList("SELECT id,tool,status,progress,extension,size_bytes,error_message,expires_at FROM pdf_tool_job WHERE id=? AND visitor_id=? AND expires_at>CURRENT_TIMESTAMP",id,owner);if(rows.isEmpty())throw new ApiError(404,"结果已过期或不存在，请重新处理");return rows.get(0);}
    @GetMapping("/jobs/{id}") public Map<String,Object> state(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header){return owned(id,visitor(header));}
    @GetMapping("/jobs/{id}/file") public ResponseEntity<byte[]> download(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header) throws IOException {
        var job=owned(id,visitor(header));if(!"completed".equals(job.get("status")))throw new ApiError(409,"文件尚未完成");Path file=resultPath(id);if(!Files.isRegularFile(file))throw new ApiError(410,"临时文件已清理，请重新处理");String ext=job.get("extension").toString();return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header("Content-Disposition","attachment; filename=\"dengke-result."+ext+"\"").body(Files.readAllBytes(file));
    }
    @DeleteMapping("/jobs/{id}") public Map<String,Object> remove(@PathVariable String id,@RequestHeader(value="Authorization",required=false)String header)throws IOException {var job=owned(id,visitor(header));if(Set.of("queued","processing").contains(job.get("status")))throw new ApiError(409,"文件正在处理");Files.deleteIfExists(resultPath(id));db.update("DELETE FROM pdf_tool_job WHERE id=?",id);return Map.of("ok",true);}
    @PostMapping("/purchase") public void purchase(){throw new ApiError(503,"微信支付尚未开通，暂不收款");}
    @PostMapping("/ad-reward") public void reward(){throw new ApiError(503,"激励视频广告尚未开通，暂不发放次数");}
    @Scheduled(fixedDelay=60000) public void cleanup(){
        try {
            for(var row:db.queryForList("SELECT id,visitor_id,quota_date,status FROM pdf_tool_job WHERE expires_at<=CURRENT_TIMESTAMP")) {
                String id=row.get("id").toString();
                Files.deleteIfExists(resultPath(id));
                tx.executeWithoutResult(s->{
                    if(Set.of("queued","processing").contains(row.get("status")) && db.update("UPDATE pdf_tool_job SET status='expired' WHERE id=? AND status IN ('queued','processing')",id)==1)
                        refund(row.get("visitor_id").toString(),LocalDate.parse(row.get("quota_date").toString()));
                    db.update("DELETE FROM pdf_tool_job WHERE id=?",id);
                });
            }
        } catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("PDF cleanup deferred ({})",e.getClass().getSimpleName());}
    }
    @EventListener(ApplicationReadyEvent.class) public void recover(){cleanup();}
    @PreDestroy public void stop(){workers.shutdown();}
}
