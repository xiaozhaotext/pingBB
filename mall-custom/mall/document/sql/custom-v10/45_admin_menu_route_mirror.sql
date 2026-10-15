-- 45_admin_menu_route_mirror.sql
--
-- 背景：后台侧边栏由前端路由表过滤生成，权限过滤按「路由 name == ums_menu.name」匹配，
-- 匹配不到且非 hidden 的路由会被整棵丢弃。历史迁移（33/34/35/41）在不同环境里给同一批页面用了
-- 不同的菜单 name（desk/sysMgr 等），导致本地后台缺少 后台桌面、系统设置、订单筛选、会员管理、
-- 财务钱包 等一二级菜单，与线上不一致。
--
-- 本脚本把 ums_menu 与前端路由树（mall-admin-web/src/router/index.ts 的 asyncRouterMap）按 name 对齐：
--   * 结构 / 标题 / 图标 / hidden 以当前前端路由为准；
--   * 已上线菜单的排序与隐藏以线上 /admin-api/admin/info 为准，保证与线上顺序一致；
--   * 9 个一级分组的顺序：自选商品管理 > 复购商品管理 > 消费区商品管理 > 订单管理 > 会员 > 财务中心 > 服务中心 > 后台桌面 > 系统设置。
--
-- 幂等：按 name 只补缺失行、只归一已有行元数据、只补缺失授权，不删除菜单、角色授权或业务数据。
-- 本文件由 tools/gen_admin_menu_mirror.mjs 生成，路由调整后请重新生成，不要手工改。
-- 执行：mysql --default-character-set=utf8mb4 -u<user> -p<pass> <database> < 45_admin_menu_route_mirror.sql

START TRANSACTION;

