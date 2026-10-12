-- Additive menu only. Existing PICK/REBUY table and financial rules remain unchanged.
SET NAMES utf8mb4;
SET @parent_menu=(SELECT id FROM ums_menu WHERE name='pms' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @parent_menu,NOW(),'专区商品管理',1,1,'productZones','product-list',0
WHERE @parent_menu IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='productZones');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,id FROM ums_menu m WHERE name='productZones'
AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
