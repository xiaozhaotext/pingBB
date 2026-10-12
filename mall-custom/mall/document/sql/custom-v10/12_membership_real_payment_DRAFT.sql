-- 12_membership_real_payment_DRAFT.sql
-- P0-1a 真实支付改造：基于 01~10 已有表结构，只做加法，不改已有语义
-- 执行前提：01~10 已执行；可重复执行（幂等）
-- 说明：
--   biz_partner_order / biz_cofounder_order 是 04/05 建的模拟订单表
--   （pay_status: 0待支付/1模拟支付成功/2已取消，simulate_flag 默认1）。
--   本文件只追加真实支付字段，不动已有数据和语义；
--   新增 pay_status=3 表示真实支付成功。

DELIMITER //
DROP PROCEDURE IF EXISTS add_column_if_missing //
CREATE PROCEDURE add_column_if_missing(
  IN t VARCHAR(64), IN c VARCHAR(64), IN defn TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', defn);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END //

DROP PROCEDURE IF EXISTS add_index_if_missing //
CREATE PROCEDURE add_index_if_missing(
  IN t VARCHAR(64), IN idx VARCHAR(64), IN defn TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = idx
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', t, '` ADD ', defn);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

-- 1) 订单表追加真实支付字段
CALL add_column_if_missing('biz_partner_order', 'pay_channel', 'VARCHAR(16) NULL COMMENT ''微信/支付宝''');
CALL add_column_if_missing('biz_partner_order', 'transaction_id', 'VARCHAR(64) NULL COMMENT ''支付网关交易号，回调幂等用''');
CALL add_column_if_missing('biz_cofounder_order', 'pay_channel', 'VARCHAR(16) NULL COMMENT ''微信/支付宝''');
CALL add_column_if_missing('biz_cofounder_order', 'transaction_id', 'VARCHAR(64) NULL COMMENT ''支付网关交易号，回调幂等用''');

-- 2) transaction_id 唯一索引（回调幂等，防重复入账）
CALL add_index_if_missing('biz_partner_order', 'uk_transaction_id', 'UNIQUE KEY `uk_transaction_id` (`transaction_id`)');
CALL add_index_if_missing('biz_cofounder_order', 'uk_transaction_id', 'UNIQUE KEY `uk_transaction_id` (`transaction_id`)');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;

-- 3) pay_status 追加语义：3=真实支付成功（原有 0/1/2 不动）
ALTER TABLE `biz_partner_order`
  MODIFY COLUMN `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0->待支付; 1->模拟支付成功; 2->已取消; 3->真实支付成功';
ALTER TABLE `biz_cofounder_order`
  MODIFY COLUMN `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0->待支付; 1->模拟支付成功; 2->已取消; 3->真实支付成功';

-- 4) sys_config 追加支付配置键（INSERT IGNORE 幂等，全留空等商户资料）
-- 注意：sys_config.config_value 是 VARCHAR(512)，证书/私钥一律存服务器路径，不存内容
INSERT IGNORE INTO sys_config (config_key, config_value, config_group, remark) VALUES
('pay.wx.mchid',      '', 'pay', '微信商户号（待商户资料）'),
('pay.wx.apiv3_key',  '', 'pay', '微信APIv3密钥（待商户资料，加密存）'),
('pay.wx.cert_path',  '', 'pay', '微信证书服务器路径（待商户资料）'),
('pay.wx.notify_url', '', 'pay', '微信回调地址'),
('pay.ali.appid',       '', 'pay', '支付宝APPID（待商户资料）'),
('pay.ali.private_key', '', 'pay', '支付宝应用私钥服务器路径（待商户资料）'),
('pay.ali.public_key',  '', 'pay', '支付宝公钥（待商户资料）'),
('pay.ali.notify_url',  '', 'pay', '支付宝回调地址');
