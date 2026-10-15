-- Three independently auditable bonus pools for verified 3999-yuan partner purchases.
-- Apply after 15_dividend_pool.sql. Existing single-pool history is not reassigned.
CREATE TABLE IF NOT EXISTS bonus_dividend_pool (
  pool_code VARCHAR(16) NOT NULL PRIMARY KEY,
  pool_balance DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  total_in DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  total_out DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO bonus_dividend_pool(pool_code) VALUES
  ('PARTNER'), ('COFOUNDER'), ('OPERATIONS');

CREATE TABLE IF NOT EXISTS bonus_dividend_pool_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pool_code VARCHAR(16) NOT NULL,
  change_amount DECIMAL(14,2) NOT NULL,
  balance_after DECIMAL(14,2) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  receiver_member_id BIGINT DEFAULT NULL,
  receiver_identity VARCHAR(32) DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bonus_pool_source (pool_code, source_type, source_id),
  KEY idx_bonus_pool_time (pool_code, create_time),
  KEY idx_bonus_receiver (receiver_member_id),
  CONSTRAINT fk_bonus_pool_code FOREIGN KEY (pool_code) REFERENCES bonus_dividend_pool(pool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bonus_dividend_payout_request (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  pool_code VARCHAR(16) NOT NULL,
  receiver_member_id BIGINT NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_by BIGINT NOT NULL,
  reviewed_by BIGINT DEFAULT NULL,
  review_remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  review_time DATETIME DEFAULT NULL,
  KEY idx_bonus_request_status (pool_code, status, create_time),
  CONSTRAINT fk_bonus_request_pool FOREIGN KEY (pool_code) REFERENCES bonus_dividend_pool(pool_code),
  CONSTRAINT fk_bonus_request_member FOREIGN KEY (receiver_member_id) REFERENCES ums_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
