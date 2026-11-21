START TRANSACTION;
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'小程序管理',0,954,'miniappMgr','miniapp',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='miniappMgr');
SET @miniapp_parent=(SELECT id FROM ums_menu WHERE name='miniappMgr' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @miniapp_parent,NOW(),'首页文字',1,2,'miniappHome','form',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='miniappHome');
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @miniapp_parent,NOW(),'栏目图片',1,1,'miniappImages','miniapp',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='miniappImages');
UPDATE ums_menu SET icon='miniapp' WHERE name IN ('miniappMgr','miniappImages') AND icon<>'miniapp';
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @miniapp_parent,NOW(),'板块显示',1,3,'miniappSections','product-list',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='miniappSections');
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @miniapp_parent,NOW(),'首页跳转',1,4,'miniappLinks','form',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='miniappLinks');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m
WHERE m.name IN ('miniappMgr','miniappHome','miniappImages','miniappSections','miniappLinks')
AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
COMMIT;

