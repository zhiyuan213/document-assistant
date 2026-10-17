package cn.dengkemiao.docs;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Results live in MySQL so polling/downloads work across cloud replicas. */
@Service
public class PdfTranslationJobs {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new SynchronousQueue<>());
    private final String worker=UUID.randomUUID().toString();
    public PdfTranslationJobs(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    @FunctionalInterface interface Work { Completed run(java.util.function.IntConsumer progress) throws Exception; }
    record Completed(byte[] pdf,byte[] word,Map<String,Object> result) {}
    Map<String,Object> existing(String owner,String requestId) {
        var rows=db.queryForList("SELECT id,status,progress FROM pdf_translation_job WHERE visitor_id=? AND client_request_id=?",owner,requestId);
        if(rows.isEmpty())return null;
        var row=rows.getFirst();return Map.of("translationJobId",row.get("id"),"status",row.get("status"),"progress",row.get("progress"));
    }
    Map<String,Object> start(String owner,String requestId,Work work) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pdf_translation_job(id,visitor_id,client_request_id,status,progress,worker_id,expires_at) VALUES (?,?,?,'queued',0,?,TIMESTAMPADD(MINUTE,90,CURRENT_TIMESTAMP))",id,owner,requestId,worker);
        try { workers.execute(()->execute(id,work)); }
        catch(RejectedExecutionException e){db.update("DELETE FROM pdf_translation_job WHERE id=?",id);throw new ApiError(429,"文档翻译繁忙，请稍后再试");}
        return Map.of("translationJobId",id,"status","queued","progress",0);
    }
    private void execute(String id,Work work) {
        try {
            db.update("UPDATE pdf_translation_job SET status='processing',progress=5 WHERE id=?",id);
            var output=work.run(percent->db.update("UPDATE pdf_translation_job SET progress=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='processing'",percent,id));
            db.update("UPDATE pdf_translation_job SET status='completed',progress=100,pdf_data=?,word_data=?,result_json=?,expires_at=TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='processing'",
                    output.pdf,output.word,json.writeValueAsString(output.result),id);
        } catch(Exception e) {
            String message=e instanceof ApiError?e.getMessage():"翻译处理失败，请稍后重试；已调用AI的部分按用量结算";
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("PDF translation job {} failed ({})",id,e.getClass().getSimpleName());
            db.update("UPDATE pdf_translation_job SET status='failed',error_message=?,expires_at=TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE id=?",message,id);
        }
    }
    Map<String,Object> state(String owner,String id)throws Exception {
        var rows=db.queryForList("SELECT status,progress,error_message,result_json,TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP,expires_at) AS remaining_seconds FROM pdf_translation_job WHERE id=? AND visitor_id=? AND expires_at>CURRENT_TIMESTAMP",id,owner);
        if(rows.isEmpty())throw new ApiError(404,"翻译任务已过期或不存在，请重新处理");
        var row=rows.getFirst();var result=new HashMap<String,Object>();
        result.put("expiresAt",java.time.Instant.now().plusSeconds(((Number)row.get("remaining_seconds")).longValue()).toString());
        result.put("translationJobId",id);result.put("status",row.get("status"));result.put("progress",row.get("progress"));
        if(row.get("error_message")!=null)result.put("message",row.get("error_message"));
        if(row.get("result_json")!=null)result.putAll(json.readValue(row.get("result_json").toString(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
        return result;
    }
    ResponseEntity<byte[]> download(String owner,String id,String format)throws Exception {
        if(!Set.of("pdf","docx","txt").contains(format))throw new ApiError(400,"请选择PDF、Word或文本格式");
        var state=state(owner,id);if(!"completed".equals(state.get("status")))throw new ApiError(409,"翻译文件尚未完成");
        if(format.equals("pdf")&&Boolean.FALSE.equals(state.get("pdfAvailable")))throw new ApiError(422,"本次未能保持原版式，未生成PDF；请下载文本或Word译文");
        byte[] bytes;
        if(format.equals("txt"))bytes=("\uFEFF"+state.get("answer")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        else bytes=db.queryForObject("SELECT "+(format.equals("pdf")?"pdf_data":"word_data")+" FROM pdf_translation_job WHERE id=? AND visitor_id=?",byte[].class,id,owner);
        if(bytes==null)throw new ApiError(410,"翻译文件已清理，请重新处理");
        String type=format.equals("pdf")?"application/pdf":format.equals("docx")?"application/vnd.openxmlformats-officedocument.wordprocessingml.document":"text/plain;charset=utf-8";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).header("Content-Disposition","attachment; filename=\"translated-document."+format+"\"").body(bytes);
    }
    @Scheduled(fixedDelay=60000) public void cleanup() {
        try {
            // Expired processing jobs have unknown provider usage: never falsely refund or report success.
            db.update("UPDATE pdf_translation_job SET status='failed',error_message='任务中断或超时，请核对额度后重试',expires_at=TIMESTAMPADD(MINUTE,30,CURRENT_TIMESTAMP) WHERE status IN ('queued','processing') AND expires_at<=CURRENT_TIMESTAMP");
            db.update("DELETE FROM pdf_translation_job WHERE status IN ('completed','failed') AND expires_at<=CURRENT_TIMESTAMP");
        } catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Translation cleanup deferred ({})",e.getClass().getSimpleName());}
    }
    @PreDestroy public void stop(){workers.shutdown();}
}
