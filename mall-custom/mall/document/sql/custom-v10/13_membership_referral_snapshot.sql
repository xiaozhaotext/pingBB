-- P0-1e: retain the platform-admin-approved direct referral rule on each order.
-- Additive and repeatable on MySQL 8; run after 12_membership_real_payment.sql.
-- No existing paid order is backfilled with a guessed reward.

SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='membership_purchase_order' AND COLUMN_NAME='referrer_member_id')=0,
  'ALTER TABLE membership_purchase_order ADD COLUMN referrer_member_id BIGINT DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;

SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='membership_purchase_order' AND COLUMN_NAME='referral_cash_rate')=0,
  'ALTER TABLE membership_purchase_order ADD COLUMN referral_cash_rate DECIMAL(10,6) DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;

SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='membership_purchase_order' AND COLUMN_NAME='referral_cash_cap')=0,
  'ALTER TABLE membership_purchase_order ADD COLUMN referral_cash_cap DECIMAL(12,2) DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;

SET @membership_ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME='membership_purchase_order' AND COLUMN_NAME='referral_repurchase_points')=0,
  'ALTER TABLE membership_purchase_order ADD COLUMN referral_repurchase_points BIGINT DEFAULT NULL', 'SELECT 1');
PREPARE membership_stmt FROM @membership_ddl; EXECUTE membership_stmt; DEALLOCATE PREPARE membership_stmt;

-- Platform admins must set the two membership.referral.*.repurchase_points values
-- before checkout is enabled. Existing paid orders without a snapshot need manual review.
