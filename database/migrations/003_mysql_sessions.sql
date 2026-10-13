-- 选择 dengke 数据库后执行。登录会话改存 MySQL，原 Redis 登录需重新登录。
CREATE TABLE IF NOT EXISTS login_session (
 token_hash CHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_login_session_expiry (expires_at),
 FOREIGN KEY (user_id) REFERENCES app_user(id)
);
