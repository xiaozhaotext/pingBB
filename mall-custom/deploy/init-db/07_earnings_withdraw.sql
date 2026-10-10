-- ============================================================
-- 模块F：收益与提现（模拟）
-- 文件：06_earnings_withdraw.sql
-- 说明：所有金额均为"模拟金额"，明确标识，不得展示为真实可提现资产。
--       提现状态流转：申请->审核->模拟处理->完成/拒绝。0.6%手续费为待审批参数。
--       不进行真实扣费、银行转账或第三方付款。
-- ============================================================

-- 会员收益账户（模拟）
DROP TABLE IF EXISTS `ums_earnings`;
CREATE TABLE `ums_earnings` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `member_id`          BIGINT        NOT NULL,
  `total_earnings`     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计收益(模拟)',
  `available_earnings` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '可提现(模拟)',
  `frozen_earnings`    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结(模拟)',
  `withdrawn`          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '已提现(模拟)',
  `version`            BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member` (`member_id`),
  CONSTRAINT `fk_earnings_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员收益账户(模拟)';

-- 收益流水（模拟）
DROP TABLE IF EXISTS `ums_earnings_ledger`;
CREATE TABLE `ums_earnings_ledger` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `member_id`        BIGINT        NOT NULL,
  `change_type`      VARCHAR(32)   NOT NULL COMMENT 'simulate_reward/withdraw_apply/withdraw_done/withdraw_reject',
  `amount`           DECIMAL(12,2) NOT NULL,
  `balance_after`    DECIMAL(12,2) NOT NULL,
  `biz_id`           VARCHAR(64)   DEFAULT NULL,
  `idempotency_key`  VARCHAR(64)   DEFAULT NULL,
  `remark`           VARCHAR(255)  DEFAULT NULL,
  `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idem` (`idempotency_key`),
  KEY `idx_member` (`member_id`),
  KEY `idx_create` (`create_time`),
  CONSTRAINT `fk_earnings_ledger_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收益流水(模拟)';

-- 提现申请（模拟状态流转）
DROP TABLE IF EXISTS `ums_withdraw_apply`;
CREATE TABLE `ums_withdraw_apply` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `member_id`      BIGINT        NOT NULL,
  `amount`         DECIMAL(12,2) NOT NULL COMMENT '申请金额',
  `fee_rate`       DECIMAL(10,6) NOT NULL DEFAULT 0.006 COMMENT '手续费率0.6%(待审批参数)',
  `fee_amount`     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '手续费(模拟计算)',
  `actual_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '实际到账(模拟)',
  `status`         TINYINT       NOT NULL DEFAULT 0 COMMENT '0->申请;1->审核通过;2->模拟处理中;3->完成;4->拒绝',
  `audit_id`       BIGINT        DEFAULT NULL COMMENT '审核管理员ID',
  `audit_remark`   VARCHAR(255)  DEFAULT NULL,
  `apply_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `audit_time`     DATETIME      DEFAULT NULL,
  `simulate_flag`  TINYINT       NOT NULL DEFAULT 1 COMMENT '1->模拟,无真实打款',
  `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member` (`member_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_withdraw_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请表(模拟状态流转)';
