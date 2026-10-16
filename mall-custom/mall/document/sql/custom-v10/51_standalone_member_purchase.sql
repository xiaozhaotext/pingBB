-- Extend the existing checked order level without changing any historical orders.
-- Run after 50_three_dividend_pools.sql on MySQL 8.
SET @tri_old_level_check = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='membership_purchase_order'
 AND CONSTRAINT_NAME='chk_membership_order_level' AND CONSTRAINT_TYPE='CHECK');
SET @tri_level_ddl = IF(@tri_old_level_check>0,
 'ALTER TABLE membership_purchase_order DROP CHECK chk_membership_order_level, ADD CONSTRAINT chk_membership_order_level CHECK (level IN (''MEMBER'',''PARTNER'',''COFOUNDER''))',
 'ALTER TABLE membership_purchase_order ADD CONSTRAINT chk_membership_order_level CHECK (level IN (''MEMBER'',''PARTNER'',''COFOUNDER''))');
PREPARE tri_level_statement FROM @tri_level_ddl;
EXECUTE tri_level_statement;
DEALLOCATE PREPARE tri_level_statement;
