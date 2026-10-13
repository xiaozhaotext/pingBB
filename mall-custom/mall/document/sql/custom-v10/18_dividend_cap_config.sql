-- Dividend cap formula parameters (configurable via admin API)
-- Additive and repeatable on MySQL 8.

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_group`, `remark`) VALUES
  ('dividend.cap.base', '1200', 'dividend', '分红封顶基础金额（元，无分享时）'),
  ('dividend.cap.per_referral', '1200', 'dividend', '每推荐1人增加的封顶金额（元，不限人数）'),
  ('dividend.cap.max', '12000', 'dividend', '分红封顶总上限（元）');
