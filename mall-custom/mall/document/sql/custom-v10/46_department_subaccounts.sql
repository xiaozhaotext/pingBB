-- Department-bound subaccounts use the existing ums_department and ums_admin.dept_id columns.
-- Only add the role editor entry for platform administrators. No business data is removed.
START TRANSACTION;
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT p.id,NOW(),'角色与板块',2,96,'serviceRoles','ums-role',0
FROM ums_menu p WHERE p.name='systemAdminSettings'
  AND NOT EXISTS (SELECT 1 FROM ums_menu m WHERE m.name='serviceRoles')
LIMIT 1;
UPDATE ums_menu SET title='子账号管理' WHERE name='serviceAdmin';
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m WHERE m.name='serviceRoles'
  AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
-- Department roles receive only the resources explicitly assigned in the role editor.
-- The legacy /p0/** resource remains for platform admins and is ignored for subaccounts.
INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),v.name,v.url,'部门板块接口权限',NULL FROM (
  SELECT '会员管理' AS name,'/p0/members/**' AS url UNION ALL
  SELECT '合伙人管理','/p0/partner/**' UNION ALL
  SELECT '联创管理','/p0/cofounder/**' UNION ALL
  SELECT '积分管理','/p0/points/**' UNION ALL
  SELECT '财务管理','/p0/finance/**' UNION ALL
  SELECT '分红池管理','/p0/dividend/**' UNION ALL
  SELECT '公告管理','/p0/notices/**' UNION ALL
  SELECT '咨询管理','/p0/consultations/**' UNION ALL
  SELECT '平台设置','/p0/settings/**' UNION ALL
  SELECT '登录日志','/p0/login-logs/**'
) v WHERE NOT EXISTS (SELECT 1 FROM ums_resource r WHERE r.url=v.url);
COMMIT;
