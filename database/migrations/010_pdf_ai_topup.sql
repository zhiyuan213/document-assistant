-- Execute after 008_wechat_pay.sql and 009_pdf_ai.sql, before deploying AI top-ups.

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
