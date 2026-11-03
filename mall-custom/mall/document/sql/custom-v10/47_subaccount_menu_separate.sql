-- Preserve the existing platform administrator page and add a separate subaccount entry.
START TRANSACTION;
UPDATE ums_menu SET title='管理用户' WHERE name='serviceAdmin';
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT p.id,NOW(),'子账号管理',2,99,'serviceSubaccounts','ums-admin',0
FROM ums_menu p WHERE p.name='systemAdminSettings'
  AND NOT EXISTS (SELECT 1 FROM ums_menu m WHERE m.name='serviceSubaccounts')
LIMIT 1;
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m WHERE m.name='serviceSubaccounts'
  AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
COMMIT;
