SET @sms_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_message_record' AND column_name='provider')=0, 'ALTER TABLE sys_message_record ADD COLUMN provider VARCHAR(32) NULL', 'SELECT 1');
PREPARE sms_stmt FROM @sms_ddl;
EXECUTE sms_stmt;
DEALLOCATE PREPARE sms_stmt;

SET @sms_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_message_record' AND column_name='provider_code')=0, 'ALTER TABLE sys_message_record ADD COLUMN provider_code VARCHAR(120) NULL', 'SELECT 1');
PREPARE sms_stmt FROM @sms_ddl;
EXECUTE sms_stmt;
DEALLOCATE PREPARE sms_stmt;

SET @sms_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_message_record' AND column_name='provider_message')=0, 'ALTER TABLE sys_message_record ADD COLUMN provider_message VARCHAR(500) NULL', 'SELECT 1');
PREPARE sms_stmt FROM @sms_ddl;
EXECUTE sms_stmt;
DEALLOCATE PREPARE sms_stmt;

SET @sms_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_message_record' AND column_name='request_id')=0, 'ALTER TABLE sys_message_record ADD COLUMN request_id VARCHAR(100) NULL', 'SELECT 1');
PREPARE sms_stmt FROM @sms_ddl;
EXECUTE sms_stmt;
DEALLOCATE PREPARE sms_stmt;

SET @sms_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_message_record' AND column_name='serial_no')=0, 'ALTER TABLE sys_message_record ADD COLUMN serial_no VARCHAR(100) NULL', 'SELECT 1');
PREPARE sms_stmt FROM @sms_ddl;
EXECUTE sms_stmt;
DEALLOCATE PREPARE sms_stmt;
