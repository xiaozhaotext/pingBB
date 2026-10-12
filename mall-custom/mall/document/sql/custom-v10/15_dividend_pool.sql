-- P0-2: Real dividend pool and daily dividend distribution.
-- Additive and repeatable on MySQL 8. Run after 13_membership_referral_snapshot.sql.

CREATE TABLE IF NOT EXISTS `dividend_pool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `pool_balance` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT 'Current pool balance',
  `total_in` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT 'Cumulative inflow',
  `total_out` DECIMAL(14,2) NOT NULL DEFAULT 0.00 COMMENT 'Cumulative outflow',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dividend pool balance (single row, id=1)';

INSERT IGNORE INTO `dividend_pool` (`id`, `pool_balance`, `total_in`, `total_out`)
VALUES (1, 0.00, 0.00, 0.00);

CREATE TABLE IF NOT EXISTS `dividend_pool_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `change_amount` DECIMAL(14,2) NOT NULL COMMENT 'Positive=inflow, negative=outflow',
  `balance_after` DECIMAL(14,2) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL COMMENT 'MEMBERSHIP_PURCHASE / DAILY_DIVIDEND',
  `source_id` BIGINT DEFAULT NULL COMMENT 'order_id or allocation batch id',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dividend pool ledger';

CREATE TABLE IF NOT EXISTS `member_dividend_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL,
  `stat_date` DATE NOT NULL COMMENT 'Dividend date',
  `shares` INT NOT NULL COMMENT '1=partner, 3=cofounder',
  `dividend_amount` DECIMAL(12,2) NOT NULL,
  `cap_amount` DECIMAL(12,2) NOT NULL COMMENT 'Referral-based cap applied',
  `cumulative_received` DECIMAL(14,2) NOT NULL COMMENT 'Total received after this allocation',
  `calc_batch` VARCHAR(64) NOT NULL COMMENT 'Idempotency key: date',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_date` (`member_id`, `stat_date`),
  KEY `idx_member` (`member_id`),
  CONSTRAINT `fk_dividend_ledger_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Member daily dividend records';

-- Default configs (INSERT IGNORE preserves admin changes)
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_group`, `remark`) VALUES
  ('withdraw.fee_rate', '0.006', 'withdraw', 'Withdrawal fee rate, e.g. 0.006 = 0.6%'),
  ('dividend.pool.ratio', '0.10', 'dividend', 'Portion of membership purchase amount entering dividend pool'),
  ('dividend.cap.base', '1200.00', 'dividend', 'Dividend cap with no referrals'),
  ('dividend.cap.per_referral', '1200.00', 'dividend', 'Cap increase per referred member'),
  ('dividend.cap.max', '12000.00', 'dividend', 'Maximum dividend cap (3+ referrals)');
