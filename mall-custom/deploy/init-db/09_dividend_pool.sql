-- ============================================================
-- 每日分红池模块（多劳多得 / 基于有效经营贡献值的比例分配）
-- 文件：08_dividend_pool.sql
-- 规则要点（严禁平均分配、严禁写死/随机收益）：
--   1) 每日分红池不平均分配
--   2) 采用基于"有效经营贡献值"的比例分配 (ratio = 个人贡献 / 当日总贡献)
--   3) 贡献值来自可核验真实业务行为(如完成订单)
--   4) 不得仅因购买身份/发展下线/缴纳入门费而自动产生收益
--   5) 退款/取消订单 -> 贡献冲回(负贡献事件)
--   6) 计算任务幂等、可追溯、可复算 (calc_batch 维度)
--   7) 资金来源/计分规则/结算周期/支付方式均列为待客户确认
--   8) 不启用真实分红入账/提现/自动付款
-- ============================================================

-- 贡献事件（可核验的真实业务行为）
DROP TABLE IF EXISTS `dividend_contribution_event`;
CREATE TABLE `dividend_contribution_event` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `member_id`         BIGINT        NOT NULL,
  `event_type`        VARCHAR(32)   NOT NULL COMMENT 'order_complete/order_refund/order_cancel',
  `biz_id`            VARCHAR(64)   NOT NULL COMMENT '业务单号(订单ID)',
  `contribution_value` DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '贡献值(正=产生,负=冲回)',
  `verify_status`     TINYINT       NOT NULL DEFAULT 1 COMMENT '1->已核验真实业务;0->待核验',
  `stat_date`         DATE          DEFAULT NULL COMMENT '所属统计日期',
  `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_event` (`biz_id`,`event_type`) COMMENT '防重复计入',
  KEY `idx_member_date` (`member_id`,`stat_date`),
  KEY `idx_date` (`stat_date`),
  CONSTRAINT `fk_div_event_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日分红池-贡献事件';

-- 每日统计（按会员/日期聚合）
DROP TABLE IF EXISTS `dividend_daily_stat`;
CREATE TABLE `dividend_daily_stat` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `stat_date`          DATE          NOT NULL,
  `member_id`          BIGINT        NOT NULL,
  `contribution_value` DECIMAL(12,4) NOT NULL DEFAULT 0.0000,
  `order_count`        INT           NOT NULL DEFAULT 0,
  `order_amount`       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_member` (`stat_date`,`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日分红池-每日统计';

-- 计算快照（幂等可复算，按 stat_date+member_id+calc_batch 唯一）
DROP TABLE IF EXISTS `dividend_snapshot`;
CREATE TABLE `dividend_snapshot` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `stat_date`          DATE          NOT NULL,
  `member_id`          BIGINT        NOT NULL,
  `contribution_value` DECIMAL(12,4) NOT NULL,
  `pool_total`         DECIMAL(12,4) NOT NULL DEFAULT 0.0000 COMMENT '当日总贡献(分母)',
  `ratio`              DECIMAL(12,8) NOT NULL DEFAULT 0.00000000 COMMENT '分配比例=个人贡献/总贡献',
  `estimated_amount`   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '模拟分配金额(非真实)',
  `simulate_flag`      TINYINT       NOT NULL DEFAULT 1,
  `calc_batch`         VARCHAR(64)   DEFAULT NULL COMMENT '计算批次(幂等)',
  `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date_member_batch` (`stat_date`,`member_id`,`calc_batch`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日分红池-计算快照';

-- 模拟分配明细
DROP TABLE IF EXISTS `dividend_allocate_detail`;
CREATE TABLE `dividend_allocate_detail` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `stat_date`        DATE          NOT NULL,
  `member_id`        BIGINT        NOT NULL,
  `ratio`            DECIMAL(12,8) NOT NULL,
  `estimated_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `simulate_flag`    TINYINT       NOT NULL DEFAULT 1,
  `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_date` (`stat_date`),
  KEY `idx_member` (`member_id`),
  CONSTRAINT `fk_div_alloc_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日分红池-模拟分配明细';

-- 异常审计
DROP TABLE IF EXISTS `dividend_exception_audit`;
CREATE TABLE `dividend_exception_audit` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `stat_date`   DATE         DEFAULT NULL,
  `member_id`   BIGINT       DEFAULT NULL,
  `reason`      VARCHAR(255) DEFAULT NULL COMMENT '异常原因(负贡献/循环贡献/数据缺失)',
  `detail`      TEXT         DEFAULT NULL,
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日分红池-异常审计';
