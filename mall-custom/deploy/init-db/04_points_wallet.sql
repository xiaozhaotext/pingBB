-- ============================================================
-- 模块C：双积分钱包（商品积分 + 复购积分）
-- 文件：03_points_wallet.sql
-- 设计要点：
--   1) 两个独立账户(ums_points_account, points_type 区分)
--   2) 所有余额变动必须写流水(ums_points_ledger)，禁止只改余额
--   3) 幂等键(idempotency_key/biz_id)防止重复发放
--   4) 乐观锁(version)防止并发覆盖
--   5) 流水/账户更新在同一事务，失败回滚
--   6) 禁止负余额(应用层+约束校验)
-- 金额单位：积分(整数)，不使用小数。
-- ============================================================

-- 双积分账户：1=商品积分 2=复购积分
DROP TABLE IF EXISTS `ums_points_account`;
CREATE TABLE `ums_points_account` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`   BIGINT       NOT NULL,
  `points_type` TINYINT      NOT NULL COMMENT '1->商品积分; 2->复购积分',
  `balance`     BIGINT       NOT NULL DEFAULT 0 COMMENT '总余额',
  `frozen`      BIGINT       NOT NULL DEFAULT 0 COMMENT '冻结余额',
  `available`   BIGINT       NOT NULL DEFAULT 0 COMMENT '可用余额 = balance - frozen',
  `version`     BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_type` (`member_id`,`points_type`),
  KEY `idx_member` (`member_id`),
  CONSTRAINT `fk_points_account_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='双积分账户表';

-- 积分账本流水（必须可追溯，禁止只改余额不记录流水）
DROP TABLE IF EXISTS `ums_points_ledger`;
CREATE TABLE `ums_points_ledger` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `member_id`        BIGINT       NOT NULL,
  `points_type`      TINYINT      NOT NULL COMMENT '1->商品积分; 2->复购积分',
  `change_type`      VARCHAR(32)  NOT NULL COMMENT 'earn/consume/freeze/unfreeze/adjust/dividend',
  `amount`           BIGINT       NOT NULL COMMENT '变动额(正=增, 负=减)',
  `balance_after`    BIGINT       NOT NULL COMMENT '变动后余额',
  `biz_id`           VARCHAR(64)  DEFAULT NULL COMMENT '业务单号(订单ID/调整单ID)',
  `idempotency_key`  VARCHAR(64)  NOT NULL COMMENT '幂等键, 防重复发放',
  `operator_id`      BIGINT       DEFAULT NULL COMMENT '操作人(后台调整时)',
  `remark`           VARCHAR(255) DEFAULT NULL,
  `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idem` (`idempotency_key`),
  KEY `idx_member_type` (`member_id`,`points_type`),
  KEY `idx_biz` (`biz_id`),
  KEY `idx_create` (`create_time`),
  CONSTRAINT `fk_points_ledger_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账本流水表';
