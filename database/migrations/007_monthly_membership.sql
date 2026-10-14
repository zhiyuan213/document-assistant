-- Execute once before deploying the monthly membership backend.
-- Existing daily usage counts are retained. Historical file counts use job counts
-- as a conservative approximation because the old schema did not record inputs.
ALTER TABLE pdf_daily_quota ADD COLUMN used_files INT NOT NULL DEFAULT 0;
UPDATE pdf_daily_quota SET used_files=used_count;
ALTER TABLE pdf_tool_job ADD COLUMN quota_units INT NOT NULL DEFAULT 1;
CREATE TABLE IF NOT EXISTS pdf_membership (
 visitor_id CHAR(36) PRIMARY KEY, expires_at DATETIME NOT NULL,
 FOREIGN KEY(visitor_id) REFERENCES pdf_visitor(id)
);
