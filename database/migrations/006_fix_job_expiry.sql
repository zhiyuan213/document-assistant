-- Explicit default prevents legacy MySQL implicit ON UPDATE on the first TIMESTAMP.
-- Preserves existing rows; does not restore results already removed by cleanup.
ALTER TABLE pdf_tool_job MODIFY COLUMN expires_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
