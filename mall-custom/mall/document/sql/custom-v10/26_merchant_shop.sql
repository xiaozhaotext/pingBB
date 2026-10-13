-- 商家（店铺）数据模型 2026-10-13：购物车展示商家名字
--
-- 对应需求：REQ-FE-CART-002（购物车展示商家名字，商家模式为平台自营 + 第三方入驻）
-- 只做新增：CREATE TABLE IF NOT EXISTS、按 information_schema 判断后加列，可重复执行，不含删表语句。
-- 执行：mysql mall_demo < 26_merchant_shop.sql

CREATE TABLE IF NOT EXISTS `pms_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '商家/店铺名称',
  `self_operated` tinyint NOT NULL DEFAULT 0 COMMENT '1 平台自营 0 第三方入驻',
  `enabled` tinyint NOT NULL DEFAULT 1,
  `sort` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家（店铺）';

INSERT INTO `pms_merchant` (`name`,`self_operated`,`sort`)
SELECT '平台自营',1,0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `pms_merchant` WHERE `self_operated`=1);

SET @merchant_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pms_product' AND COLUMN_NAME = 'merchant_id') = 0,
  'ALTER TABLE `pms_product` ADD COLUMN `merchant_id` BIGINT NULL COMMENT ''所属商家，见 pms_merchant''',
  'SELECT 1');
PREPARE merchant_stmt FROM @merchant_ddl; EXECUTE merchant_stmt; DEALLOCATE PREPARE merchant_stmt;

-- 现有商品全部归属平台自营；第三方入驻商家由后台后续指定。
UPDATE `pms_product` SET `merchant_id` = (SELECT id FROM `pms_merchant` WHERE `self_operated` = 1 ORDER BY id LIMIT 1)
WHERE `merchant_id` IS NULL;
