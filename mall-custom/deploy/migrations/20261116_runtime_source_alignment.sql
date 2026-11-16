-- 仅补齐缺失结构；线上已有字段和业务数据保持不变。执行前备份数据库。
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_pick_activity' AND column_name='is_open'), 'SELECT 1', 'ALTER TABLE pms_pick_activity ADD COLUMN is_open TINYINT NOT NULL DEFAULT 1');
PREPARE runtime_alignment FROM @ddl;
EXECUTE runtime_alignment;
DEALLOCATE PREPARE runtime_alignment;
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pms_product_zone' AND column_name='consume_points_per_yuan'), 'SELECT 1', 'ALTER TABLE pms_product_zone ADD COLUMN consume_points_per_yuan INT NOT NULL DEFAULT 100');
PREPARE runtime_alignment FROM @ddl;
EXECUTE runtime_alignment;
DEALLOCATE PREPARE runtime_alignment;
