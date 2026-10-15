-- Idempotent MySQL migration; historical rows keep a NULL request key.
SET @withdraw_column_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ums_withdraw_apply' AND COLUMN_NAME='idempotency_key')=0,
 'ALTER TABLE ums_withdraw_apply ADD COLUMN idempotency_key VARCHAR(64) NULL', 'SELECT 1');
PREPARE withdraw_column_stmt FROM @withdraw_column_sql;
EXECUTE withdraw_column_stmt;
DEALLOCATE PREPARE withdraw_column_stmt;
SET @withdraw_index_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ums_withdraw_apply' AND INDEX_NAME='uk_withdraw_member_request')=0,
 'CREATE UNIQUE INDEX uk_withdraw_member_request ON ums_withdraw_apply(member_id,idempotency_key)', 'SELECT 1');
PREPARE withdraw_index_stmt FROM @withdraw_index_sql;
EXECUTE withdraw_index_stmt;
DEALLOCATE PREPARE withdraw_index_stmt;
