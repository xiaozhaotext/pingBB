-- Only the explicitly designated platform role (5) receives new permissions.
INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),'运营统计','/dashboard/**','数据库实时统计',NULL
WHERE NOT EXISTS(SELECT 1 FROM ums_resource WHERE url='/dashboard/**');
INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT 5,r.id FROM ums_resource r WHERE r.url='/dashboard/**'
AND EXISTS(SELECT 1 FROM ums_role WHERE id=5)
AND NOT EXISTS(SELECT 1 FROM ums_role_resource_relation x WHERE x.role_id=5 AND x.resource_id=r.id);
SET @p0_parent=(SELECT id FROM ums_menu WHERE name='p0' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @p0_parent,NOW(),x.title,1,x.sort,x.name,'ums-admin',0 FROM (
 SELECT '公告管理' title,8 sort,'p0Notices' name UNION ALL
 SELECT '在线咨询',9,'p0Consultations'
) x WHERE @p0_parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu m WHERE m.name=x.name);
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m WHERE m.name IN ('p0Notices','p0Consultations')
AND EXISTS(SELECT 1 FROM ums_role WHERE id=5)
AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
