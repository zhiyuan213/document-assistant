-- Run before deployment when SQL_INIT_MODE=never. Safe to run again.

CREATE TABLE IF NOT EXISTS pdf_ai_daily_free (
 visitor_id CHAR(36) PRIMARY KEY,
 credit_date DATE NOT NULL,
 used_credits BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
