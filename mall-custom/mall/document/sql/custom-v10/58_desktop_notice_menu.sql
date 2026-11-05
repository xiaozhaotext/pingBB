-- Move the existing notice menu and preserve access for its current roles.
START TRANSACTION;
UPDATE ums_menu notice
JOIN ums_menu desktop ON desktop.name='serviceDesktop'
SET notice.parent_id=desktop.id, notice.level=1, notice.hidden=0, notice.sort=99
WHERE notice.name='memberMgrNotices';
UPDATE ums_menu SET sort=100 WHERE name='serviceDesktopData';
UPDATE ums_menu SET sort=97 WHERE name='desktopClearCache';
UPDATE ums_menu SET hidden=1 WHERE name='serviceDesktopUser';
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT relation.role_id,desktop.id
FROM ums_role_menu_relation relation
JOIN ums_menu notice ON notice.id=relation.menu_id AND notice.name='memberMgrNotices'
JOIN ums_menu desktop ON desktop.name='serviceDesktop'
LEFT JOIN ums_role_menu_relation existing ON existing.role_id=relation.role_id AND existing.menu_id=desktop.id
WHERE existing.menu_id IS NULL;
COMMIT;
