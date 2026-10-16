-- Execute once after 007_monthly_membership.sql to enable VIP PDF AI usage tracking.
CREATE TABLE IF NOT EXISTS pdf_ai_usage (
 visitor_id CHAR(36) NOT NULL, usage_month DATE NOT NULL, request_count INT NOT NULL DEFAULT 0,
 used_credits BIGINT NOT NULL DEFAULT 0,
 input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(visitor_id,usage_month), FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);

CREATE TABLE IF NOT EXISTS pdf_ai_daily_usage (
  visitor_id CHAR(36) NOT NULL,
  usage_date DATE NOT NULL,
  request_count INT NOT NULL DEFAULT 0,
  PRIMARY KEY (visitor_id, usage_date),
  CONSTRAINT fk_pdf_ai_daily_usage_visitor FOREIGN KEY (visitor_id) REFERENCES pdf_visitor(id) ON DELETE CASCADE
);
