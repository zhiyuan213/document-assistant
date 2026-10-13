CREATE TABLE IF NOT EXISTS app_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, open_id VARCHAR(128) NOT NULL UNIQUE,
 nickname VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS login_session (
 token_hash CHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_login_session_expiry (expires_at),
 FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS file_info (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, name VARCHAR(128) NOT NULL,
 kind VARCHAR(8) NOT NULL, storage_key VARCHAR(64) NOT NULL, size_bytes BIGINT NOT NULL,
 source_bytes BIGINT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS file_task (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, operation VARCHAR(32) NOT NULL,
 status VARCHAR(16) NOT NULL, result_id VARCHAR(36), error_message VARCHAR(256),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP NULL,
 FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS ocr_record (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, file_id VARCHAR(36) NOT NULL,
 recognized_text LONGTEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES app_user(id), FOREIGN KEY(file_id) REFERENCES file_info(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS product (
 id BIGINT PRIMARY KEY, name VARCHAR(128) NOT NULL, price_fen INT NOT NULL, enabled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE IF NOT EXISTS order_info (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 amount_fen INT NOT NULL, status VARCHAR(16) NOT NULL, wx_transaction_id VARCHAR(64) UNIQUE,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, paid_at TIMESTAMP NULL,
 FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(product_id) REFERENCES product(id)
);
CREATE TABLE IF NOT EXISTS user_rights (
 user_id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL, expires_at TIMESTAMP NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(user_id) REFERENCES app_user(id)
);
CREATE TABLE IF NOT EXISTS user_quota (
 user_id BIGINT NOT NULL, quota_date DATE NOT NULL, used_count INT NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,quota_date), FOREIGN KEY(user_id) REFERENCES app_user(id)
);
INSERT INTO product(id,name,price_fen,enabled) VALUES (1,'登科喵高级工具包',990,FALSE)
ON DUPLICATE KEY UPDATE name=VALUES(name),price_fen=VALUES(price_fen);

CREATE TABLE IF NOT EXISTS resource_category (
 id VARCHAR(32) PRIMARY KEY, name VARCHAR(32) NOT NULL, prefix VARCHAR(16) NOT NULL,
 color VARCHAR(16) NOT NULL, icon VARCHAR(16) NOT NULL, sort_order INT NOT NULL
);
CREATE TABLE IF NOT EXISTS resource_item (
 id VARCHAR(36) PRIMARY KEY, category_id VARCHAR(32) NOT NULL, topic VARCHAR(32) NOT NULL,
 name VARCHAR(128) NOT NULL, description LONGTEXT NOT NULL, kind VARCHAR(8) NOT NULL,
 storage_key VARCHAR(64) NOT NULL, size_bytes BIGINT NOT NULL, tags VARCHAR(256) NOT NULL,
 is_free BOOLEAN NOT NULL DEFAULT TRUE, is_demo BOOLEAN NOT NULL DEFAULT FALSE,
 published BOOLEAN NOT NULL DEFAULT TRUE, downloads_count BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(category_id) REFERENCES resource_category(id)
);
CREATE TABLE IF NOT EXISTS resource_favorite (
 user_id BIGINT NOT NULL, resource_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id,resource_id),
 FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(resource_id) REFERENCES resource_item(id)
);
CREATE TABLE IF NOT EXISTS resource_download (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, resource_id VARCHAR(36) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'started', served_at TIMESTAMP NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP NULL,
 FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(resource_id) REFERENCES resource_item(id)
);
CREATE TABLE IF NOT EXISTS user_feedback (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, content VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(user_id) REFERENCES app_user(id)
);
INSERT INTO resource_category(id,name,prefix,color,icon,sort_order) VALUES
 ('graduate','考研资料','考研','red','exam',1),
 ('civil','公考资料','公考','blue','briefcase',2),
 ('teacher','教师资格','教资','orange','badge',3),
 ('english','英语资料','英语','purple','language',4),
 ('college','大学资料','大学','green','cap',5),
 ('career','职业考试','职业','violet','book',6),
 ('templates','实用模板','实用','indigo','document',7),
 ('other','其他资料','资料','gray','grid',8)
 ON DUPLICATE KEY UPDATE name=VALUES(name),prefix=VALUES(prefix),color=VALUES(color),icon=VALUES(icon),sort_order=VALUES(sort_order);
