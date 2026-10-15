-- 后台总控导航重构（8 个一级功能分组，严格分级：一级 → 二级 → 三级）
-- 幂等：先清理 100~299 区间的历史菜单与角色关联，再重建；最后隐藏旧的一级分组。
-- 本文件取代 32_admin_menu_ia.sql（32 为 6 分组过渡版本）。
USE `mall`;

DELETE FROM ums_role_menu_relation WHERE menu_id BETWEEN 100 AND 299;
DELETE FROM ums_menu WHERE id BETWEEN 100 AND 299;

-- 一级分组（level 0）
INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (200,0,NOW(),'桌面主页',0,108,'desk','dashboard',0),
 (201,0,NOW(),'自选商品管理',0,107,'pick','product',0),
 (202,0,NOW(),'复购商品管理',0,106,'rebuy','product-list',0),
 (203,0,NOW(),'订单管理',0,105,'orderMgr','order',0),
 (204,0,NOW(),'会员',0,104,'memberMgr','ums-admin',0),
 (205,0,NOW(),'财务中心',0,103,'financeMgr','total-today',0),
 (206,0,NOW(),'服务中心',0,102,'serviceMgr','ums',0),
 (207,0,NOW(),'系统设置',0,101,'sysMgr','ums',0);

-- 二级菜单（level 1）
INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (210,200,NOW(),'大数据界面',1,10,'deskDashboard','dashboard',0),
 (211,200,NOW(),'清除缓存',1,9,'deskClearCache','document',0),
 (212,200,NOW(),'用户',1,8,'deskUser','user',0),
 (213,201,NOW(),'分类管理',1,10,'pickCategory','product-cate',0),
 (214,201,NOW(),'商品列表',1,9,'pickProduct','product-list',0),
 (215,202,NOW(),'分类管理',1,10,'rebuyCategory','product-cate',0),
 (216,202,NOW(),'商品列表',1,9,'rebuyProduct','product-list',0),
 (217,203,NOW(),'订单列表',1,10,'orderMgrList','product-list',0),
 (218,203,NOW(),'订单销量汇总',1,9,'orderSummary','total-today',0),
 (219,203,NOW(),'订单详情',1,8,'orderDetailView','order-detail',0),
 (220,204,NOW(),'会员管理',1,10,'memberManage','user',0),
 (221,204,NOW(),'会员操作',1,9,'memberOperation','form',0),
 (222,205,NOW(),'资金分析',1,10,'financeAnalysis','total-today',0),
 (223,205,NOW(),'资金管理',1,9,'financeFunds','total-today',0),
 (224,205,NOW(),'产品积分明细',1,8,'financeProductPoints','product-comment',0),
 (225,205,NOW(),'复购积分明细',1,7,'financeRepurchasePoints','product-comment',0),
 (226,205,NOW(),'钱包明细管理',1,6,'financeWallet','total-week',0),
 (227,205,NOW(),'每日贡献奖励池',1,5,'financeDividend','total-yesterday',0),
 (228,206,NOW(),'账号与安全',1,10,'serviceAccount','password',0),
 (229,206,NOW(),'隐私政策',1,9,'servicePrivacy','form',0),
 (230,207,NOW(),'管理首页',1,10,'sysHome','home',0),
 (231,207,NOW(),'管理员设置',1,9,'sysAdmin','ums-admin',0),
 (232,207,NOW(),'数据库管理',1,8,'sysDatabase','table',0),
 (233,207,NOW(),'扩展功能',1,7,'sysExtra','form',0);

-- 三级菜单（level 2）
INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (240,212,NOW(),'系统首页',2,10,'deskUserHome','home',0),
 (241,212,NOW(),'修改密码',2,9,'deskUserPassword','password',0),
 (242,212,NOW(),'系统锁屏',2,8,'deskUserLock','password',0),
 (243,212,NOW(),'退出系统',2,7,'deskUserLogout','password',0),
 (244,220,NOW(),'会员列表',2,10,'memberMgrList','user',0),
 (245,220,NOW(),'会员身份',2,9,'memberIdentity','ums-role',0),
 (246,221,NOW(),'注册须知',2,10,'memberRegisterNotice','form',0),
 (247,221,NOW(),'会员统计',2,9,'memberStats','total-today',0),
 (248,230,NOW(),'系统配置',2,10,'sysConfig','ums',0),
 (249,230,NOW(),'地址库管理',2,9,'sysAreaLibrary','marker',0),
 (250,230,NOW(),'发送记录',2,8,'sysMessageRecords','form',0),
 (251,230,NOW(),'访问统计',2,7,'sysVisitStatistics','dashboard',0),
 (252,230,NOW(),'日志管理',2,6,'sysLogManager','document',0),
 (253,230,NOW(),'图片管理',2,5,'sysImages','eye',0),
 (254,231,NOW(),'部门管理',2,10,'sysDepartment','ums',0),
 (255,231,NOW(),'管理用户',2,9,'sysAdminUsers','ums-admin',0),
 (256,231,NOW(),'角色管理',2,8,'sysRoles','ums-role',0),
 (257,231,NOW(),'菜单管理',2,7,'sysMenus','ums-menu',0),
 (258,231,NOW(),'登陆日志',2,6,'sysLoginLog','document',0),
 (259,232,NOW(),'数据库备份',2,10,'sysDatabaseBackup','table',0),
 (260,232,NOW(),'查看备份',2,9,'sysDatabaseBackupList','table',0),
 (261,233,NOW(),'公告与咨询',2,10,'sysNotices','form',0),
 (262,233,NOW(),'品牌管理',2,9,'sysBrand','product-brand',0),
 (263,233,NOW(),'商品属性',2,8,'sysProductAttr','product-attr',0),
 (264,233,NOW(),'优惠券',2,7,'sysCoupon','sms-coupon',0),
 (265,233,NOW(),'秒杀活动',2,6,'sysFlash','sms-flash',0),
 (266,233,NOW(),'广告列表',2,5,'sysAdvertise','sms-ad',0),
 (267,233,NOW(),'推荐位管理',2,4,'sysRecommend','sms-new',0);

-- 让原本拥有后台总控菜单的角色同时拥有新的 8 分组菜单
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT r.role_id, m.id
FROM (SELECT DISTINCT role_id FROM ums_role_menu_relation WHERE menu_id IN (1,7,12,21,26)) r
CROSS JOIN ums_menu m
WHERE m.id BETWEEN 200 AND 299;

-- 旧的一级分组不再出现在侧边栏（路由保留，仅隐藏菜单）
UPDATE ums_menu SET hidden = 1 WHERE name IN ('pms','oms','sms','p0','ums');