-- ============================================================
-- 模块E：联创体系（模拟）
-- 文件：05_cofounder.sql
-- 说明：10000元权益方案展示与模拟购买；联创积分/三份分红/分享奖励(上限200元)
--       仅实现隔离的模拟计算与展示，不进行真实收益入账或支付。
-- ============================================================

-- 联创权益配置（规则草案）
DROP TABLE IF EXISTS `biz_cofounder_config`;
CREATE TABLE `biz_cofounder_config` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `price`             DECIMAL(10,2) NOT NULL DEFAULT 10000.00 COMMENT '权益价格(元,模拟)',
  `cofounder_points`  INT           NOT NULL DEFAULT 10000 COMMENT '联创积分',
  `share_reward_cap`  DECIMAL(10,2) NOT NULL DEFAULT 200.00 COMMENT '分享奖励上限(200元)',
  `dividend_count`    INT           NOT NULL DEFAULT 3 COMMENT '三份分红',
  `simulate_flag`     TINYINT       NOT NULL DEFAULT 1,
  `enabled`           TINYINT       NOT NULL DEFAULT 1,
  `remark`            VARCHAR(255)  DEFAULT NULL,
  `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联创权益配置(规则草案)';

-- 联创模拟购买订单
DROP TABLE IF EXISTS `biz_cofounder_order`;
CREATE TABLE `biz_cofounder_order` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `member_id`    BIGINT        NOT NULL,
  `amount`       DECIMAL(10,2) NOT NULL,
  `pay_status`   TINYINT       NOT NULL DEFAULT 0 COMMENT '0->待支付; 1->模拟支付成功; 2->已取消',
  `simulate_flag` TINYINT       NOT NULL DEFAULT 1,
  `biz_id`       VARCHAR(64)   DEFAULT NULL,
  `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `pay_time`     DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz` (`biz_id`),
  KEY `idx_member` (`member_id`),
  CONSTRAINT `fk_cofounder_order_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联创模拟购买订单';

INSERT INTO `biz_cofounder_config` (`price`, `cofounder_points`, `share_reward_cap`, `dividend_count`, `simulate_flag`, `enabled`, `remark`)
VALUES (10000.00, 10000, 200.00, 3, 1, 1, '联创10000权益方案(模拟,规则待确认)');
