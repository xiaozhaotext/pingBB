-- P0-1a: additive, repeatable MySQL 8 migration. Run after mall.sql and custom-v10/01-07.
-- This file never deletes or changes existing member, point, earnings, or audit rows.
-- The older 12_membership_real_payment_DRAFT.sql is a design draft; do not execute it.
-- P0-1b/c/e application code must update balances and their ledgers in one transaction.

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(64) NOT NULL,
  `config_value` VARCHAR(512) DEFAULT NULL,
  `config_group` VARCHAR(32) DEFAULT 'default',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Keep legacy columns used by the existing admin application and extend the audit record.
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `operator_id` BIGINT DEFAULT NULL,
  `operator_name` VARCHAR(64) DEFAULT NULL,
  `module` VARCHAR(32) DEFAULT NULL,
  `action` VARCHAR(64) DEFAULT NULL,
  `target_id` BIGINT DEFAULT NULL,
  `detail` TEXT DEFAULT NULL,
  `ip` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `operator` VARCHAR(128) DEFAULT NULL,
  `target_type` VARCHAR(64) DEFAULT NULL,
  `old_value` TEXT DEFAULT NULL,
  `new_value` TEXT DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create` (`create_time`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL 8 does not offer portable ADD COLUMN IF NOT EXISTS. Conditional prepared
-- ALTER statements preserve existing sys_audit_log data and permit rerunning this file.
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'operator') = 0,
  'ALTER TABLE `sys_audit_log` ADD COLUMN `operator` VARCHAR(128) DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'target_type') = 0,
  'ALTER TABLE `sys_audit_log` ADD COLUMN `target_type` VARCHAR(64) DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'old_value') = 0,
  'ALTER TABLE `sys_audit_log` ADD COLUMN `old_value` TEXT DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'new_value') = 0,
  'ALTER TABLE `sys_audit_log` ADD COLUMN `new_value` TEXT DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'created_at') = 0,
  'ALTER TABLE `sys_audit_log` ADD COLUMN `created_at` DATETIME DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;
-- Backfill old audit timestamps from the original column, instead of dating
-- historical operations at migration time.
UPDATE `sys_audit_log` SET `created_at` = `create_time` WHERE `created_at` IS NULL;
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND COLUMN_NAME = 'created_at' AND COLUMN_DEFAULT IS NULL) > 0,
  'ALTER TABLE `sys_audit_log` MODIFY COLUMN `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;
SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_audit_log' AND INDEX_NAME = 'idx_target') = 0,
  'ALTER TABLE `sys_audit_log` ADD INDEX `idx_target` (`target_type`, `target_id`)', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;

