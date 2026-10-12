-- P0: admin-configurable keys for payment credentials, SMS provider and dividend rules.
-- Additive and repeatable on MySQL 8; run after 13_membership_referral_snapshot.sql.
-- INSERT IGNORE preserves administrator changes when this migration is rerun.
-- NULL values are deliberate: credentials and rule parameters must be filled by the platform admin.

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_group`, `remark`) VALUES
  ('pay.wechat.mch_id', NULL, 'pay', '微信支付商户号'),
  ('pay.wechat.api_key', NULL, 'pay', '微信支付 API 密钥（敏感，列表脱敏显示）'),
  ('pay.wechat.app_id', NULL, 'pay', '微信支付 AppID'),
  ('pay.alipay.app_id', NULL, 'pay', '支付宝 AppID'),
  ('pay.alipay.private_key', NULL, 'pay', '支付宝应用私钥（敏感，列表脱敏显示）'),
  ('pay.alipay.public_key', NULL, 'pay', '支付宝公钥（敏感，列表脱敏显示）'),
  ('sms.provider', NULL, 'sms', '短信服务商：aliyun/tencent，空为未配置'),
  ('sms.access_key', NULL, 'sms', '短信服务商 AccessKey'),
  ('sms.access_secret', NULL, 'sms', '短信服务商 AccessSecret（敏感，列表脱敏显示）'),
  ('sms.sign_name', NULL, 'sms', '短信签名'),
  ('sms.template_code', NULL, 'sms', '短信模板 Code'),
  ('dividend.pool.ratio', NULL, 'dividend', '购买金额进入分红池的比例（0-1，最多4位小数）'),
  ('dividend.daily.base', NULL, 'dividend', '每日每份分红基数（元，≥0）');
