-- Apply before deploying repeat-membership code. Preserve source_key payment idempotency.
-- Remove only single-column unique indexes on member_id, retaining every financial row.
SET @tri_repeat_drops=(SELECT GROUP_CONCAT(CONCAT('DROP INDEX `',index_name,'`') SEPARATOR ', ')
 FROM (SELECT index_name FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_payment_event' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY'
 GROUP BY index_name HAVING COUNT(*)=1 AND MAX(column_name)='member_id') AS old_unique);
SET @tri_repeat_ddl=IF(@tri_repeat_drops IS NULL,'SELECT 1',CONCAT('ALTER TABLE tri_payment_event ',@tri_repeat_drops));
PREPARE tri_repeat_stmt FROM @tri_repeat_ddl; EXECUTE tri_repeat_stmt; DEALLOCATE PREPARE tri_repeat_stmt;
SET @tri_repeat_ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tri_payment_event' AND INDEX_NAME='ix_tri_payment_member')=0,
 'ALTER TABLE tri_payment_event ADD INDEX ix_tri_payment_member(member_id)','SELECT 1');
PREPARE tri_repeat_stmt FROM @tri_repeat_ddl; EXECUTE tri_repeat_stmt; DEALLOCATE PREPARE tri_repeat_stmt;
