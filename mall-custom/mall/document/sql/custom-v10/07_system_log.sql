-- ============================================================
-- 模块G：系统设置与日志
-- 文件：07_system_log.sql
-- 说明：基础系统参数、会员/积分配置展示、管理员操作日志、财务操作审计、权限校验。
--       短信/微信等外部服务仅保留真实配置入口与未连接状态，不伪造接入成功。
-- ============================================================

-- 系统参数配置
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `config_key`    VARCHAR(64)  NOT NULL,
  `config_value`  VARCHAR(512) DEFAULT NULL,
  `config_group`  VARCHAR(32)  DEFAULT 'default' COMMENT 'member/points/partner/cofounder/dividend/withdraw',
  `remark`        VARCHAR(255) DEFAULT NULL,
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置表';

-- 管理员操作审计日志
DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `operator_id`   BIGINT       DEFAULT NULL,
  `operator_name` VARCHAR(64)  DEFAULT NULL,
  `module`        VARCHAR(32)  DEFAULT NULL COMMENT 'member/invitation/points/partner/cofounder/earnings/dividend',
  `action`        VARCHAR(64)  DEFAULT NULL,
  `target_id`     BIGINT       DEFAULT NULL,
  `detail`        TEXT         DEFAULT NULL,
  `ip`            VARCHAR(64)  DEFAULT NULL,
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作审计日志';

-- 财务操作审计日志
DROP TABLE IF EXISTS `sys_finance_audit`;
CREATE TABLE `sys_finance_audit` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `related_type`   VARCHAR(32)  DEFAULT NULL COMMENT 'points/earnings/withdraw/dividend',
  `related_id`     BIGINT       DEFAULT NULL,
  `operator_id`    BIGINT       DEFAULT NULL,
  `action`         VARCHAR(64)  DEFAULT NULL,
  `amount`         DECIMAL(12,2) DEFAULT NULL,
  `simulate_flag`  TINYINT      NOT NULL DEFAULT 1 COMMENT '1->模拟操作',
  `detail`         TEXT         DEFAULT NULL,
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_related` (`related_type`,`related_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务操作审计日志';

-- 初始系统参数种子（均为待确认/模拟参数）
INSERT INTO `sys_config` (`config_key`, `config_value`, `config_group`, `remark`) VALUES
('points.product.name', '商品积分', 'points', '商品积分名称'),
('points.repurchase.name', '复购积分', 'points', '复购积分名称'),
('partner.price', '3999.00', 'partner', '合伙人权益价格(模拟)'),
('partner.direct_share_rate', '0.10', 'partner', '直接分享奖励比例(待确认)'),
('cofounder.price', '10000.00', 'cofounder', '联创权益价格(模拟)'),
('cofounder.share_reward_cap', '200.00', 'cofounder', '分享奖励上限(元,待确认)'),
('withdraw.fee_rate', '0.006', 'withdraw', '提现手续费率0.6%(待审批)'),
('dividend.pool.source', '待客户确认', 'dividend', '分红池资金来源(待确认)'),
('dividend.settle.cycle', '待客户确认', 'dividend', '结算周期(待确认)'),
('sms.provider', 'unconnected', 'external', '短信服务:未连接(不伪造接入)'),
('wechat.provider', 'unconnected', 'external', '微信服务:未连接(不伪造接入)');
