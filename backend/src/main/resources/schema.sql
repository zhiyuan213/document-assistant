-- 先创建新版PDF工具表。旧库清理请在新版部署和备份后执行005。
CREATE TABLE IF NOT EXISTS pdf_visitor (
 id CHAR(36) PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE, wechat_openid VARCHAR(64), INDEX idx_pdf_visitor_openid(wechat_openid),
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS pdf_daily_quota (
 visitor_id CHAR(36) NOT NULL, quota_date DATE NOT NULL, used_count INT NOT NULL DEFAULT 0, used_files INT NOT NULL DEFAULT 0,
 PRIMARY KEY(visitor_id,quota_date), FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
CREATE TABLE IF NOT EXISTS pdf_tool_job (
 id CHAR(36) PRIMARY KEY, visitor_id CHAR(36) NOT NULL, tool VARCHAR(24) NOT NULL,
 status VARCHAR(16) NOT NULL, progress INT NOT NULL DEFAULT 0, quota_date DATE NOT NULL, quota_units INT NOT NULL DEFAULT 1,
 extension VARCHAR(8), size_bytes BIGINT, error_message VARCHAR(256),
 expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_pdf_job_expiry(expires_at), FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
CREATE TABLE IF NOT EXISTS pdf_credit_balance (
 visitor_id CHAR(36) PRIMARY KEY, remaining INT NOT NULL DEFAULT 0,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
CREATE TABLE IF NOT EXISTS pdf_purchase (
 id CHAR(36) PRIMARY KEY, visitor_id CHAR(36) NOT NULL, package_id VARCHAR(16) NOT NULL,
 amount_fen INT NOT NULL, credits INT NOT NULL, status VARCHAR(16) NOT NULL,
 transaction_id VARCHAR(64) UNIQUE, out_trade_no VARCHAR(32) UNIQUE, paid_at DATETIME,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);

CREATE TABLE IF NOT EXISTS pdf_membership (
 visitor_id CHAR(36) PRIMARY KEY, expires_at DATETIME NOT NULL,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
CREATE TABLE IF NOT EXISTS pdf_ai_usage (
 visitor_id CHAR(36) NOT NULL, usage_month DATE NOT NULL, request_count INT NOT NULL DEFAULT 0,
 used_credits BIGINT NOT NULL DEFAULT 0,
 input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(visitor_id,usage_month), FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
CREATE TABLE IF NOT EXISTS pdf_ai_daily_usage (
 visitor_id CHAR(36) NOT NULL, usage_date DATE NOT NULL, request_count INT NOT NULL DEFAULT 0,
 PRIMARY KEY(visitor_id,usage_date), FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pdf_ai_wallet (
 visitor_id CHAR(36) PRIMARY KEY,
 period_end DATETIME NULL,
 monthly_used BIGINT NOT NULL DEFAULT 0,
 topup_balance BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);

CREATE TABLE IF NOT EXISTS pdf_ai_account (
 wechat_openid VARCHAR(64) PRIMARY KEY,
 visitor_id CHAR(36) NOT NULL,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);

CREATE TABLE IF NOT EXISTS pdf_virtual_order (
 purchase_id CHAR(36) PRIMARY KEY,
 openid VARCHAR(64) NOT NULL,
 offer_id VARCHAR(64) NOT NULL,
 product_id VARCHAR(128) NOT NULL,
 environment INT NOT NULL,
 refunded_fen INT NOT NULL DEFAULT 0,
 member_start DATETIME NULL,
 member_end DATETIME NULL,
 next_check_at DATETIME NOT NULL,
 delivered_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 last_error VARCHAR(96) NULL,
 INDEX idx_pdf_virtual_check(next_check_at),
 FOREIGN KEY(purchase_id) REFERENCES pdf_purchase(id)
);

CREATE TABLE IF NOT EXISTS pdf_translation_job (
 id CHAR(36) PRIMARY KEY,
 visitor_id CHAR(36) NOT NULL,
 client_request_id VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL,
 progress INT NOT NULL DEFAULT 0,
 worker_id CHAR(36) NOT NULL,
 error_message VARCHAR(512) NULL,
 result_json MEDIUMTEXT NULL,
 pdf_data LONGBLOB NULL,
 word_data LONGBLOB NULL,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 expires_at DATETIME NOT NULL,
 INDEX idx_pdf_translation_expiry(expires_at),
 UNIQUE KEY uq_pdf_translation_request(visitor_id,client_request_id),
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
