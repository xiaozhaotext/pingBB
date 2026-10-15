-- Recharge principal is separate from earnings and points. All money is integer CNY cents.
CREATE TABLE IF NOT EXISTS ums_recharge_account (
 member_id BIGINT NOT NULL PRIMARY KEY,
 balance_cents BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ums_recharge_order (
 order_no VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 member_id BIGINT NOT NULL,
 request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 amount_cents BIGINT NOT NULL,
 channel VARCHAR(10) NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
 transaction_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
 balance_after_cents BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 paid_at TIMESTAMP NULL,
 checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_member_request(member_id,request_id),
 UNIQUE KEY uk_channel_transaction(channel,transaction_id),
 KEY ix_member_created(member_id,created_at),
 KEY ix_pending_check(status,checked_at,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
