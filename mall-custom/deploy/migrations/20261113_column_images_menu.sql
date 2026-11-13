START TRANSACTION;
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'栏目图片',0,955,'columnImagesMgr','picture',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='columnImagesMgr');
SET @column_parent=(SELECT id FROM ums_menu WHERE name='columnImagesMgr' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @column_parent,NOW(),'栏目图片',1,0,'columnImages','picture',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='columnImages');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m
WHERE m.name IN ('columnImagesMgr','columnImages')
AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
COMMIT;
