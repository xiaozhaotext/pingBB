-- Preserve existing keys and widen only when the older migration used VARCHAR(64).
-- The backend accepts 16..100 ASCII characters. No rows are deleted or rewritten.
SET @key_ddl = IF(
 (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
  AND TABLE_NAME='oms_order_request' AND COLUMN_NAME='idempotency_key') < 100,
 'ALTER TABLE oms_order_request MODIFY idempotency_key VARCHAR(100) NOT NULL', 'SELECT 1');
PREPARE key_stmt FROM @key_ddl; EXECUTE key_stmt; DEALLOCATE PREPARE key_stmt;
