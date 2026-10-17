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
