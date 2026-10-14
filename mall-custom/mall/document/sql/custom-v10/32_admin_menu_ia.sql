-- 后台总控导航重构：6 个一级功能分组（对齐原图思维导图）
-- 幂等：先清理 100~199 区间的新菜单与角色关联，再重建；最后隐藏旧的一级分组。
USE `mall`;

DELETE FROM ums_role_menu_relation WHERE menu_id BETWEEN 100 AND 199;
DELETE FROM ums_menu WHERE id BETWEEN 100 AND 199;

INSERT INTO ums_menu(id,parent_id,create_time,title,level,sort,name,icon,hidden) VALUES
 (100,0,NOW(),'自选商品管理',0,100,'pick','product',0),
 (101,0,NOW(),'复购商品管理',0,99,'rebuy','product-list',0),
 (102,0,NOW(),'订单管理',0,98,'orderMgr','order',0),
 (103,0,NOW(),'会员',0,97,'memberMgr','ums-admin',0),
 (104,0,NOW(),'财务中心',0,96,'financeMgr','total-today',0),
 (105,0,NOW(),'服务中心',0,95,'serviceMgr','ums',0),
 (110,100,NOW(),'商品列表',1,10,'pickProduct','product-list',0),
 (111,100,NOW(),'分类管理',1,9,'pickCategory','product-cate',0),
 (112,101,NOW(),'商品列表',1,10,'rebuyProduct','product-list',0),
 (113,101,NOW(),'分类管理',1,9,'rebuyCategory','product-cate',0),
 (114,102,NOW(),'筛选',1,10,'orderMgrFilter','product-list',0),
 (115,102,NOW(),'订单销量汇总',1,9,'orderSummary','total-today',0),
 (116,102,NOW(),'订单详情',1,8,'orderMgrDetails','document',0),
 (117,102,NOW(),'退货申请处理',1,7,'orderMgrReturnApply','order-return',1),
 (118,102,NOW(),'退货原因设置',1,6,'orderMgrReturnReason','order-return-reason',1),
 (119,103,NOW(),'会员列表',1,10,'memberMgrList','user',0),
 (120,103,NOW(),'会员身份',1,9,'memberIdentity','ums-role',0),
 (121,103,NOW(),'合伙人管理',1,8,'memberMgrPartner','ums-role',0),
 (122,103,NOW(),'联创管理',1,7,'memberMgrCofounder','ums-role',0),
 (123,103,NOW(),'会员统计',1,6,'memberStats','total-today',0),
 (124,103,NOW(),'注册须知',1,5,'memberRegisterNotice','form',0),
 (125,103,NOW(),'公告管理',1,4,'memberMgrNotices','form',0),
 (126,103,NOW(),'在线咨询',1,3,'memberMgrConsultations','form',0),
 (127,103,NOW(),'发送记录',1,2,'memberMgrMessageRecords','form',0),
 (128,104,NOW(),'资金分析',1,10,'financeAnalysis','total-today',0),
 (129,104,NOW(),'资金管理',1,9,'financeFunds','total-today',0),
 (130,104,NOW(),'产品积分明细',1,8,'financeProductPoints','product-comment',0),
 (131,104,NOW(),'复购积分明细',1,7,'financeRepurchasePoints','product-comment',0),
 (132,104,NOW(),'钱包明细管理',1,6,'financeWallet','total-week',0),
 (133,104,NOW(),'每日贡献奖励池',1,5,'financeDividend','total-yesterday',0),
 (134,105,NOW(),'账号与安全',1,10,'serviceAccount','password',0),
 (135,105,NOW(),'隐私政策',1,9,'servicePrivacy','form',0),
 (136,105,NOW(),'平台设置',1,8,'serviceSettings','ums',0),
 (137,105,NOW(),'图片管理',1,7,'serviceImages','eye',0),
 (138,105,NOW(),'数据库管理',1,6,'serviceDatabase','table',0),
 (139,105,NOW(),'省市区街道地址库',1,5,'serviceAreaLibrary','marker',0),
 (140,105,NOW(),'访问统计',1,4,'serviceVisitStatistics','dashboard',0),
 (141,105,NOW(),'用户列表',1,3,'serviceAdmin','ums-admin',0),
 (142,105,NOW(),'角色列表',1,3,'serviceRole','ums-role',0),
 (143,105,NOW(),'菜单列表',1,3,'serviceMenu','ums-menu',0),
 (144,105,NOW(),'资源列表',1,3,'serviceResource','ums-resource',0),
 (145,105,NOW(),'部门管理',1,2,'serviceDepartment','ums',0),
 (146,105,NOW(),'登录日志',1,2,'serviceLoginLog','document',0),
 (147,105,NOW(),'品牌管理',1,2,'serviceBrand','product-brand',0),
 (148,105,NOW(),'商品属性',1,2,'serviceProductAttr','product-attr',0),
 (149,105,NOW(),'优惠券',1,2,'serviceCoupon','sms-coupon',0),
 (150,105,NOW(),'秒杀活动',1,2,'serviceFlash','sms-flash',0),
 (151,105,NOW(),'广告列表',1,2,'serviceAdvertise','sms-ad',0),
 (152,105,NOW(),'品牌推荐',1,2,'serviceHomeBrand','product-brand',0),
 (153,105,NOW(),'新品推荐',1,2,'serviceHomeNew','sms-new',0),
 (154,105,NOW(),'人气推荐',1,2,'serviceHomeHot','sms-hot',0),
 (155,105,NOW(),'专题推荐',1,2,'serviceHomeSubject','sms-subject',0);

-- 让原本拥有旧一级分组的角色同时拥有新菜单
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT r.role_id, m.id
FROM (SELECT DISTINCT role_id FROM ums_role_menu_relation WHERE menu_id IN (1,7,12,21,26)) r
CROSS JOIN ums_menu m
WHERE m.id BETWEEN 100 AND 199;

-- 旧的一级分组不再出现在侧边栏（路由保留，仅隐藏菜单）
UPDATE ums_menu SET hidden = 1 WHERE name IN ('pms','oms','sms','p0','ums');
