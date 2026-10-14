-- 服务中心 -> 后台桌面按思维导图恢复三级菜单。
-- 旧菜单记录保持不变，仅恢复显示标志；重复执行安全。
UPDATE ums_menu
SET hidden = 0
WHERE id BETWEEN 177 AND 184
  AND name IN (
    'serviceDesktop', 'serviceDesktopData', 'desktopClearCache',
    'serviceDesktopUser', 'serviceDesktopHome', 'serviceDesktopPassword',
    'desktopLock', 'desktopLogout'
  );
