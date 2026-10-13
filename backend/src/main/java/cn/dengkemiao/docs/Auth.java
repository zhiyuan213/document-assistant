package cn.dengkemiao.docs;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class Auth {
    private final JdbcTemplate db;
    private final StringRedisTemplate redis;
    @Value("${app.dev-login}") private boolean dev;
    @Value("${app.wx-app-id}") private String appId;
    @Value("${app.wx-secret}") private String secret;
    public Auth(JdbcTemplate db, StringRedisTemplate redis) { this.db = db; this.redis = redis; }
    public Map<String, Object> login(String code, boolean development) {
        String openId;
        if (development) {
            if (!dev) throw new ApiError(403, "开发登录未启用");
            openId = "dev-local-user";
        } else {
            if (appId.isBlank() || secret.isBlank()) throw new ApiError(503, "尚未配置微信登录");
            if (code == null || code.isBlank()) throw new ApiError(400, "缺少微信登录code");
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
            factory.setReadTimeout(Duration.ofSeconds(10));
            try {
                String response = RestClient.builder().requestFactory(factory).build().get().uri("https://api.weixin.qq.com/sns/jscode2session?appid={id}&secret={secret}&js_code={code}&grant_type=authorization_code", appId, secret, code).retrieve().body(String.class);
                openId = new ObjectMapper().readTree(response).path("openid").asText();
                if (openId.isBlank()) throw new ApiError(401,"微信登录失败，请重新登录");
            } catch (ApiError e) { throw e; }
            catch (Exception e) { throw new ApiError(502,"微信登录服务暂不可用"); }
        }
        db.update("INSERT INTO app_user(open_id,nickname) VALUES (?,?) ON DUPLICATE KEY UPDATE open_id=VALUES(open_id)", openId, development ? "开发体验用户" : "登科喵用户");
        Long userId = db.queryForObject("SELECT id FROM app_user WHERE open_id=?", Long.class, openId);
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        redis.opsForValue().set("session:" + token, userId.toString(), Duration.ofDays(7));
        return Map.of("token", token, "userId", userId, "development", development);
    }
    public long user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new ApiError(401, "请先登录");
        String value = redis.opsForValue().get("session:" + authorization.substring(7));
        if (value == null) throw new ApiError(401, "登录已过期，请重新登录");
        return Long.parseLong(value);
    }
}