CREATE TEMPORARY TABLE tmp_admin_menu_mirror (
  name   VARCHAR(100) NOT NULL,
  parent VARCHAR(100) NULL,
  title  VARCHAR(100) NOT NULL,
  icon   VARCHAR(200) NULL,
  level  INT NOT NULL,
  sort   INT NOT NULL,
  hidden TINYINT NOT NULL,
  PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO tmp_admin_menu_mirror(name, parent, title, icon, level, sort, hidden) VALUES
  ('pick', NULL, '自选商品管理', 'product', 0, 1000, 0),
  ('rebuy', NULL, '复购商品管理', 'product-list', 0, 990, 0),
  ('consume', NULL, '消费区商品管理', 'product-list', 0, 980, 0),
  ('orderMgr', NULL, '订单管理', 'order', 0, 970, 0),
  ('memberMgr', NULL, '会员', 'ums-admin', 0, 960, 0),
  ('financeMgr', NULL, '财务中心', 'total-today', 0, 950, 0),
  ('serviceMgr', NULL, '服务中心', 'ums', 0, 940, 0),
  ('serviceDesktop', NULL, '后台桌面', 'dashboard', 0, 930, 0),
  ('systemSettings', NULL, '系统设置', 'ums', 0, 920, 0),
  ('consumeCategory', 'consume', '分类管理', 'product-cate', 1, 100, 0),
  ('financeAnalysis', 'financeMgr', '资金分析', 'total-today', 1, 100, 0),
  ('memberManagement', 'memberMgr', '会员管理', 'ums-role', 1, 100, 0),
  ('orderMgrFilter', 'orderMgr', '筛选', 'product-list', 1, 100, 0),
  ('pickCategory', 'pick', '分类管理', 'product-cate', 1, 100, 0),
  ('rebuyCategory', 'rebuy', '分类管理', 'product-cate', 1, 100, 0),
  ('serviceAccount', 'serviceMgr', '账号与安全', 'password', 1, 100, 0),
  ('serviceDesktopData', 'serviceDesktop', '大数据界面', 'dashboard', 1, 100, 0),
  ('systemManagementHome', 'systemSettings', '管理首页', 'dashboard', 1, 100, 0),
  ('consumeProduct', 'consume', '商品列表', 'product-list', 1, 97, 0),
  ('desktopClearCache', 'serviceDesktop', '清除缓存', 'refresh', 1, 97, 0),
  ('financeFunds', 'financeMgr', '资金管理', 'total-today', 1, 97, 0),
  ('memberOperations', 'memberMgr', '会员操作', 'form', 1, 97, 0),
  ('orderSummary', 'orderMgr', '订单销量汇总', 'total-today', 1, 97, 0),
  ('pickProduct', 'pick', '商品列表', 'product-list', 1, 97, 0),
  ('rebuyProduct', 'rebuy', '商品列表', 'product-list', 1, 97, 0),
  ('servicePrivacy', 'serviceMgr', '隐私政策', 'form', 1, 97, 0),
  ('systemDatabaseManagement', 'systemSettings', '数据库管理', 'table', 1, 97, 0),
  ('consumeAddCategory', 'consume', '添加商品分类', NULL, 1, 94, 1),
  ('financePoints', 'financeMgr', '积分明细', 'product-comment', 1, 94, 0),
  ('orderMgrDetails', 'orderMgr', '订单详情', 'document', 1, 94, 0),
  ('pickAddCategory', 'pick', '添加商品分类', NULL, 1, 94, 1),
  ('rebuyAddCategory', 'rebuy', '添加商品分类', NULL, 1, 94, 1),
  ('serviceDesktopUser', 'serviceDesktop', '用户', 'user', 1, 94, 0),
  ('systemAdminSettings', 'systemSettings', '管理员设置', 'ums-admin', 1, 94, 0),
  ('consumeUpdateCategory', 'consume', '编辑商品分类', NULL, 1, 91, 1),
  ('financeWalletManagement', 'financeMgr', '钱包明细管理', 'total-week', 1, 91, 0),
  ('orderMgrLegacyList', 'orderMgr', '订单列表（旧入口）', NULL, 1, 91, 1),
  ('pickUpdateCategory', 'pick', '编辑商品分类', NULL, 1, 91, 1),
  ('rebuyUpdateCategory', 'rebuy', '编辑商品分类', NULL, 1, 91, 1),
  ('consumeAddProduct', 'consume', '添加商品', NULL, 1, 88, 1),
  ('financeDividend', 'financeMgr', '每日贡献奖励池', 'total-yesterday', 1, 88, 0),
  ('orderMgrOrderDetail', 'orderMgr', '订单详情', 'document', 1, 88, 1),
  ('pickAddProduct', 'pick', '添加商品', NULL, 1, 88, 1),
  ('rebuyAddProduct', 'rebuy', '添加商品', NULL, 1, 88, 1),
  ('consumeUpdateProduct', 'consume', '编辑商品', NULL, 1, 85, 1),
  ('orderMgrReturnApplyDetail', 'orderMgr', '售后详情', NULL, 1, 85, 1),
  ('pickUpdateProduct', 'pick', '编辑商品', NULL, 1, 85, 1),
  ('rebuyUpdateProduct', 'rebuy', '编辑商品', NULL, 1, 85, 1),
  ('orderMgrDeliverOrderList', 'orderMgr', '订单发货', NULL, 1, 82, 1),
  ('financeProductPoints', 'financePoints', '产品积分明细', 'product-comment', 2, 100, 0),
  ('financeWallet', 'financeWalletManagement', '佣金提现审核', 'total-week', 2, 100, 0),
  ('memberMgrList', 'memberManagement', '会员列表', 'user', 2, 100, 0),
  ('memberMgrPartner', 'memberOperations', '合伙人管理', 'ums-role', 2, 100, 1),
  ('serviceAdmin', 'systemAdminSettings', '管理用户', 'ums-admin', 2, 100, 0),
  ('serviceDatabase', 'systemDatabaseManagement', '数据库备份', 'table', 2, 100, 0),
  ('serviceDesktopHome', 'serviceDesktopUser', '系统首页', 'dashboard', 2, 100, 0),
  ('systemConfig', 'systemManagementHome', '系统配置', 'ums', 2, 100, 0),
  ('financeCommissionAccounts', 'financeWalletManagement', '佣金账户', 'total-week', 2, 97, 0),
  ('financeRepurchasePoints', 'financePoints', '复购积分明细', 'product-comment', 2, 97, 0),
  ('memberIdentity', 'memberManagement', '会员身份', 'ums-role', 2, 97, 1),
  ('memberMgrCofounder', 'memberOperations', '联创管理', 'ums-role', 2, 97, 1),
  ('serviceDepartment', 'systemAdminSettings', '部门管理', 'ums', 2, 97, 0),
  ('serviceDesktopPassword', 'serviceDesktopUser', '修改密码', 'password', 2, 97, 0),
  ('systemDatabaseBackups', 'systemDatabaseManagement', '查看备份', 'document', 2, 97, 0),
  ('systemImages', 'systemManagementHome', '图片管理', 'eye', 2, 97, 0),
  ('desktopLock', 'serviceDesktopUser', '系统锁屏', 'lock', 2, 94, 0),
  ('financeCommissionLedger', 'financeWalletManagement', '佣金明细', 'document', 2, 94, 0),
  ('memberStats', 'memberOperations', '会员统计', 'total-today', 2, 94, 0),
  ('serviceLoginLog', 'systemAdminSettings', '登录日志', 'document', 2, 94, 0),
  ('systemAddresses', 'systemManagementHome', '地址库管理', 'marker', 2, 94, 0),
  ('desktopLogout', 'serviceDesktopUser', '退出系统', 'logout', 2, 91, 0),
  ('financeCommissionSources', 'financeWalletManagement', '佣金来源', 'document', 2, 91, 1),
  ('memberMgrNotices', 'memberOperations', '公告管理', 'form', 2, 91, 1),
  ('systemVisits', 'systemManagementHome', '访问统计', 'dashboard', 2, 91, 0),
  ('memberRegisterNotice', 'memberOperations', '注册须知', 'form', 2, 88, 0),
  ('serviceBrand', 'systemManagementHome', '品牌管理', 'product-brand', 2, 88, 1),
  ('memberMgrConsultations', 'memberOperations', '在线咨询', 'form', 2, 85, 0),
  ('systemMessages', 'systemManagementHome', '发送记录', 'form', 2, 85, 0),
  ('memberMgrMessageRecords', 'memberOperations', '发送记录', 'form', 2, 82, 0),
  ('serviceProductAttr', 'systemManagementHome', '商品属性', 'product-attr', 2, 82, 1),
  ('serviceCoupon', 'systemManagementHome', '优惠券', 'sms-coupon', 2, 79, 1),
  ('systemLogs', 'systemManagementHome', '日志管理', 'document', 2, 76, 0),
  ('serviceFlash', 'systemManagementHome', '秒杀活动', 'sms-flash', 2, 73, 1),
  ('serviceAdvertise', 'systemManagementHome', '广告列表', 'sms-ad', 2, 70, 1),
  ('serviceHomeBrand', 'systemManagementHome', '品牌推荐', 'product-brand', 2, 67, 1),
  ('serviceHomeNew', 'systemManagementHome', '新品推荐', 'sms-new', 2, 64, 1),
  ('serviceHomeHot', 'systemManagementHome', '人气推荐', 'sms-hot', 2, 61, 1),
  ('serviceHomeSubject', 'systemManagementHome', '专题推荐', 'sms-subject', 2, 58, 1),
  ('systemSiteProfile', 'systemConfig', '公司信息', 'form', 3, 100, 0),
  ('systemParameters', 'systemConfig', '系统参数', 'form', 3, 97, 0),
  ('systemWatermark', 'systemConfig', '水印设置', 'form', 3, 94, 0),
  ('systemUpload', 'systemConfig', '上传设置', 'form', 3, 91, 0),
  ('systemBackendDirectory', 'systemConfig', '后台目录', 'form', 3, 88, 0),
  ('systemIpFilter', 'systemConfig', 'IP 过滤设置', 'form', 3, 85, 0),
  ('systemSms', 'systemConfig', '短信设置', 'form', 3, 82, 0),
  ('systemWechat', 'systemConfig', '微信设置', 'form', 3, 79, 0);

-- 1) 补齐缺失菜单
INSERT INTO ums_menu(parent_id, create_time, title, level, sort, name, icon, hidden)
SELECT 0, NOW(), t.title, t.level, t.sort, t.name, t.icon, t.hidden
FROM tmp_admin_menu_mirror t
LEFT JOIN ums_menu m ON m.name = t.name
WHERE m.id IS NULL;

