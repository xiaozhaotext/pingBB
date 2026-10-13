-- Additive and repeatable. Never reset inventory or existing orders.
CREATE TABLE IF NOT EXISTS oms_order_request (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 order_id BIGINT NULL,
 state VARCHAR(24) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_order_request_member_key(member_id,idempotency_key),
 KEY idx_order_request_order(order_id),
 CONSTRAINT fk_order_request_member FOREIGN KEY(member_id) REFERENCES ums_member(id),
 CONSTRAINT fk_order_request_order FOREIGN KEY(order_id) REFERENCES oms_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS oms_payment_confirmation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 channel_code VARCHAR(24) NOT NULL,
 channel_trade_no VARCHAR(128) NOT NULL,
 paid_amount DECIMAL(18,2) NOT NULL,
 currency_code VARCHAR(8) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_payment_order(order_id),
 UNIQUE KEY uk_payment_channel_trade(channel_code,channel_trade_no),
 CONSTRAINT fk_payment_confirmation_order FOREIGN KEY(order_id) REFERENCES oms_order(id),
 CONSTRAINT ck_payment_confirmation_amount CHECK(paid_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS oms_payment_exception (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 order_sn VARCHAR(64) NOT NULL,
 channel_code VARCHAR(24) NOT NULL,
 channel_trade_no VARCHAR(128) NOT NULL,
 paid_amount DECIMAL(18,2) NOT NULL,
 currency_code VARCHAR(8) NOT NULL,
 reason VARCHAR(128) NOT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'OPEN',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_exception_channel_trade(channel_code,channel_trade_no),
 KEY idx_exception_order(order_id),
 CONSTRAINT fk_payment_exception_order FOREIGN KEY(order_id) REFERENCES oms_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS oms_refund_compensation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 payment_exception_id BIGINT NOT NULL,
 state VARCHAR(40) NOT NULL DEFAULT 'WAITING_MANUAL_REVIEW',
 attempts INT NOT NULL DEFAULT 0,
 last_error VARCHAR(500) NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_compensation_exception(payment_exception_id),
 KEY idx_compensation_state(state),
 CONSTRAINT fk_compensation_exception FOREIGN KEY(payment_exception_id) REFERENCES oms_payment_exception(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
