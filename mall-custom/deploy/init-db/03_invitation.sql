-- ============================================================
-- 模块B：邀请码与邀请关系
-- 文件：02_invitation.sql
-- 说明：每位会员唯一邀请码；推荐关系绑定（防重复绑定/防循环/防自改）。
--       邀请码来自真实数据库，不做写死。
-- ============================================================

-- 会员邀请码（一人一码，唯一）
DROP TABLE IF EXISTS `ums_invitation_code`;
CREATE TABLE `ums_invitation_code` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`   BIGINT       NOT NULL COMMENT '会员ID -> ums_member.id',
  `code`        VARCHAR(32)  NOT NULL COMMENT '邀请码(唯一,真实生成)',
  `qr_content`  VARCHAR(512) DEFAULT NULL COMMENT '二维码内容(邀请链接/文本)',
  `short_link`  VARCHAR(512) DEFAULT NULL COMMENT '邀请短链',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1->有效; 0->失效',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_member` (`member_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_invcode_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员邀请码表';

-- 推荐关系（每个会员仅能被绑定一次；bind_path 用于防循环）
DROP TABLE IF EXISTS `ums_member_recommend`;
CREATE TABLE `ums_member_recommend` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`  BIGINT       NOT NULL COMMENT '被推荐人(下级) -> ums_member.id',
  `inviter_id` BIGINT       NOT NULL COMMENT '推荐人(上级) -> ums_member.id',
  `invite_code` VARCHAR(32) DEFAULT NULL COMMENT '使用的邀请码',
  `bind_order_id` BIGINT    DEFAULT NULL COMMENT '触发绑定的订单ID(注册绑定则为空)',
  `bind_path`  VARCHAR(512) DEFAULT NULL COMMENT '推荐链,如 /1/3/5 ,用于防循环检测',
  `bind_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member` (`member_id`) COMMENT '防重复绑定(一人只能有一次上级)',
  KEY `idx_inviter` (`inviter_id`),
  KEY `idx_code` (`invite_code`),
  CONSTRAINT `fk_recommend_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_recommend_inviter` FOREIGN KEY (`inviter_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员推荐关系表';

-- 推荐关系绑定审计（防止会员自行修改推荐人）
DROP TABLE IF EXISTS `ums_member_recommend_audit`;
CREATE TABLE `ums_member_recommend_audit` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`    BIGINT       NOT NULL,
  `inviter_id`   BIGINT       DEFAULT NULL,
  `invite_code`  VARCHAR(32)  DEFAULT NULL,
  `action`       VARCHAR(32)  NOT NULL COMMENT 'bind/reject/cycle_blocked/self_blocked',
  `operator_id`  BIGINT       DEFAULT NULL,
  `detail`       VARCHAR(512) DEFAULT NULL,
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐关系绑定审计日志';