-- 2) 归一已有菜单的标题/图标/排序/隐藏/层级
UPDATE ums_menu m
JOIN tmp_admin_menu_mirror t ON t.name = m.name
SET m.title = t.title, m.icon = t.icon, m.sort = t.sort, m.hidden = t.hidden, m.level = t.level;

-- 3) 修正父子关系（一级菜单 parent_id = 0）
UPDATE ums_menu m
JOIN tmp_admin_menu_mirror t ON t.name = m.name
LEFT JOIN ums_menu p ON p.name = t.parent
SET m.parent_id = COALESCE(p.id, 0);

-- 4) 给已开通新控制台的角色补齐授权（与 34 号脚本一致：持有「自选商品管理」的角色获得全部新菜单）
INSERT INTO ums_role_menu_relation(role_id, menu_id)
SELECT DISTINCT r.role_id, m.id
FROM ums_role_menu_relation r
JOIN ums_menu root ON root.id = r.menu_id AND root.name = 'pick'
JOIN ums_menu m ON m.name IN (SELECT name FROM tmp_admin_menu_mirror)
LEFT JOIN ums_role_menu_relation old ON old.role_id = r.role_id AND old.menu_id = m.id
WHERE old.menu_id IS NULL;

-- 5) 隐藏被新控制台取代的旧菜单记录（保留记录与授权，仅不再展示；与规范菜单同名的旧记录不在此列）
UPDATE ums_menu SET hidden = 1 WHERE name IN ('desk', 'sysMgr', 'deskDashboard', 'deskClearCache', 'deskUser', 'deskUserHome', 'deskUserPassword', 'deskUserLock', 'deskUserLogout', 'sysHome', 'sysAdmin', 'sysDatabase', 'sysExtra', 'sysConfig', 'sysAreaLibrary', 'sysMessageRecords', 'sysVisitStatistics', 'sysLogManager', 'sysImages', 'sysDepartment', 'sysAdminUsers', 'sysRoles', 'sysMenus', 'sysLoginLog', 'sysDatabaseBackup', 'sysDatabaseBackupList', 'sysNotices', 'sysBrand', 'sysProductAttr', 'sysCoupon', 'sysFlash', 'sysAdvertise', 'sysRecommend', 'orderMgrList', 'orderDetailView', 'memberManage', 'memberOperation');

DROP TEMPORARY TABLE tmp_admin_menu_mirror;

COMMIT;
