-- Execute once after 007_monthly_membership.sql and before deploying WeChat Pay support.
ALTER TABLE pdf_visitor ADD COLUMN wechat_openid VARCHAR(64);
CREATE INDEX idx_pdf_visitor_openid ON pdf_visitor(wechat_openid);
ALTER TABLE pdf_purchase ADD COLUMN out_trade_no VARCHAR(32) NULL;
ALTER TABLE pdf_purchase ADD COLUMN paid_at DATETIME NULL;
CREATE UNIQUE INDEX uq_pdf_purchase_out_trade_no ON pdf_purchase(out_trade_no);
