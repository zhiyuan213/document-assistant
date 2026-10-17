package cn.dengkemiao.docs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class PdfAiCredits {
    public static final long TOPUP_CREDITS = 2_000_000L;
    public static final int TOPUP_PRICE_FEN = 990;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    @Value("${PDF_AI_MONTHLY_TOKEN_CREDITS:2000000}") private long monthlyCredits;
    public PdfAiCredits(JdbcTemplate db, PlatformTransactionManager manager) {
        this.db = db; this.tx = new TransactionTemplate(manager);
    }
    public long monthlyCap() { return Math.min(2_000_000L, Math.max(0, monthlyCredits)); }
    static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    public String owner(String visitor) {
        var openids = db.queryForList("SELECT wechat_openid FROM pdf_visitor WHERE id=?", String.class, visitor);
        if (openids.isEmpty() || openids.get(0)==null || openids.get(0).isBlank()) return visitor;
        String openid = openids.get(0);
        var accounts = db.queryForList("SELECT visitor_id FROM pdf_ai_account WHERE wechat_openid=?", String.class, openid);
        if (!accounts.isEmpty()) return accounts.get(0);
        var ids = db.queryForList("SELECT id FROM pdf_visitor WHERE wechat_openid=(SELECT wechat_openid FROM pdf_visitor WHERE id=?) ORDER BY created_at,id LIMIT 1", String.class, visitor);
        String canonical = ids.isEmpty() ? visitor : ids.get(0);
        // Persist the identity once: same-second registrations must not change the wallet owner.
        db.update("INSERT INTO pdf_ai_account(wechat_openid,visitor_id) VALUES (?,?) ON DUPLICATE KEY UPDATE wechat_openid=VALUES(wechat_openid)", openid, canonical);
        return db.queryForObject("SELECT visitor_id FROM pdf_ai_account WHERE wechat_openid=?", String.class, openid);
    }
    public Snapshot snapshot(String owner) { return tx.execute(s -> load(owner)); }
    // Every balance change locks the canonical visitor first, including payment grants.
    private Snapshot load(String owner) {
        db.queryForList("SELECT id FROM pdf_visitor WHERE id=? FOR UPDATE", owner);
        db.update("INSERT INTO pdf_ai_wallet(visitor_id) VALUES (?) ON DUPLICATE KEY UPDATE visitor_id=VALUES(visitor_id)", owner);
        var row = db.queryForMap("SELECT * FROM pdf_ai_wallet WHERE visitor_id=?", owner);
        var memberships = db.queryForList("SELECT expires_at FROM pdf_membership WHERE visitor_id=?", owner);
        LocalDateTime expiry = memberships.isEmpty() ? null : JdbcDates.local( memberships.get(0).get("expires_at"));
        LocalDateTime end = row.get("period_end") == null ? null : JdbcDates.local( row.get("period_end"));
        boolean vip = expiry != null && expiry.isAfter(now());
        long used = ((Number) row.get("monthly_used")).longValue();
        if (vip && end == null) {
            // Preserve consumption from installations using the previous monthly usage table.
            var old = db.queryForList("SELECT used_credits FROM pdf_ai_usage WHERE visitor_id=? AND usage_month=?", owner, now().toLocalDate().withDayOfMonth(1));
            used = old.isEmpty() ? 0 : ((Number) old.get(0).get("used_credits")).longValue();
            end = expiry;
            while (end.minusMonths(1).isAfter(now())) end = end.minusMonths(1);
        } else if (vip && !end.isAfter(now())) {
            do { end = end.plusMonths(1); } while (!end.isAfter(now()));
            if (end.isAfter(expiry)) end = expiry;
            used = 0;
        }
        db.update("UPDATE pdf_ai_wallet SET period_end=?,monthly_used=? WHERE visitor_id=?", end == null ? null : Timestamp.valueOf(end), used, owner);
        long topup = ((Number) row.get("topup_balance")).longValue();
        return new Snapshot(vip, vip ? Math.max(0, monthlyCap()-used) : 0, topup, end);
    }
    public Reservation reserve(String owner, long credits) {
        return tx.execute(s -> {
            Snapshot balance = load(owner);
            if (credits <= 0 || balance.total() < credits) throw new ApiError(402, "AI 额度不足，请购买加油包后继续");
            long monthly = Math.min(balance.monthly(), credits), topup = credits-monthly;
            db.update("UPDATE pdf_ai_wallet SET monthly_used=monthly_used+?,topup_balance=topup_balance-? WHERE visitor_id=?", monthly, topup, owner);
            return new Reservation(credits, monthly, topup, balance.periodEnd());
        });
    }
    public void settle(String owner, Reservation reservation, long actual) {
        tx.executeWithoutResult(s -> {
            Snapshot balance = load(owner);
            long monthlyActual = Math.min(actual, reservation.monthly());
            long topupActual = Math.max(0, actual-reservation.monthly());
            // A response arriving after the period ends must not restore expired monthly credits.
            long monthlyRefund = Objects.equals(balance.periodEnd(), reservation.periodEnd()) ? reservation.monthly()-monthlyActual : 0;
            db.update("UPDATE pdf_ai_wallet SET monthly_used=GREATEST(0,monthly_used-?),topup_balance=topup_balance+? WHERE visitor_id=?",
                    monthlyRefund, reservation.topup()-topupActual, owner);
        });
    }
    public void grantTopup(String owner, long amount) {
        tx.executeWithoutResult(s -> {
            load(owner);
            db.update("UPDATE pdf_ai_wallet SET topup_balance=topup_balance+? WHERE visitor_id=?", amount, owner);
        });
    }
    public void activateMonth(String owner, LocalDateTime expires, boolean renewing) {
        tx.executeWithoutResult(s -> {
            load(owner);
            if (!renewing) db.update("UPDATE pdf_ai_wallet SET period_end=?,monthly_used=0 WHERE visitor_id=?", Timestamp.valueOf(expires), owner);
        });
    }
    public record Snapshot(boolean vip, long monthly, long topup, LocalDateTime periodEnd) {
        public long total() { return Math.max(0, monthly+topup); }
    }
    public record Reservation(long credits, long monthly, long topup, LocalDateTime periodEnd) {}
}
