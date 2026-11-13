START TRANSACTION;
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'赠品商品',0,956,'giftOptionsMgr','product',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='giftOptionsMgr');
SET @gift_parent=(SELECT id FROM ums_menu WHERE name='giftOptionsMgr' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @gift_parent,NOW(),'赠品商品',1,0,'giftOptions','product',0
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='giftOptions');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m
WHERE m.name IN ('giftOptionsMgr','giftOptions')
AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
COMMIT;
