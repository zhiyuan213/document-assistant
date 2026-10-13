package cn.dengkemiao.docs;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Resources {
    private final JdbcTemplate db;
    public Resources(JdbcTemplate db) { this.db=db; }
    static final String COLUMNS="r.id,r.name,r.description,r.kind,r.size_bytes,r.tags,r.is_free,r.is_demo,r.downloads_count,r.category_id,r.topic,r.created_at,c.name AS category_name";
    public List<Map<String,Object>> categories() {
        return db.queryForList("SELECT c.*, (SELECT COUNT(*) FROM resource_item r WHERE r.category_id=c.id AND r.published=TRUE) AS resource_count FROM resource_category c ORDER BY c.sort_order");
    }
    public Map<String,Object> list(String query,String category,String topic,String order,int page,Long user,boolean favorites) {
        if(page<1||page>10000||query.length()>100||!Set.of("recent","popular").contains(order)) throw new ApiError(400,"无效搜索参数");
        if(!topic.isBlank()&&!Set.of("papers","notes","study","schools","courses","experience").contains(topic)) throw new ApiError(400,"无效资料类型");
        if(favorites&&user==null) throw new ApiError(401,"请先登录");
        List<Object> args=new ArrayList<>();
        String where=" WHERE r.published=TRUE";
        if(!query.isBlank()) {
            // !作为LIKE转义符，让百分号、下划线按字面搜索，参数均使用预编译绑定。
            String value="%"+query.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            where+=" AND (LOWER(r.name) LIKE LOWER(?) ESCAPE '!' OR LOWER(r.description) LIKE LOWER(?) ESCAPE '!' OR LOWER(r.tags) LIKE LOWER(?) ESCAPE '!')";
            args.addAll(List.of(value,value,value));
        }
        if(!category.isBlank()) { where+=" AND r.category_id=?";args.add(category); }
        if(!topic.isBlank()) { where+=" AND r.topic=?";args.add(topic); }
        if(favorites) { where+=" AND EXISTS(SELECT 1 FROM resource_favorite f WHERE f.resource_id=r.id AND f.user_id=?)";args.add(user); }
        long total=db.queryForObject("SELECT COUNT(*) FROM resource_item r"+where,Long.class,args.toArray());
        String sort=order.equals("popular")?"r.downloads_count DESC,r.created_at DESC,r.id":"r.created_at DESC,r.id";
        args.add((page-1)*20);
        var items=db.queryForList("SELECT "+COLUMNS+" FROM resource_item r JOIN resource_category c ON c.id=r.category_id"+where+" ORDER BY "+sort+" LIMIT 20 OFFSET ?",args.toArray());
        return Map.of("items",items,"total",total,"page",page);
    }
    public Map<String,Object> item(String id) {
        var items=db.queryForList("SELECT "+COLUMNS+",r.storage_key FROM resource_item r JOIN resource_category c ON c.id=r.category_id WHERE r.id=? AND r.published=TRUE",id);
        if(items.isEmpty()) throw new ApiError(404,"资料不存在或已下架");return items.getFirst();
    }
    public boolean favorite(long user,String id) { return db.queryForObject("SELECT COUNT(*) FROM resource_favorite WHERE user_id=? AND resource_id=?",Integer.class,user,id)>0; }
    public void addFavorite(long user,String id) {
        item(id);db.update("INSERT INTO resource_favorite(user_id,resource_id) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",user,id);
    }
    public void removeFavorite(long user,String id) { db.update("DELETE FROM resource_favorite WHERE user_id=? AND resource_id=?",user,id); }
    public Map<String,Object> ownedDownload(long user,String id) {
        var rows=db.queryForList("SELECT d.*,r.name,r.kind,r.size_bytes,r.category_id,r.storage_key,r.is_free,r.published FROM resource_download d JOIN resource_item r ON r.id=d.resource_id WHERE d.id=? AND d.user_id=?",id,user);
        if(rows.isEmpty()) throw new ApiError(404,"下载记录不存在");return rows.getFirst();
    }
    @Transactional
    public void markServed(long user,String id,String resourceId) {
        int changed=db.update("UPDATE resource_download SET served_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND served_at IS NULL",id,user);
        if(changed==1) db.update("UPDATE resource_item SET downloads_count=downloads_count+1 WHERE id=?",resourceId);
    }
    public Map<String,Object> downloads(long user,int page) {
        if(page<1||page>10000)throw new ApiError(400,"无效页码");
        var total=db.queryForObject("SELECT COUNT(*) FROM resource_download WHERE user_id=?",Long.class,user);
        var items=db.queryForList("SELECT d.id,d.resource_id,d.status,d.created_at,d.finished_at,r.name,r.kind,r.size_bytes FROM resource_download d JOIN resource_item r ON r.id=d.resource_id WHERE d.user_id=? ORDER BY d.created_at DESC,d.id DESC LIMIT 20 OFFSET ?",user,(page-1)*20);
        return Map.of("items",items,"total",total,"page",page);
    }
    public Map<String,Object> summary(long user) {
        return Map.of("favorites",db.queryForObject("SELECT COUNT(*) FROM resource_favorite f JOIN resource_item r ON r.id=f.resource_id WHERE f.user_id=? AND r.published=TRUE",Long.class,user),
            "downloads",db.queryForObject("SELECT COUNT(*) FROM resource_download WHERE user_id=? AND status='completed'",Long.class,user),
            "files",db.queryForObject("SELECT COUNT(*) FROM file_info WHERE user_id=?",Long.class,user));
    }
}
