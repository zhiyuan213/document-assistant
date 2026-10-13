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
