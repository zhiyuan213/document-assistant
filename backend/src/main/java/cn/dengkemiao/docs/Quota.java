package cn.dengkemiao.docs;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Quota {
    private final JdbcTemplate db;
    @Value("${app.free-daily-limit}") private int limit;
    public Quota(JdbcTemplate db) { this.db = db; }
    String today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(); }
    public boolean premium(long user) { return db.queryForObject("SELECT COUNT(*) FROM user_rights WHERE user_id=? AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)", Integer.class, user) > 0; }
    @Transactional
    public String reserve(long user) {
        if (premium(user)) return null;
        String day = today();
        db.update("INSERT INTO user_quota(user_id,quota_date,used_count) VALUES (?,?,0) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", user, day);
        if (db.update("UPDATE user_quota SET used_count=used_count+1 WHERE user_id=? AND quota_date=? AND used_count<?", user, day, limit) == 0) throw new ApiError(429, "今日免费次数已用完，明天再来吧");
        return day;
    }
    public void refund(long user, String day) { if (day != null) db.update("UPDATE user_quota SET used_count=GREATEST(used_count-1,0) WHERE user_id=? AND quota_date=?", user, day); }
    public Map<String,Object> info(long user) {
        var counts = db.queryForList("SELECT used_count FROM user_quota WHERE user_id=? AND quota_date=?", user, today());
        int used = counts.isEmpty() ? 0 : ((Number) counts.getFirst().get("used_count")).intValue();
        return Map.of("premium", premium(user), "dailyLimit", limit, "used", used, "remaining", Math.max(0, limit-used));
    }
}
