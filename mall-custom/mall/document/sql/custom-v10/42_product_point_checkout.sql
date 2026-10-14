-- Product points are worth one yuan each and may cover the full payable amount.
-- Only insert missing settings; existing administrator choices remain unchanged.
INSERT IGNORE INTO sys_config(config_key,config_value,config_group,remark) VALUES
  ('product.points.yuan_per_point','1.00','distribution','每 1 产品积分可抵扣的金额（元）'),
  ('product.points.max_ratio','1.00','distribution','产品积分最多抵扣应付金额的比例（0-1）');

CREATE TABLE IF NOT EXISTS oms_order_point_usage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  point_type VARCHAR(16) NOT NULL,
  used_points BIGINT NOT NULL,
  deducted_amount DECIMAL(12,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_point_type (order_id,point_type),
  KEY idx_member_order (member_id,order_id),
  CONSTRAINT chk_order_point_type CHECK (point_type IN ('PRODUCT','REPURCHASE')),
  CONSTRAINT chk_order_point_amount CHECK (used_points > 0 AND deducted_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品订单积分预扣及取消返还依据';
