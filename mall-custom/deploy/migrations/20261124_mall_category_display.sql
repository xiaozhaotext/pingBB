-- Menu/resource metadata only. Source categories, zone relations and products are never rewritten.
START TRANSACTION;
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'商品管理',0,900,'pms','product',0 WHERE NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='pms');
SET @mall_parent=(SELECT id FROM ums_menu WHERE name='pms' ORDER BY id LIMIT 1);
UPDATE ums_menu SET title='商品管理' WHERE id=@mall_parent;
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @mall_parent,NOW(),'商城分类筛选',1,100,'mallCategoryDisplay','product-cate',0
WHERE NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='mallCategoryDisplay');
SET @mall_child=(SELECT id FROM ums_menu WHERE name='mallCategoryDisplay' ORDER BY id LIMIT 1);
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT r.role_id,@mall_child FROM ums_role_menu_relation r WHERE r.menu_id=@mall_parent
AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=r.role_id AND x.menu_id=@mall_child);
INSERT INTO ums_resource(category_id,create_time,name,url,description)
SELECT COALESCE((SELECT category_id FROM (SELECT category_id FROM ums_resource WHERE url='/productCategory/**' LIMIT 1) c),1),NOW(),
'商城分类筛选管理','/p0/mall-category-display','只调整全商城分类展示，不写专区分类'
WHERE NOT EXISTS(SELECT 1 FROM ums_resource WHERE url='/p0/mall-category-display');
INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT DISTINCT r.role_id,res.id FROM ums_role_menu_relation r JOIN ums_resource res ON res.url='/p0/mall-category-display'
WHERE r.menu_id=@mall_child AND NOT EXISTS(SELECT 1 FROM ums_role_resource_relation x WHERE x.role_id=r.role_id AND x.resource_id=res.id);
COMMIT;
