-- P0 管理后台：在已经应用 01..09 的独立测试库执行。
-- 无 DROP；只为超级管理员角色 5 授权。其他角色默认拒绝。
CREATE TABLE IF NOT EXISTS p0_points_adjust_request (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  member_id BIGINT NOT NULL,
  points_type TINYINT NOT NULL,
  delta BIGINT NOT NULL,
  reason VARCHAR(200) NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  requested_by BIGINT NOT NULL,
  reviewed_by BIGINT DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  review_time DATETIME DEFAULT NULL,
  UNIQUE KEY uk_p0_points_adjust_key (idempotency_key),
  KEY idx_p0_points_adjust_member (member_id),
  KEY idx_p0_points_adjust_status (status),
  CONSTRAINT fk_p0_points_adjust_member FOREIGN KEY (member_id) REFERENCES ums_member(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),'P0会员运营后台','/p0/**','仅平台授权管理员；模拟规则与财务只读',NULL
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/p0/**');

SET @p0_resource_id = (SELECT id FROM ums_resource WHERE url='/p0/**' ORDER BY id LIMIT 1);
INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT 5,@p0_resource_id FROM DUAL
WHERE EXISTS (SELECT 1 FROM ums_role WHERE id=5)
  AND NOT EXISTS (SELECT 1 FROM ums_role_resource_relation WHERE role_id=5 AND resource_id=@p0_resource_id);

INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT 0,NOW(),'会员运营',0,10,'p0','ums',0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ums_menu WHERE name='p0');
SET @p0_menu_id = (SELECT id FROM ums_menu WHERE name='p0' ORDER BY id LIMIT 1);

INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT @p0_menu_id,NOW(),items.title,1,items.sort,items.name,'ums-admin',0
FROM (
 SELECT '会员管理' AS title,7 AS sort,'p0Members' AS name UNION ALL
 SELECT '合伙人管理',6,'p0Partner' UNION ALL
 SELECT '联创管理',5,'p0Cofounder' UNION ALL
 SELECT '双积分管理',4,'p0Points' UNION ALL
 SELECT '财务中心',3,'p0Finance' UNION ALL
 SELECT '每日贡献奖励池',2,'p0Dividend' UNION ALL
 SELECT '系统配置与日志',1,'p0Settings'
) AS items
WHERE NOT EXISTS (SELECT 1 FROM ums_menu m WHERE m.name=items.name);

INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT 5,m.id FROM ums_menu m
WHERE m.name IN ('p0','p0Members','p0Partner','p0Cofounder','p0Points','p0Finance','p0Dividend','p0Settings')
AND NOT EXISTS (SELECT 1 FROM ums_role_menu_relation r WHERE r.role_id=5 AND r.menu_id=m.id);
