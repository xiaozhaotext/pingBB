-- ============================================================
-- 模块A：会员身份管理
-- 文件：01_member_identity.sql
-- 说明：在现有 ums_member 基础上扩展身份类型（普通会员/合伙人/联创）
--       与身份变更审计。不修改 ums_member 原表。
-- 回滚：见 rollback_all.sql 或执行本文件末尾 DROP 语句。
-- ============================================================

-- 当前生效身份（每个会员一条）
DROP TABLE IF EXISTS `ums_member_identity`;
CREATE TABLE `ums_member_identity` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`     BIGINT       NOT NULL COMMENT '会员ID -> ums_member.id',
  `identity_type` TINYINT      NOT NULL DEFAULT 0 COMMENT '身份类型: 0->普通会员; 1->合伙人; 2->联创',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0->失效; 1->生效',
  `become_time`   DATETIME     DEFAULT NULL COMMENT '获得身份时间',
  `expire_time`   DATETIME     DEFAULT NULL COMMENT '过期时间(NULL=永久)',
  `operator_id`   BIGINT       DEFAULT NULL COMMENT '后台操作人ID',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member` (`member_id`),
  KEY `idx_identity_type` (`identity_type`),
  CONSTRAINT `fk_identity_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员当前生效身份表';

-- 身份变更记录（审计）
DROP TABLE IF EXISTS `ums_member_identity_log`;
CREATE TABLE `ums_member_identity_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`    BIGINT       NOT NULL COMMENT '会员ID',
  `from_type`    TINYINT      DEFAULT NULL COMMENT '原身份类型',
  `to_type`      TINYINT      NOT NULL COMMENT '新身份类型',
  `reason`       VARCHAR(255) DEFAULT NULL COMMENT '变更原因',
  `operator_id`  BIGINT       DEFAULT NULL COMMENT '操作人ID',
  `biz_order_id` BIGINT       DEFAULT NULL COMMENT '关联业务单(如模拟购买订单ID)',
  `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员身份变更审计日志';
