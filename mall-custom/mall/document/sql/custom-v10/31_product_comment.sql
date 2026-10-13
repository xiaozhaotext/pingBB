-- 商品评价（pms_comment）增量迁移 2026-10-13
--
-- 对应需求：REQ-FE-PICK（商品详情评价）与 REQ-ADM-MEMBER 订单评价
-- 原表缺少会员/订单维度，无法做到“一订单一评价”幂等校验，这里只新增列与索引，可重复执行。
-- 执行：mysql mall_demo < 31_product_comment.sql

SET @m1 = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pms_comment' AND COLUMN_NAME='member_id')=0,
  'ALTER TABLE `pms_comment` ADD COLUMN `member_id` BIGINT NULL COMMENT ''评价会员 ID''', 'SELECT 1');
PREPARE s1 FROM @m1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @m2 = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pms_comment' AND COLUMN_NAME='order_id')=0,
  'ALTER TABLE `pms_comment` ADD COLUMN `order_id` BIGINT NULL COMMENT ''来源订单 ID''', 'SELECT 1');
PREPARE s2 FROM @m2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @m3 = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='pms_comment' AND INDEX_NAME='idx_comment_member_order')=0,
  'ALTER TABLE `pms_comment` ADD INDEX `idx_comment_member_order` (`member_id`,`order_id`,`product_id`)', 'SELECT 1');
PREPARE s3 FROM @m3; EXECUTE s3; DEALLOCATE PREPARE s3;