CREATE TABLE IF NOT EXISTS `membership_purchase_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `merchant_order_no` VARCHAR(64) NOT NULL COMMENT 'Platform order number sent to the payment provider',
  `request_id` VARCHAR(64) NOT NULL COMMENT 'Client retry key scoped to the member',
  `member_id` BIGINT NOT NULL,
  `level` VARCHAR(16) NOT NULL COMMENT 'PARTNER or COFOUNDER',
  `amount` DECIMAL(12,2) NOT NULL COMMENT 'CNY amount due, snapshotted at order creation',
  `product_points` BIGINT NOT NULL COMMENT 'Grant amount snapshotted at order creation',
  `repurchase_points` BIGINT NOT NULL COMMENT 'Grant amount snapshotted at order creation',
  `pay_channel` VARCHAR(16) NOT NULL COMMENT 'WECHAT or ALIPAY',
  `pay_mode` VARCHAR(20) NOT NULL COMMENT 'WECHAT_JSAPI, WECHAT_H5 or ALIPAY_WAP',
  `transaction_id` VARCHAR(128) DEFAULT NULL COMMENT 'Verified provider transaction ID; NULL before payment',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, PAID, CANCELLED',
  `paid_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_membership_merchant_order` (`merchant_order_no`),
  UNIQUE KEY `uk_membership_request` (`member_id`, `request_id`),
  UNIQUE KEY `uk_membership_transaction_id` (`transaction_id`),
  KEY `idx_membership_member_status` (`member_id`, `status`),
  CONSTRAINT `fk_membership_order_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_membership_order_amount` CHECK (`amount` > 0),
  CONSTRAINT `chk_membership_order_points` CHECK (`product_points` >= 0 AND `repurchase_points` >= 0),
  CONSTRAINT `chk_membership_order_level` CHECK (`level` IN ('PARTNER', 'COFOUNDER')),
  CONSTRAINT `chk_membership_order_channel` CHECK (`pay_channel` IN ('WECHAT', 'ALIPAY')),
  CONSTRAINT `chk_membership_order_mode` CHECK (`pay_mode` IN ('WECHAT_JSAPI', 'WECHAT_H5', 'ALIPAY_WAP')),
  CONSTRAINT `chk_membership_order_status` CHECK (`status` IN ('PENDING', 'PAID', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Verified membership payment orders';

CREATE TABLE IF NOT EXISTS `member_point_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL,
  `point_type` VARCHAR(16) NOT NULL COMMENT 'PRODUCT or REPURCHASE',
  `change_amount` BIGINT NOT NULL COMMENT 'Positive credit or negative debit',
  `balance_after` BIGINT NOT NULL,
  `source_type` VARCHAR(24) NOT NULL COMMENT 'PURCHASE_GRANT, REFERRAL_REWARD, PURCHASE_DEDUCTION, REFUND',
  `source_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_point_source` (`member_id`, `point_type`, `source_type`, `source_id`),
  KEY `idx_member_point_created` (`member_id`, `point_type`, `created_at`),
  CONSTRAINT `fk_member_point_ledger_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_member_point_type` CHECK (`point_type` IN ('PRODUCT', 'REPURCHASE')),
  CONSTRAINT `chk_member_point_balance` CHECK (`balance_after` >= 0),
  CONSTRAINT `chk_member_point_change` CHECK (`change_amount` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Real membership point movement audit';

CREATE TABLE IF NOT EXISTS `member_level_change_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `from_level` VARCHAR(32) DEFAULT NULL,
  `to_level` VARCHAR(32) NOT NULL,
  `operator` VARCHAR(128) NOT NULL COMMENT 'SYSTEM:payment or administrator identity',
  `reason` VARCHAR(255) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level_order` (`order_id`),
  KEY `idx_level_member_created` (`member_id`, `created_at`),
  CONSTRAINT `fk_level_log_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_level_log_order` FOREIGN KEY (`order_id`) REFERENCES `membership_purchase_order` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Member identity change audit';

-- Separate real cash accounts from the legacy ums_earnings simulation tables.
CREATE TABLE IF NOT EXISTS `member_cash_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL,
  `available_balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `version` BIGINT NOT NULL DEFAULT 0,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_cash_member` (`member_id`),
  CONSTRAINT `fk_cash_account_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_cash_balance` CHECK (`available_balance` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Real withdrawable cash balance';

CREATE TABLE IF NOT EXISTS `member_cash_ledger` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL,
  `change_amount` DECIMAL(12,2) NOT NULL,
  `balance_after` DECIMAL(12,2) NOT NULL,
  `source_type` VARCHAR(24) NOT NULL,
  `source_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_source` (`member_id`, `source_type`, `source_id`),
  KEY `idx_cash_member_created` (`member_id`, `created_at`),
  CONSTRAINT `fk_cash_ledger_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_cash_ledger_balance` CHECK (`balance_after` >= 0),
  CONSTRAINT `chk_cash_ledger_change` CHECK (`change_amount` <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Real cash movement audit';

CREATE TABLE IF NOT EXISTS `member_referral_reward` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL COMMENT 'Direct referrer',
  `from_member_id` BIGINT NOT NULL COMMENT 'Paying member',
  `order_id` BIGINT NOT NULL,
  `basis_amount` DECIMAL(12,2) NOT NULL COMMENT 'Configured reward base snapshotted at settlement',
  `reward_rate` DECIMAL(10,6) NOT NULL,
  `reward_cap` DECIMAL(12,2) DEFAULT NULL,
  `reward_amount` DECIMAL(12,2) NOT NULL COMMENT 'Cash credited to real withdrawable account',
  `reward_points` BIGINT NOT NULL COMMENT 'Repurchase points credited',
  `status` VARCHAR(16) NOT NULL COMMENT 'POSTED or REVERSED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_referral_reward_order` (`order_id`),
  KEY `idx_referral_member_created` (`member_id`, `created_at`),
  CONSTRAINT `fk_referral_reward_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_referral_reward_from_member` FOREIGN KEY (`from_member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_referral_reward_order` FOREIGN KEY (`order_id`) REFERENCES `membership_purchase_order` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_referral_reward_nonnegative` CHECK (`basis_amount` >= 0 AND `reward_rate` >= 0 AND (`reward_cap` IS NULL OR `reward_cap` >= 0) AND `reward_amount` >= 0 AND `reward_points` >= 0),
  CONSTRAINT `chk_referral_reward_status` CHECK (`status` IN ('POSTED', 'REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Real direct referral rewards';

-- INSERT IGNORE preserves administrator changes when this migration is rerun.
-- NULL referral point values are deliberate: the award amount was not specified.
-- Payment stays disabled until credentials, callback verification and P0-1b/c/e are ready.
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_group`, `remark`) VALUES
  ('membership.purchase.enabled', '0', 'membership', 'Real membership checkout switch; keep disabled until payment integration passes'),
  ('membership.partner.price', '3999.00', 'membership', 'Partner purchase price, CNY'),
  ('membership.partner.product_points', '3999', 'membership', 'Partner product point grant'),
  ('membership.partner.repurchase_points', '3999', 'membership', 'Partner repurchase point grant'),
  ('membership.cofounder.price', '10000.00', 'membership', 'Cofounder purchase price, CNY'),
  ('membership.cofounder.product_points', '10000', 'membership', 'Cofounder product point grant'),
  ('membership.cofounder.repurchase_points', '10000', 'membership', 'Cofounder repurchase point grant'),
  ('membership.referral.reward_basis', 'PAID_AMOUNT', 'membership', 'Use verified actual payment amount'),
  ('membership.referral.cash_rate', '0.10', 'membership', 'Direct referral cash reward rate'),
  ('membership.referral.partner.cash_cap', NULL, 'membership', 'NULL means no partner cash cap specified'),
  ('membership.referral.cofounder.cash_cap', '200.00', 'membership', 'Cofounder cash reward cap, CNY'),
  ('membership.referral.partner.repurchase_points', NULL, 'membership', 'Administrator must set direct referral repurchase points'),
  ('membership.referral.cofounder.repurchase_points', NULL, 'membership', 'Administrator must set direct referral repurchase points');
