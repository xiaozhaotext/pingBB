-- Additive only. No fabricated company details or policy text.
CREATE TABLE IF NOT EXISTS sys_site_profile (
 id BIGINT NOT NULL PRIMARY KEY,
 company_name VARCHAR(128) NOT NULL DEFAULT '',
 address VARCHAR(500) NOT NULL DEFAULT '',
 telephone VARCHAR(64) NOT NULL DEFAULT '',
 mobile VARCHAR(32) NOT NULL DEFAULT '',
 email VARCHAR(128) NOT NULL DEFAULT '',
 qq VARCHAR(32) NOT NULL DEFAULT '',
 wechat_qr_url VARCHAR(1000) NOT NULL DEFAULT '',
 company_revision BIGINT NOT NULL DEFAULT 0,
 privacy_title VARCHAR(128) NOT NULL DEFAULT '',
 privacy_content MEDIUMTEXT NOT NULL,
 privacy_revision BIGINT NOT NULL DEFAULT 0,
 published_title VARCHAR(128) NOT NULL DEFAULT '',
 published_content MEDIUMTEXT NOT NULL,
 published_revision BIGINT NULL,
 published_at DATETIME NULL,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO sys_site_profile(id,privacy_content,published_content) VALUES(1,'','');
-- Preserve an already published policy from the existing settings module as the baseline.
UPDATE sys_site_profile p JOIN sys_content c ON c.content_key='privacy_policy'
SET p.privacy_title='隐私政策',p.privacy_content=c.content_value,
 p.published_title='隐私政策',p.published_content=c.content_value,
 p.published_revision=0,p.published_at=c.update_time
WHERE p.id=1 AND p.published_revision IS NULL AND p.privacy_revision=0
AND c.content_value IS NOT NULL AND TRIM(c.content_value)<>'';
SET @parent=(SELECT id FROM ums_menu WHERE name='p0' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @parent,NOW(),'公司信息与隐私政策',1,10,'p0SiteProfile','ums-admin',0
WHERE @parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='p0SiteProfile');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m WHERE m.name='p0SiteProfile'
AND EXISTS(SELECT 1 FROM ums_role WHERE id=5)
AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
