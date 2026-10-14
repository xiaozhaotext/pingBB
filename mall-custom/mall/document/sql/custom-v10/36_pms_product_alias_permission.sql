-- Register the legacy /pms/product/** API alias for platform admin role 5.
-- Additive and idempotent. Existing /product/** resources and other roles are unchanged.
INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),'商品管理兼容接口','/pms/product/**','仅用于兼容后台 PMS 商品 API 路径',
       (SELECT category_id FROM ums_resource WHERE url='/product/**' ORDER BY id LIMIT 1)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/pms/product/**');

SET @pms_product_alias_resource_id = (
  SELECT id FROM ums_resource WHERE url='/pms/product/**' ORDER BY id LIMIT 1
);
INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT 5,@pms_product_alias_resource_id
FROM DUAL
WHERE EXISTS (SELECT 1 FROM ums_role WHERE id=5)
  AND NOT EXISTS (
    SELECT 1 FROM ums_role_resource_relation
    WHERE role_id=5 AND resource_id=@pms_product_alias_resource_id
  );
