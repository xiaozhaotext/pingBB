-- 29_department_upload_loginlog.sql
-- 补齐 REQ-ADM-SET-014(部门)、REQ-ADM-SET-004(上传设置)、REQ-ADM-SET-017(登录日志查看入口)。
-- 幂等：可在 MySQL 8 重复执行。

-- 1) 部门表
CREATE TABLE IF NOT EXISTS ums_department (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(64) NOT NULL,
  leader VARCHAR(64) DEFAULT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  role_id BIGINT DEFAULT NULL COMMENT '该部门默认角色（权限设置）',
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  remark VARCHAR(255) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_department_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台部门';

-- 2) 管理员归属部门（MySQL 8 无 ADD COLUMN IF NOT EXISTS，用预处理语句保证幂等）
SET @add_dept_col := (
  SELECT IF(COUNT(*) = 0,
            'ALTER TABLE ums_admin ADD COLUMN dept_id BIGINT NULL COMMENT ''所属部门''',
            'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ums_admin' AND COLUMN_NAME = 'dept_id');
PREPARE stmt_dept FROM @add_dept_col;
EXECUTE stmt_dept;
DEALLOCATE PREPARE stmt_dept;

-- 3) 上传设置默认值（配置中心“上传设置”分组）
INSERT INTO sys_config(config_key,config_value,config_group,remark,update_time)
SELECT 'upload.image_max_size','5','upload','单张图片大小上限(MB)',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='upload.image_max_size');
INSERT INTO sys_config(config_key,config_value,config_group,remark,update_time)
SELECT 'upload.image_extensions','jpg,jpeg,png,gif,webp','upload','允许上传的图片格式(逗号分隔)',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='upload.image_extensions');
INSERT INTO sys_config(config_key,config_value,config_group,remark,update_time)
SELECT 'upload.image_max_width','1920','upload','图片最大宽度(px)，超出按比例压缩',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='upload.image_max_width');
INSERT INTO sys_config(config_key,config_value,config_group,remark,update_time)
SELECT 'upload.image_optimize','1','upload','是否启用图片压缩优化：0关闭 1开启',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='upload.image_optimize');
INSERT INTO sys_config(config_key,config_value,config_group,remark,update_time)
SELECT 'upload.attachment_max_size','20','upload','附件大小上限(MB)',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='upload.attachment_max_size');

-- 4) 后台菜单：部门管理 / 登录日志（挂在“权限”目录 id=21 下）
INSERT INTO ums_menu(parent_id,title,level,sort,name,icon,hidden)
SELECT 21,'部门管理',1,5,'department','ums','0'
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='department');
INSERT INTO ums_menu(parent_id,title,level,sort,name,icon,hidden)
SELECT 21,'登录日志',1,6,'loginLog','ums','0'
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='loginLog');

-- 5) 授权给平台管理员角色(5)
INSERT IGNORE INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,id FROM ums_menu WHERE name IN ('department','loginLog');
