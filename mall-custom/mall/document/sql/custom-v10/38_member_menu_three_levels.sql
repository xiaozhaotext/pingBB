-- 会员 -> 会员管理 -> 会员列表及四个功能入口。
-- 合伙人、联创等运营入口继续展示，避免现有功能从菜单消失。
START TRANSACTION;

INSERT IGNORE INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden)
VALUES (185,120,NOW(),'会员操作',2,9,'memberOperationsPage','form',0);

UPDATE ums_menu SET parent_id=120,level=2,sort=8,hidden=0
WHERE id=125 AND name='memberStats';
UPDATE ums_menu SET parent_id=120,level=2,sort=7,hidden=0
WHERE id=126 AND name='memberRegisterNotice';
UPDATE ums_menu SET title='会员运营',hidden=0 WHERE id=124 AND name='memberOperations';

INSERT IGNORE INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT relation.role_id,185
FROM ums_admin admin
JOIN ums_admin_role_relation relation ON relation.admin_id=admin.id
WHERE admin.username='admin';

COMMIT;
