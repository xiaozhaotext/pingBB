-- Add a durable collision guard. Existing duplicates must be backed up and
-- reconciled by order ID before this migration; ALTER fails rather than losing data.
SET @has_unique_order_sn = (SELECT COUNT(*) FROM (
 SELECT INDEX_NAME FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='oms_order' AND NON_UNIQUE=0
 GROUP BY INDEX_NAME HAVING COUNT(*)=1 AND MAX(COLUMN_NAME)='order_sn'
) existing_unique);
SET @order_sn_ddl = IF(@has_unique_order_sn>0,'SELECT 1',
 'ALTER TABLE oms_order ADD UNIQUE KEY uk_oms_order_sn (order_sn)');
PREPARE order_sn_stmt FROM @order_sn_ddl;
EXECUTE order_sn_stmt;
DEALLOCATE PREPARE order_sn_stmt;
