-- Permit a retained visitor row without losing paid-referral counters and financial history.
SET @old_check=(SELECT tc.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='tri_member' AND tc.CONSTRAINT_TYPE='CHECK' AND cc.CHECK_CLAUSE LIKE '%tier%' LIMIT 1);
SET @drop_check=IF(@old_check IS NULL,'SELECT 1',CONCAT('ALTER TABLE tri_member DROP CHECK `',@old_check,'`'));
PREPARE identity_stmt FROM @drop_check;
EXECUTE identity_stmt;
DEALLOCATE PREPARE identity_stmt;
ALTER TABLE tri_member ADD CONSTRAINT chk_tri_member_identity CHECK (tier IN ('VISITOR','MEMBER','PARTNER','COFOUNDER') AND direct_paid_count>=0);
CREATE TABLE IF NOT EXISTS tri_member_identity_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,member_id BIGINT NOT NULL,
 from_tier VARCHAR(16) NOT NULL,to_tier VARCHAR(16) NOT NULL,reason VARCHAR(200) NOT NULL,
 operator_id BIGINT NOT NULL,operator_name VARCHAR(64) NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY idx_identity_history(member_id,id)
);
