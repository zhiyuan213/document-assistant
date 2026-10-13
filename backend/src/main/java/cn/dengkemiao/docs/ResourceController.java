package cn.dengkemiao.docs;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
public class ResourceController {
    private final Auth auth;private final Resources resources;private final JdbcTemplate db;private final Quota quota;private final Storage storage;
    public ResourceController(Auth auth,Resources resources,JdbcTemplate db,Quota quota,Storage storage) { this.auth=auth;this.resources=resources;this.db=db;this.quota=quota;this.storage=storage; }
    @GetMapping("/categories") public List<Map<String,Object>> categories() { return resources.categories(); }
    @GetMapping("/resources") public Map<String,Object> search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String topic,@RequestParam(defaultValue="popular") String sort,@RequestParam(defaultValue="1") int page) {
        return resources.list(q,category,topic,sort,page,null,false);
    }
    @GetMapping("/resources/{id}") public Map<String,Object> detail(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String header) {
        var data=new LinkedHashMap<>(resources.item(id));data.remove("storage_key");
        data.put("favorite",header==null||header.equals("Bearer ")?false:resources.favorite(auth.user(header),id));
        var related=resources.list("",data.get("category_id").toString(),"","popular",1,null,false);
        @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)related.get("items");
        data.put("related",items.stream().filter(item->!item.get("id").equals(id)).limit(3).toList());return data;
    }
    @GetMapping("/favorites") public Map<String,Object> favorites(@RequestHeader(value="Authorization",required=false) String header,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="1") int page) {
        return resources.list(q,category,"","recent",page,auth.user(header),true);
    }
    @PostMapping("/resources/{id}/favorite") public Map<String,Object> favorite(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String header) {
        resources.addFavorite(auth.user(header),id);return Map.of("favorite",true);
    }
    @DeleteMapping("/resources/{id}/favorite") public Map<String,Object> unfavorite(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String header) {
        resources.removeFavorite(auth.user(header),id);return Map.of("favorite",false);
    }
    record DownloadRequest(String resourceId) {}
    record DownloadStatus(String status) {}
    private void access(long user,Map<String,Object> item) {
        if(!Boolean.TRUE.equals(item.get("published"))&&item.containsKey("published"))throw new ApiError(404,"资料已下架");
        if(!Boolean.TRUE.equals(item.get("is_free"))&&!quota.premium(user))throw new ApiError(403,"此资料需要会员权益");
    }
    @PostMapping("/downloads") public Map<String,Object> create(@RequestBody DownloadRequest body,@RequestHeader(value="Authorization",required=false) String header) {
        long user=auth.user(header);var item=resources.item(body.resourceId());access(user,item);
        String id=UUID.randomUUID().toString();db.update("INSERT INTO resource_download(id,user_id,resource_id,status) VALUES (?,?,?,'started')",id,user,body.resourceId());
        var result=new LinkedHashMap<>(resources.ownedDownload(user,id));result.remove("storage_key");return result;
    }
    @GetMapping("/downloads") public Map<String,Object> downloads(@RequestHeader(value="Authorization",required=false) String header,@RequestParam(defaultValue="1") int page) { return resources.downloads(auth.user(header),page); }
    @GetMapping("/downloads/{id}/file") public ResponseEntity<StreamingResponseBody> file(@PathVariable String id,@RequestHeader(value="Authorization",required=false) String header) throws Exception {
        long user=auth.user(header);var item=resources.ownedDownload(user,id);access(user,item);
        String kind=item.get("kind").toString();String name=item.get("name").toString();if(!name.toLowerCase().endsWith("."+kind))name+="."+kind;
        StreamingResponseBody body=output->{
            try(var input=storage.open(item.get("storage_key").toString())){
                byte[] buffer=new byte[64*1024];int read;boolean first=true;
                while((read=input.read(buffer))!=-1){output.write(buffer,0,read);if(first){resources.markServed(user,id,item.get("resource_id").toString());first=false;}}
                output.flush();
            }
        };
        MediaType mediaType=kind.equals("pdf")?MediaType.APPLICATION_PDF:kind.equals("txt")?new MediaType("text","plain",StandardCharsets.UTF_8):MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).contentLength(((Number)item.get("size_bytes")).longValue())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).body(body);
    }
    @PutMapping("/downloads/{id}") public Map<String,Object> status(@PathVariable String id,@RequestBody DownloadStatus body,@RequestHeader(value="Authorization",required=false) String header) {
        long user=auth.user(header);var item=resources.ownedDownload(user,id);
        if(body.status()==null||!Set.of("started","paused","completed","failed").contains(body.status()))throw new ApiError(400,"无效下载状态");
        if(body.status().equals("completed")&&item.get("served_at")==null)throw new ApiError(409,"文件尚未下载，不能标记完成");
        String current=item.get("status").toString();
        if(current.equals("completed")&&!body.status().equals("completed"))throw new ApiError(409,"下载已完成，请创建新的下载任务");
        db.update("UPDATE resource_download SET status=?,finished_at=CASE WHEN ? IN ('completed','failed') THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id=? AND user_id=?",body.status(),body.status(),id,user);
        return Map.of("id",id,"status",body.status());
    }
    @GetMapping("/library/summary") public Map<String,Object> summary(@RequestHeader(value="Authorization",required=false) String header) { return resources.summary(auth.user(header)); }
    record Feedback(String content) {}
    @PostMapping("/feedback") public Map<String,Object> feedback(@RequestHeader(value="Authorization",required=false) String header,@RequestBody Feedback body) {
        long user=auth.user(header);String content=body.content()==null?"":body.content().trim();
        if(content.length()<5||content.length()>1000)throw new ApiError(400,"请填写5–1000字的建议");
        db.update("INSERT INTO user_feedback(id,user_id,content) VALUES (?,?,?)",UUID.randomUUID().toString(),user,content);return Map.of("received",true);
    }
}
