-- 恢复 2026-10-14 16:28 版导航：后台桌面使用前端固定一级入口。
-- 旧服务中心桌面菜单不再展示，保留记录和已有权限关联。
UPDATE ums_menu
SET hidden = 1
WHERE id BETWEEN 177 AND 184
  AND name IN (
    'serviceDesktop', 'serviceDesktopData', 'desktopClearCache',
    'serviceDesktopUser', 'serviceDesktopHome', 'serviceDesktopPassword',
    'desktopLock', 'desktopLogout'
  );
