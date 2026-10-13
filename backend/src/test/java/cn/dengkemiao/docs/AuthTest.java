package cn.dengkemiao.docs;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTest {
    @Test void devLoginIsDisabledByDefault() {
        var auth=new Auth(mock(JdbcTemplate.class),mock(StringRedisTemplate.class));
        assertEquals(403,assertThrows(ApiError.class,()->auth.login(null,true)).status);
    }
    @Test void sessionsExpireAndMissingTokensAreRejected() {
        var db=mock(JdbcTemplate.class);var redis=mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(db.queryForObject("SELECT id FROM app_user WHERE open_id=?",Long.class,"dev-local-user")).thenReturn(42L);
        var auth=new Auth(db,redis);ReflectionTestUtils.setField(auth,"dev",true);
        String token=auth.login(null,true).get("token").toString();
        verify(values).set("session:"+token,"42",Duration.ofDays(7));
        when(values.get("session:"+token)).thenReturn("42");assertEquals(42,auth.user("Bearer "+token));
        assertEquals(401,assertThrows(ApiError.class,()->auth.user(null)).status);
        assertEquals(401,assertThrows(ApiError.class,()->auth.user("Bearer expired")).status);
    }
}
