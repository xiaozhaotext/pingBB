-- Order idempotency request tracking
-- Additive and repeatable on MySQL 8.

CREATE TABLE IF NOT EXISTS `oms_order_request` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL,
  `idempotency_key` VARCHAR(64) NOT NULL,
  `request_hash` VARCHAR(64) NOT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `state` VARCHAR(16) NOT NULL DEFAULT 'CREATING' COMMENT 'CREATING/COMPLETED/FAILED',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_key` (`member_id`, `idempotency_key`),
  KEY `idx_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Order idempotency tracking';
