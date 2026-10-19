-- Platform settings centre: company profile, image watermark, mini program credentials, IP access,
-- long-form compliance content and the sidebar entries for the new admin pages.
-- Additive and repeatable on MySQL 8; INSERT IGNORE keeps every administrator change.
-- Run after 20_operations_permissions.sql (or after 10_admin_p0.sql on the current demo database).

CREATE TABLE IF NOT EXISTS `sys_content` (
  `content_key`   VARCHAR(64)  NOT NULL,
  `content_value` MEDIUMTEXT   DEFAULT NULL,
  `operator`      VARCHAR(64)  DEFAULT NULL,
  `remark`        VARCHAR(255) DEFAULT NULL,
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`content_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Long-form platform content (privacy policy, agreements)';

-- NULL means "not configured yet"; the admin page and the storefront both tolerate empty values.
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_group`, `remark`) VALUES
  ('company.name',          '智游美', 'company',     '公司名称'),
  ('company.wechat_qrcode', NULL,    'company',     '公司微信二维码图片地址'),
  ('company.address',       NULL,    'company',     '公司地址'),
  ('company.phone',         NULL,    'company',     '公司电话'),
  ('company.mobile',        NULL,    'company',     '手机号码'),
  ('company.email',         NULL,    'company',     '公司邮箱'),
  ('company.qq',            NULL,    'company',     '客服 QQ'),
  ('watermark.enabled',     '0',     'watermark',   '是否在商品图片上叠加水印：0 关闭 / 1 开启'),
  ('watermark.text',        '智游美', 'watermark',   '水印文字'),
  ('watermark.position',    'bottomRight', 'watermark', '水印位置：topLeft/topRight/bottomLeft/bottomRight/tile'),
  ('watermark.opacity',     '0.35',  'watermark',   '水印不透明度（0-1）'),
  ('watermark.size',        '18',    'watermark',   '水印字号（px，8-200）'),
  ('miniapp.name',          '智游美', 'miniapp',     '小程序名称'),
  ('miniapp.appid',         NULL,    'miniapp',     '小程序 AppID'),
  ('miniapp.secret',        NULL,    'miniapp',     '小程序 AppSecret（敏感，列表脱敏显示）'),
  ('ip.access.enabled',     '0',     'ip_access',   '是否限制后台访问 IP：0 关闭 / 1 开启'),
  ('ip.access.mode',        'allow', 'ip_access',   '限制模式：allow 仅允许列表 / deny 仅拒绝列表'),
  ('ip.access.list',        NULL,    'ip_access',   'IP 或 CIDR 列表，逗号或空格分隔，最多 32 条');

-- Sidebar entries under the existing 会员运营 group; the frontend routes are static, the menu name
-- is what grants visibility to role 5.
SET @p0_parent = (SELECT id FROM ums_menu WHERE name='p0' ORDER BY id LIMIT 1);

INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @p0_parent,NOW(),x.title,1,x.sort,x.name,'ums-admin',0 FROM (
  SELECT '平台设置' AS title,11 AS sort,'p0SystemSettings' AS name UNION ALL
  SELECT '图片管理',12,'p0Images'
) AS x
WHERE @p0_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu m WHERE m.name=x.name);

INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m
WHERE m.name IN ('p0SystemSettings','p0Images')
AND EXISTS(SELECT 1 FROM ums_role WHERE id=5)
AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
