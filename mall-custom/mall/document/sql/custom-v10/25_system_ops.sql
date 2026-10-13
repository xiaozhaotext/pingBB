-- 管理首页运维模块 2026-10-13：省市区街道地址库、发送记录、访问统计、数据库管理
--
-- 对应需求：REQ-ADM-SET-009/010/011/018/019/020/021
-- 只做新增：CREATE TABLE IF NOT EXISTS 与 INSERT ... WHERE NOT EXISTS，可重复执行，不含删表语句。
-- 执行：mysql mall_demo < 25_system_ops.sql

CREATE TABLE IF NOT EXISTS `sys_area` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint NOT NULL DEFAULT 0,
  `name` varchar(64) NOT NULL,
  `level` tinyint NOT NULL COMMENT '1省 2市 3区县 4街道',
  `code` varchar(16) DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_name` (`parent_id`, `name`),
  KEY `idx_parent_sort` (`parent_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='省市区街道地址库';

CREATE TABLE IF NOT EXISTS `sys_message_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `channel` varchar(16) NOT NULL COMMENT '短信/站内信/推送',
  `target` varchar(128) DEFAULT NULL,
  `content` varchar(500) DEFAULT NULL,
  `biz_type` varchar(32) DEFAULT NULL,
  `biz_id` varchar(64) DEFAULT NULL,
  `status` varchar(16) NOT NULL COMMENT 'SENT/FAILED/SKIPPED',
  `error_msg` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_channel_time` (`channel`, `create_time`),
  KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息发送记录';

CREATE TABLE IF NOT EXISTS `sys_visit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client` varchar(16) NOT NULL COMMENT 'app/admin',
  `path` varchar(255) NOT NULL,
  `method` varchar(8) NOT NULL,
  `visitor_key` varchar(64) DEFAULT NULL COMMENT '会员ID或匿名标识，用于统计独立访客',
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_client_time` (`client`, `create_time`),
  KEY `idx_visitor_time` (`visitor_key`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问统计原始记录';

-- 演示用地址数据：只在一个省（北京市）下铺满四级，便于验证增删改查；
-- 真实全国地址库请按同一结构用 SQL 导入，不在此脚本里伪造。
INSERT INTO `sys_area` (`parent_id`,`name`,`level`,`code`,`sort`)
SELECT 0,'北京市',1,'110000',1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_area` WHERE `parent_id`=0 AND `name`='北京市');
SET @p_bj=(SELECT id FROM `sys_area` WHERE `parent_id`=0 AND `name`='北京市' LIMIT 1);
INSERT INTO `sys_area` (`parent_id`,`name`,`level`,`code`,`sort`)
SELECT @p_bj,'北京市',2,'110100',1 FROM DUAL
WHERE @p_bj IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `sys_area` WHERE `parent_id`=@p_bj AND `name`='北京市');
SET @c_bj=(SELECT id FROM `sys_area` WHERE `parent_id`=@p_bj AND `name`='北京市' LIMIT 1);
INSERT INTO `sys_area` (`parent_id`,`name`,`level`,`code`,`sort`)
SELECT @c_bj,x.n,x.l,x.c,x.s FROM (
  SELECT '东城区' AS n,3 AS l,'110101' AS c,1 AS s UNION ALL
  SELECT '朝阳区',3,'110105',2 UNION ALL
  SELECT '海淀区',3,'110108',3
  ) x WHERE @c_bj IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM `sys_area` WHERE `parent_id`=@c_bj AND `name`=x.n);
INSERT INTO `sys_area` (`parent_id`,`name`,`level`,`code`,`sort`)
SELECT c.id,'东华门街道',4,NULL,1 FROM `sys_area` c
WHERE c.name='东城区' AND c.level=3 AND NOT EXISTS (SELECT 1 FROM `sys_area` a WHERE a.parent_id=c.id AND a.name='东华门街道');
INSERT INTO `sys_area` (`parent_id`,`name`,`level`,`code`,`sort`)
SELECT c.id,'建国门街道',4,NULL,2 FROM `sys_area` c
WHERE c.name='东城区' AND c.level=3 AND NOT EXISTS (SELECT 1 FROM `sys_area` a WHERE a.parent_id=c.id AND a.name='建国门街道');
INSERT INTO `sys_area` (`parent_id`,`name`,`level`,`code`,`sort`)
SELECT c.id,'望京街道',4,NULL,1 FROM `sys_area` c
WHERE c.name='朝阳区' AND c.level=3 AND NOT EXISTS (SELECT 1 FROM `sys_area` a WHERE a.parent_id=c.id AND a.name='望京街道');

-- 后台菜单：挂在现有 p0 菜单下，并授权给平台管理员角色（role_id=5）
SET @parent=(SELECT id FROM ums_menu WHERE name='p0' ORDER BY id LIMIT 1);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @parent,NOW(),'省市区街道地址库',1,20,'p0AreaLibrary','ums-admin',0
WHERE @parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='p0AreaLibrary');
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @parent,NOW(),'发送记录',1,21,'p0MessageRecord','ums-admin',0
WHERE @parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='p0MessageRecord');
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @parent,NOW(),'访问统计',1,22,'p0VisitStatistics','ums-admin',0
WHERE @parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='p0VisitStatistics');
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @parent,NOW(),'数据库管理',1,23,'p0Database','ums-admin',0
WHERE @parent IS NOT NULL AND NOT EXISTS(SELECT 1 FROM ums_menu WHERE name='p0Database');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m WHERE m.name IN ('p0AreaLibrary','p0MessageRecord','p0VisitStatistics','p0Database')
AND EXISTS(SELECT 1 FROM ums_role WHERE id=5)
AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
