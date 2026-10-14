-- 思维导图后台菜单层级调整。保留既有菜单及业务数据，只更新菜单树并补齐新节点权限。
START TRANSACTION;

INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (100,0,NOW(),'自选商品管理',0,100,'pick','product',0),
 (101,0,NOW(),'复购商品管理',0,99,'rebuy','product-list',0),
 (102,0,NOW(),'订单管理',0,98,'orderMgr','order',0),
 (103,0,NOW(),'会员',0,97,'memberMgr','ums-admin',0),
 (104,0,NOW(),'财务中心',0,96,'financeMgr','total-today',0),
 (105,0,NOW(),'服务中心',0,95,'serviceMgr','ums',0),
 (164,104,NOW(),'积分明细',1,8,'financePoints','product-comment',0),
 (110,100,NOW(),'商品列表',1,9,'pickProduct','product-list',0),
 (111,100,NOW(),'分类管理',1,10,'pickCategory','product-cate',0),
 (112,101,NOW(),'商品列表',1,9,'rebuyProduct','product-list',0),
 (113,101,NOW(),'分类管理',1,10,'rebuyCategory','product-cate',0),
 (114,102,NOW(),'筛选',1,10,'orderMgrFilter','product-list',0),
 (115,102,NOW(),'订单销量汇总',1,9,'orderSummary','total-today',0),
 (116,102,NOW(),'订单详情',1,8,'orderMgrDetails','document',0),
 (117,102,NOW(),'退货申请处理',1,7,'orderMgrReturnApply','order-return',1),
 (118,102,NOW(),'退货原因设置',1,6,'orderMgrReturnReason','order-return-reason',1),
 (120,103,NOW(),'会员管理',1,9,'memberManagement','ums-role',0),
 (119,120,NOW(),'会员列表',2,11,'memberMgrList','user',0),
 (121,120,NOW(),'会员身份',2,10,'memberIdentity','ums-role',0),
 (124,103,NOW(),'会员操作',1,8,'memberOperations','form',0),
 (122,124,NOW(),'合伙人管理',2,12,'memberMgrPartner','ums-role',1),
 (123,124,NOW(),'联创管理',2,11,'memberMgrCofounder','ums-role',1),
 (125,124,NOW(),'会员统计',2,10,'memberStats','total-today',0),
 (126,124,NOW(),'注册须知',2,9,'memberRegisterNotice','form',0),
 (127,124,NOW(),'公告管理',2,8,'memberMgrNotices','form',1),
 (128,104,NOW(),'资金分析',1,10,'financeAnalysis','total-today',0),
 (129,104,NOW(),'资金管理',1,9,'financeFunds','total-today',0),
 (130,164,NOW(),'产品积分明细',2,10,'financeProductPoints','product-comment',0),
 (131,164,NOW(),'复购积分明细',2,9,'financeRepurchasePoints','product-comment',0),
 (132,104,NOW(),'钱包明细管理',1,6,'financeWalletManagement','total-week',0),
 (133,104,NOW(),'每日贡献奖励池',1,5,'financeDividend','total-yesterday',0),
 (134,105,NOW(),'账号与安全',1,10,'serviceAccount','password',0),
 (135,105,NOW(),'隐私政策',1,9,'servicePrivacy','form',0),
 (156,0,NOW(),'系统设置',0,94,'systemSettings','ums',0),
 (157,156,NOW(),'管理首页',1,10,'systemManagementHome','dashboard',0),
 (138,156,NOW(),'数据库管理',1,8,'systemDatabaseManagement','table',0),
 (141,156,NOW(),'管理员设置',1,7,'systemAdminSettings','ums-admin',0),
 (136,157,NOW(),'系统配置',2,10,'systemConfig','ums',0),
 (137,157,NOW(),'图片管理',2,9,'systemImages','eye',0),
 (139,157,NOW(),'地址库管理',2,8,'systemAddresses','marker',0),
 (140,157,NOW(),'访问统计',2,7,'systemVisits','dashboard',0),
 (158,141,NOW(),'管理用户',2,11,'serviceAdmin','ums-admin',0),
 (142,141,NOW(),'角色管理',2,10,'serviceRole','ums-role',1),
 (143,141,NOW(),'菜单管理',2,9,'serviceMenu','ums-menu',1),
 (144,141,NOW(),'资源管理',2,8,'serviceResource','ums-resource',1),
 (145,141,NOW(),'部门管理',2,7,'serviceDepartment','ums',0),
 (146,141,NOW(),'登录日志',2,6,'serviceLoginLog','document',0),
 (147,157,NOW(),'品牌管理',2,6,'serviceBrand','product-brand',1),
 (148,157,NOW(),'商品属性',2,5,'serviceProductAttr','product-attr',1),
 (149,157,NOW(),'优惠券',2,4,'serviceCoupon','sms-coupon',1),
 (150,157,NOW(),'秒杀活动',2,3,'serviceFlash','sms-flash',1),
 (151,157,NOW(),'广告列表',2,2,'serviceAdvertise','sms-ad',1),
 (152,157,NOW(),'品牌推荐',2,1,'serviceHomeBrand','product-brand',1),
 (153,157,NOW(),'新品推荐',2,0,'serviceHomeNew','sms-new',1),
 (154,157,NOW(),'人气推荐',2,-1,'serviceHomeHot','sms-hot',1),
 (155,157,NOW(),'专题推荐',2,-2,'serviceHomeSubject','sms-subject',1),
 (160,157,NOW(),'发送记录',2,5,'systemMessages','form',0),
 (162,157,NOW(),'日志管理',2,3,'systemLogs','document',0),
 (161,138,NOW(),'数据库备份',2,10,'serviceDatabase','table',0),
 (163,138,NOW(),'查看备份',2,9,'systemDatabaseBackups','document',0),
 (159,136,NOW(),'公司信息',3,10,'systemSiteProfile','form',0),
 (165,136,NOW(),'系统参数',3,9,'systemParameters','form',0),
 (166,136,NOW(),'水印设置',3,8,'systemWatermark','form',0),
 (167,136,NOW(),'上传设置',3,7,'systemUpload','form',0),
 (168,136,NOW(),'后台目录',3,6,'systemBackendDirectory','form',0),
 (169,136,NOW(),'IP 过滤设置',3,5,'systemIpFilter','form',0),
 (170,136,NOW(),'短信设置',3,4,'systemSms','form',0),
 (171,136,NOW(),'微信设置',3,3,'systemWechat','form',0),
 (173,132,NOW(),'佣金提现审核',2,10,'financeWallet','total-week',0),
 (174,132,NOW(),'佣金账户',2,9,'financeCommissionAccounts','total-week',0),
 (175,132,NOW(),'佣金明细',2,8,'financeCommissionLedger','document',0),
 (176,132,NOW(),'佣金来源',2,7,'financeCommissionSources','document',1)
ON DUPLICATE KEY UPDATE
 parent_id=VALUES(parent_id),title=VALUES(title),level=VALUES(level),sort=VALUES(sort),name=VALUES(name),icon=VALUES(icon),hidden=VALUES(hidden);

INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT ar.role_id,m.id
FROM ums_admin a
JOIN ums_admin_role_relation ar ON ar.admin_id=a.id
JOIN ums_menu m ON m.id BETWEEN 156 AND 176
LEFT JOIN ums_role_menu_relation old ON old.role_id=ar.role_id AND old.menu_id=m.id
WHERE a.username='admin' AND old.menu_id IS NULL;

UPDATE ums_menu SET hidden=1 WHERE name IN ('pms','oms','sms','p0','ums');
COMMIT;
