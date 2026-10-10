-- Local mall_phase3a test database only. Additive and safe to repeat.
-- Role 5 belongs to the local phase3a_admin fixture; never apply this mapping to production by ID.
INSERT INTO ums_resource (create_time, name, url, description, category_id)
SELECT NOW(), '本地经营概览', '/dashboard/overview', '测试库商品订单会员聚合', NULL
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url = '/dashboard/overview');

INSERT INTO ums_role_resource_relation (role_id, resource_id)
SELECT 5, r.id FROM ums_resource r
WHERE r.url = '/dashboard/overview'
  AND NOT EXISTS (
    SELECT 1 FROM ums_role_resource_relation rr
    WHERE rr.role_id = 5 AND rr.resource_id = r.id
  );
