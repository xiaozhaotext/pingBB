-- Message notification system
-- Additive and repeatable on MySQL 8.

CREATE TABLE IF NOT EXISTS `sys_notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(128) NOT NULL,
  `content` TEXT NOT NULL,
  `type` VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/PROMOTION/ORDER',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=published, 0=draft',
  `publish_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System notices';

CREATE TABLE IF NOT EXISTS `sys_notice_read` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `notice_id` BIGINT NOT NULL,
  `member_id` BIGINT NOT NULL,
  `read_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_member` (`notice_id`, `member_id`),
  CONSTRAINT `fk_notice_read_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Notice read status';
