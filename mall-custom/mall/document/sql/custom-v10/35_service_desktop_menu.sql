-- Put the desktop dashboard under Service Center and grant its routes to the admin role.
START TRANSACTION;

INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (177,105,NOW(),'后台桌面',1,12,'serviceDesktop','dashboard',0),
 (178,177,NOW(),'大数据界面',2,12,'serviceDesktopData','dashboard',0),
 (179,177,NOW(),'清除缓存',2,11,'desktopClearCache','refresh',0),
 (180,177,NOW(),'用户',2,10,'serviceDesktopUser','user',0),
 (181,180,NOW(),'系统首页',3,10,'serviceDesktopHome','dashboard',0),
 (182,180,NOW(),'修改密码',3,9,'serviceDesktopPassword','password',0),
 (183,180,NOW(),'系统锁屏',3,8,'desktopLock','lock',0),
 (184,180,NOW(),'退出系统',3,7,'desktopLogout','logout',0)
ON DUPLICATE KEY UPDATE
 parent_id=VALUES(parent_id),title=VALUES(title),level=VALUES(level),sort=VALUES(sort),name=VALUES(name),icon=VALUES(icon),hidden=VALUES(hidden);

INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT ar.role_id,m.id
FROM ums_admin a
JOIN ums_admin_role_relation ar ON ar.admin_id=a.id
JOIN ums_menu m ON m.id BETWEEN 177 AND 184
LEFT JOIN ums_role_menu_relation old ON old.role_id=ar.role_id AND old.menu_id=m.id
WHERE a.username='admin' AND old.menu_id IS NULL;

COMMIT;
