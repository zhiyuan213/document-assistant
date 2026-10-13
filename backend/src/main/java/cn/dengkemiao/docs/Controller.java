package cn.dengkemiao.docs;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class Controller {
    private final Auth auth; private final JdbcTemplate db; private final Quota quota;
    private final PdfService pdf; private final OcrService ocr; private final Storage storage;
    private final Semaphore processing = new Semaphore(2);
    @Value("${app.dev-login}") private boolean dev;
    public Controller(Auth auth, JdbcTemplate db, Quota quota, PdfService pdf, OcrService ocr, Storage storage) {
        this.auth=auth; this.db=db; this.quota=quota; this.pdf=pdf; this.ocr=ocr; this.storage=storage;
    }
    record LoginBody(String code, boolean development) {}
    @GetMapping("/health") public Map<String,Object> health() { return Map.of("status","ok","developmentLogin",dev); }
    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody LoginBody body) { return auth.login(body.code(),body.development()); }
    @GetMapping("/me") public Map<String,Object> me(@RequestHeader(value="Authorization",required=false) String header) {
        long user=auth.user(header);
        return Map.of("user",db.queryForMap("SELECT id,nickname FROM app_user WHERE id=?",user),"quota",quota.info(user));
    }
    @GetMapping("/files") public Map<String,Object> files(@RequestHeader(value="Authorization",required=false) String header,
            @RequestParam(defaultValue="all") String kind, @RequestParam(defaultValue="1") int page) {
        long user=auth.user(header); if (page<1 || page>10000 || !Set.of("all","pdf","txt").contains(kind)) throw new ApiError(400,"无效筛选参数");
        String filter = kind.equals("all") ? "" : " AND kind=?";
        Object[] parameters = kind.equals("all") ? new Object[]{user} : new Object[]{user,kind};
        var total=db.queryForObject("SELECT COUNT(*) FROM file_info WHERE user_id=?"+filter,Long.class,parameters);
        var args=Stream.concat(Arrays.stream(parameters),Stream.of((page-1)*20)).toArray();
        var items=db.queryForList("SELECT id,name,kind,size_bytes,source_bytes,created_at FROM file_info WHERE user_id=?"+filter+" ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",args);
        return Map.of("items",items,"total",total,"page",page);
    }
    private Map<String,Object> owned(long user,String id) {
        var rows=db.queryForList("SELECT * FROM file_info WHERE id=? AND user_id=?",id,user);
        if (rows.isEmpty()) throw new ApiError(404,"文件不存在"); return rows.getFirst();
    }
    @GetMapping("/files/{id}/download") public ResponseEntity<byte[]> download(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id) throws Exception {
        var file=owned(auth.user(header),id);
        return ResponseEntity.ok().contentType(file.get("kind").equals("pdf")?MediaType.APPLICATION_PDF:MediaType.TEXT_PLAIN)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.get("name").toString(),StandardCharsets.UTF_8).build().toString())
            .body(storage.get(file.get("storage_key").toString()));
    }
    @DeleteMapping("/files/{id}") public Map<String,Object> delete(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String id) throws Exception {
        long user=auth.user(header); var file=owned(user,id);
        storage.delete(file.get("storage_key").toString());
        db.update("DELETE FROM file_info WHERE id=? AND user_id=?",id,user); return Map.of("deleted",true);
    }
    @PostMapping(value="/process/{operation}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> process(@RequestHeader(value="Authorization",required=false) String header,@PathVariable String operation,
            @RequestParam("files") List<MultipartFile> files,@RequestParam(defaultValue="false") boolean landscape,
            @RequestParam(defaultValue="false") boolean highQuality) throws Exception {
        long user=auth.user(header);
        if (!Set.of("images","compress","merge","ocr").contains(operation)) throw new ApiError(404,"工具不存在");
        if (files.isEmpty() || files.stream().anyMatch(MultipartFile::isEmpty)) throw new ApiError(400,"请选择文件");
        if ((operation.equals("compress") || operation.equals("ocr")) && files.size()!=1) throw new ApiError(400,"此工具每次处理一个文件");
        if (files.size()>(operation.equals("merge")?10:20)) throw new ApiError(400,"文件数量超出限制");
        if (highQuality && !quota.premium(user)) throw new ApiError(403,"高清PDF需要高级权益");
        if (!processing.tryAcquire()) throw new ApiError(503,"当前处理任务较多，请稍后重试");
        String day=null, task=UUID.randomUUID().toString(), resultId=null, storageKey=null;
        try {
            day=quota.reserve(user);
            db.update("INSERT INTO file_task(id,user_id,operation,status) VALUES (?,?,?,'processing')",task,user,operation);
            List<byte[]> inputs=new ArrayList<>(); for (MultipartFile file:files) inputs.add(file.getBytes());
            long sourceSize=inputs.stream().mapToLong(b->b.length).sum();
            byte[] result; String text=null;
            if (operation.equals("ocr")) {
                if (inputs.getFirst().length>5*1024*1024) throw new ApiError(400,"OCR图片不能超过5MB");
                pdf.readImage(inputs.getFirst());
                text=ocr.recognize(inputs.getFirst()); result=text.getBytes(StandardCharsets.UTF_8);
            } else if (operation.equals("images")) result=pdf.images(inputs,landscape,highQuality);
            else if (operation.equals("merge")) result=pdf.merge(inputs);
            else result=pdf.compress(inputs.getFirst());
            String kind=operation.equals("ocr")?"txt":"pdf";
            resultId=UUID.randomUUID().toString(); storageKey=resultId+"."+kind;
            String name=switch(operation) { case "images"->"图片资料"; case "compress"->"压缩资料"; case "merge"->"合并资料"; default->"文字识别"; };
            name=name+"-"+java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))+"."+kind;
            storage.put(storageKey,result,kind.equals("pdf")?"application/pdf":"text/plain; charset=utf-8");
            db.update("INSERT INTO file_info(id,user_id,name,kind,storage_key,size_bytes,source_bytes) VALUES (?,?,?,?,?,?,?)",resultId,user,name,kind,storageKey,result.length,sourceSize);
            if (text!=null) db.update("INSERT INTO ocr_record(id,user_id,file_id,recognized_text) VALUES (?,?,?,?)",UUID.randomUUID().toString(),user,resultId,text);
            db.update("UPDATE file_task SET status='completed',result_id=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",resultId,task);
            Map<String,Object> response=new LinkedHashMap<>(); response.put("file",owned(user,resultId)); response.put("taskId",task);
            if (text!=null) response.put("text",text); response.put("savedBytes",Math.max(0,sourceSize-result.length)); return response;
        } catch (Exception e) {
            if (resultId!=null) db.update("DELETE FROM file_info WHERE id=? AND user_id=?",resultId,user);
            if (storageKey!=null) try { storage.delete(storageKey); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            quota.refund(user,day);
            db.update("UPDATE file_task SET status='failed',error_message=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",e instanceof ApiError?e.getMessage():"处理失败",task);
            throw e;
        } finally { processing.release(); }
    }
    @GetMapping("/product") public Map<String,Object> product() { return db.queryForMap("SELECT id,name,price_fen,enabled FROM product WHERE id=1"); }
    @PostMapping("/orders") public void orders(@RequestHeader(value="Authorization",required=false) String header) {
        auth.user(header); throw new ApiError(503,"微信支付尚未开放，暂不收款");
    }
}
