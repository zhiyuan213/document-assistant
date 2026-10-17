package cn.dengkemiao.docs;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcDatesTest {
    @Test void monthlyWalletLoadsMysqlLocalDateTimeValuesAfterPayment() {
        var db = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var manager = org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        var expiry = PdfAiCredits.now().plusMonths(1);
        org.mockito.Mockito.when(db.queryForList("SELECT expires_at FROM pdf_membership WHERE visitor_id=?", "owner"))
                .thenReturn(java.util.List.of(java.util.Map.of("expires_at",expiry)));
        org.mockito.Mockito.when(db.queryForMap("SELECT * FROM pdf_ai_wallet WHERE visitor_id=?", "owner"))
                .thenReturn(java.util.Map.of("period_end",expiry,"monthly_used",0L,"topup_balance",0L));
        var credits = new PdfAiCredits(db,manager);
        org.springframework.test.util.ReflectionTestUtils.setField(credits,"monthlyCredits",2000000L);
        var balance = credits.snapshot("owner");
        assertTrue(balance.vip());
        assertEquals(2000000L,balance.monthly());
        assertEquals(expiry,balance.periodEnd());
    }
    @Test void mysqlDatetimeAndJdbcTimestampPreserveTheSameLocalTime() {
        var value = LocalDateTime.of(2026,10,17,13,44,42,123456000);
        assertEquals(value,JdbcDates.local(value));
        assertEquals(value,JdbcDates.local(Timestamp.valueOf(value)));
        assertNull(JdbcDates.local(null));
        assertThrows(IllegalArgumentException.class,()->JdbcDates.local("2026-10-17"));
    }
}
