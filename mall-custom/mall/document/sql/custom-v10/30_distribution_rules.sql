-- 分销 / 分红 / 复购 / 提现 规则默认值（幂等）
-- 只在配置缺失或为空时写入，绝不覆盖后台已经修改过的值。
INSERT INTO sys_config(config_key, config_value, config_group, remark) VALUES
  ('distribution.enabled','1','distribution','分销体系总开关(0关闭/1开启)'),
  ('dividend.distribution.enabled','0','distribution','每日分红发放开关(0关闭/1开启，规则未定稿前保持关闭)'),
  ('dividend.pool.ratio','0.30','distribution','购买金额进入分红池的比例(0-1)'),
  ('dividend.daily.base','1200.00','distribution','每日每份分红基数(元)，作为上限；池余额不足时按比例缩水'),
  ('dividend.cap.base','1200.00','distribution','无分享时的分红封顶(元)'),
  ('dividend.cap.per_referral','1200.00','distribution','每推荐1人封顶增加(元)'),
  ('dividend.cap.max','12000.00','distribution','分红封顶上限(元)'),
  ('membership.referral.cash_rate','0.10','distribution','直推现金奖励比例(0-1)'),
  ('membership.referral.partner.cash_cap',NULL,'distribution','合伙人直推现金封顶(元)，空=不限'),
  ('membership.referral.cofounder.cash_cap','200.00','distribution','联创直推现金封顶(元)，空=不限'),
  ('membership.referral.partner.repurchase_points','3999','distribution','合伙人直推赠送复购积分'),
  ('membership.referral.cofounder.repurchase_points','10000','distribution','联创直推赠送复购积分'),
  ('share.order.bonus.enabled','1','distribution','分享下单奖励开关(0关闭/1开启)'),
  ('share.order.bonus','200.00','distribution','分享每单奖励(元)'),
  ('rebuy.points.ratio','0.30','distribution','复购区积分抵扣比例(0-1)，其余为现金'),
  ('withdraw.fee_rate','0.006','distribution','提现手续费率(0-1)'),
  ('withdraw.min_amount','1.00','distribution','单笔提现最低金额(元)')
ON DUPLICATE KEY UPDATE
  config_value = IF(config_value IS NULL OR config_value = '' OR UPPER(config_value) = 'NULL',
                    VALUES(config_value), config_value),
  config_group = IF(config_group IS NULL OR config_group = 'default', VALUES(config_group), config_group),
  remark       = IF(remark IS NULL OR remark = '', VALUES(remark), remark);
