-- Online consultation (message + admin reply)
-- Additive and repeatable on MySQL 8.

CREATE TABLE IF NOT EXISTS `sys_consultation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT DEFAULT NULL COMMENT 'NULL for guest',
  `contact` VARCHAR(128) DEFAULT NULL COMMENT 'Phone or WeChat for guest',
  `subject` VARCHAR(128) NOT NULL,
  `content` TEXT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending, 1=replied, 2=closed',
  `reply` TEXT DEFAULT NULL,
  `reply_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_sys_consultation_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Online consultation messages';
