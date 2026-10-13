package cn.dengkemiao.docs;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class AuthTest {
    private JdbcTemplate db;
    private Auth auth;
    @BeforeEach void setup() {
        db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("DROP TABLE IF EXISTS login_session");
        db.execute("DROP TABLE IF EXISTS app_user");
        db.execute("CREATE TABLE app_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,open_id VARCHAR(128) UNIQUE,nickname VARCHAR(64))");
        db.execute("CREATE TABLE login_session(token_hash CHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL,expires_at TIMESTAMP NOT NULL,FOREIGN KEY(user_id) REFERENCES app_user(id))");
        auth = new Auth(db);
    }
    @Test void devLoginIsDisabledByDefault() {
        assertEquals(403,assertThrows(ApiError.class,()->auth.login(null,true)).status);
    }
    @Test void sessionsPersistAcrossInstancesAndStoreOnlyHash() {
        ReflectionTestUtils.setField(auth,"dev",true);
        var login = auth.login(null,true);
        String token = login.get("token").toString();
        long userId = ((Number) login.get("userId")).longValue();
        assertEquals(userId,new Auth(db).user("Bearer "+token));
        String hash = db.queryForObject("SELECT token_hash FROM login_session",String.class);
        assertEquals(64,hash.length());assertNotEquals(token,hash);
        Instant expiry = db.queryForObject("SELECT expires_at FROM login_session",Timestamp.class).toInstant();
        assertTrue(expiry.isAfter(Instant.now().plusSeconds(6*86400)));
        assertTrue(expiry.isBefore(Instant.now().plusSeconds(7*86400+10)));
        assertEquals(401,assertThrows(ApiError.class,()->auth.user(null)).status);
        assertEquals(401,assertThrows(ApiError.class,()->auth.user("Bearer unknown")).status);
    }
    @Test void expiredSessionsAreRejectedAndCleanedOnLogin() {
        ReflectionTestUtils.setField(auth,"dev",true);
        String token = auth.login(null,true).get("token").toString();
        db.update("UPDATE login_session SET expires_at=?",Timestamp.from(Instant.now().minusSeconds(60)));
        assertEquals(401,assertThrows(ApiError.class,()->auth.user("Bearer "+token)).status);
        String next = auth.login(null,true).get("token").toString();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM login_session",Integer.class));
        assertTrue(auth.user("Bearer "+next)>0);
    }
}