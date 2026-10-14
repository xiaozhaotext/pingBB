-- 服务中心、后台桌面恢复同级；会员列表的四类功能在详情页呈现。
-- 只调整菜单元数据，不删除既有菜单和角色授权；重复执行安全。
START TRANSACTION;

UPDATE ums_menu
SET parent_id = 0, level = 0, sort = 94, hidden = 0, title = '后台桌面'
WHERE id = 177 AND name = 'serviceDesktop';

UPDATE ums_menu
SET sort = 93
WHERE id = 156 AND name = 'systemSettings';

UPDATE ums_menu
SET title = '会员操作', parent_id = 103, level = 1, hidden = 0
WHERE id = 124 AND name = 'memberOperations';

UPDATE ums_menu
SET parent_id = 120, level = 2, hidden = 0
WHERE id = 119 AND name = 'memberMgrList';

UPDATE ums_menu
SET parent_id = 124, level = 2, hidden = 0
WHERE (id = 125 AND name = 'memberStats')
   OR (id = 126 AND name = 'memberRegisterNotice');

UPDATE ums_menu
SET hidden = 1
WHERE id IN (121, 185) AND name IN ('memberIdentity', 'memberOperationsPage');

COMMIT;
