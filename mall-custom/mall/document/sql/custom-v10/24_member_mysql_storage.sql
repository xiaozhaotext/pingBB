-- 收藏 / 足迹 / 品牌关注 改为 MySQL 存储（2026-10-13）
--
-- 背景：上游把这三类会员数据放在 MongoDB。演示服务器没有安装 MongoDB，
--   导致 /portal/member/productCollection/list、/portal/member/readHistory/list、
--   /portal/member/attention/list 固定等待约 30 秒后返回 500。
--   应用侧已改为读写 MySQL，不再依赖 MongoDB，本脚本提供对应的三张表。
--
-- 只做新增，可重复执行，不含删表语句，也不修改任何既有表。
-- 字段沿用原 Mongo 文档，避免改变对外接口结构。
-- 执行：mysql mall_demo < 24_member_mysql_storage.sql

CREATE TABLE IF NOT EXISTS `ums_member_product_collection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `member_nickname` varchar(64) DEFAULT NULL,
  `member_icon` varchar(255) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `product_pic` varchar(255) DEFAULT NULL,
  `product_sub_title` varchar(255) DEFAULT NULL,
  `product_price` decimal(10,2) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_product` (`member_id`, `product_id`),
  KEY `idx_member_create` (`member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员商品收藏';

CREATE TABLE IF NOT EXISTS `ums_member_read_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `member_nickname` varchar(64) DEFAULT NULL,
  `member_icon` varchar(255) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `product_pic` varchar(255) DEFAULT NULL,
  `product_sub_title` varchar(255) DEFAULT NULL,
  `product_price` decimal(10,2) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_create` (`member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员商品浏览足迹';

CREATE TABLE IF NOT EXISTS `ums_member_brand_attention` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `member_nickname` varchar(64) DEFAULT NULL,
  `member_icon` varchar(255) DEFAULT NULL,
  `brand_id` bigint NOT NULL,
  `brand_name` varchar(255) DEFAULT NULL,
  `brand_logo` varchar(255) DEFAULT NULL,
  `brand_city` varchar(255) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_brand` (`member_id`, `brand_id`),
  KEY `idx_member_create` (`member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员品牌关注';
