-- Four-tier plan. Apply after 50_bonus_dividend_pools.sql.
-- This migration does not rewrite historical payments or dividends.
CREATE TABLE IF NOT EXISTS member_bonus_dividend_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  pool_code VARCHAR(16) NOT NULL,
  stat_date DATE NOT NULL,
  shares INT NOT NULL,
  total_shares INT NOT NULL,
  dividend_amount DECIMAL(12,2) NOT NULL,
  cap_amount DECIMAL(12,2) NOT NULL,
  cumulative_received DECIMAL(14,2) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_member_pool_date (member_id, pool_code, stat_date),
  KEY idx_pool_date (pool_code, stat_date),
  CONSTRAINT fk_four_tier_member FOREIGN KEY (member_id) REFERENCES ums_member(id),
  CONSTRAINT fk_four_tier_pool FOREIGN KEY (pool_code) REFERENCES bonus_dividend_pool(pool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS member_custom_travel_request (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  destination VARCHAR(100) NOT NULL,
  departure_date DATE DEFAULT NULL,
  traveler_count INT NOT NULL,
  requirements VARCHAR(2000) DEFAULT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
  admin_note VARCHAR(500) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_custom_travel_member (member_id, create_time),
  KEY idx_custom_travel_status (status, create_time),
  CONSTRAINT fk_custom_travel_member FOREIGN KEY (member_id) REFERENCES ums_member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- These are the newly approved 3999-yuan membership rewards. Existing posted
-- rewards keep their original order snapshots and are not recalculated.
INSERT INTO sys_config(config_key,config_value,config_group,remark) VALUES
  ('membership.partner.price','3999.00','membership','会员身份牌价格'),
  ('membership.partner.product_points','3999','membership','会员身份牌赠送消费积分'),
  ('membership.partner.repurchase_points','3999','membership','会员身份牌赠送复购积分'),
  ('membership.referral.cash_rate','0.10','distribution','会员直推返利比例'),
  ('membership.referral.partner.repurchase_points','1200','distribution','3999会员直推复购积分'),
  ('membership.referral.partner.cash_cap',NULL,'distribution','3999会员直推返利不另设单笔封顶'),
  ('legacy.manual_benefits.enabled','0','membership','旧每月奖励与一次性旅游权益申请开关')
ON DUPLICATE KEY UPDATE config_value=VALUES(config_value), remark=VALUES(remark);
