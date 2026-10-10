-- ============================================================
-- 模块D：合伙人体系（模拟）
-- 文件：04_partner.sql
-- 说明：3999元权益方案展示与模拟购买；积分发放/分红/奖励仅做可配置规则草案
--       与模拟计算，不启用真实资金或自动奖励发放（待业务确认+合规审批）。
-- ============================================================

-- 合伙人权益配置（规则草案，可配置）
DROP TABLE IF EXISTS `biz_partner_config`;
CREATE TABLE `biz_partner_config` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
  `price`              DECIMAL(10,2) NOT NULL DEFAULT 3999.00 COMMENT '权益价格(元,演示/模拟)',
  `product_points`     INT           NOT NULL DEFAULT 3999 COMMENT '赠送商品积分',
  `repurchase_points`  INT           NOT NULL DEFAULT 3999 COMMENT '赠送复购积分',
  `daily_dividend_rate` DECIMAL(10,6) DEFAULT NULL COMMENT '每日分红比例(草案,待确认)',
  `direct_share_rate`  DECIMAL(10,6) DEFAULT 0.10 COMMENT '直接分享奖励比例(10%)',
  `simulate_flag`      TINYINT       NOT NULL DEFAULT 1 COMMENT '1->模拟,不启用真实资金',
  `enabled`            TINYINT       NOT NULL DEFAULT 1,
  `remark`             VARCHAR(255)  DEFAULT NULL,
  `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合伙人权益配置(规则草案)';

-- 合伙人模拟购买订单（模拟支付后身份升级）
DROP TABLE IF EXISTS `biz_partner_order`;
CREATE TABLE `biz_partner_order` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `member_id`    BIGINT        NOT NULL,
  `amount`       DECIMAL(10,2) NOT NULL,
  `pay_status`   TINYINT       NOT NULL DEFAULT 0 COMMENT '0->待支付; 1->模拟支付成功; 2->已取消',
  `simulate_flag` TINYINT       NOT NULL DEFAULT 1,
  `biz_id`       VARCHAR(64)   DEFAULT NULL COMMENT '幂等键',
  `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  `pay_time`     DATETIME      DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz` (`biz_id`),
  KEY `idx_member` (`member_id`),
  CONSTRAINT `fk_partner_order_member` FOREIGN KEY (`member_id`) REFERENCES `ums_member` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合伙人模拟购买订单';

-- 默认配置种子
INSERT INTO `biz_partner_config` (`price`, `product_points`, `repurchase_points`, `direct_share_rate`, `simulate_flag`, `enabled`, `remark`)
VALUES (3999.00, 3999, 3999, 0.10, 1, 1, '合伙人3999权益方案(模拟,规则待确认)');
