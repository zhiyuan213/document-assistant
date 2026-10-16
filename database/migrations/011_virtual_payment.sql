-- Safe to run repeatedly; required after 008, 009, 010. Existing orders are preserved.
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
