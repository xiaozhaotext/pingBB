-- The finance department role already has its menus, but had no API resources.
-- Grant only its own overview and finance APIs; other role permissions remain unchanged.
INSERT INTO ums_role_resource_relation(role_id, resource_id)
SELECT 6, resource.id FROM ums_resource resource
WHERE resource.id IN (31, 32, 33, 35, 37, 42)
  AND EXISTS (SELECT 1 FROM ums_role WHERE id=6 AND name='财务' AND status=1)
  AND NOT EXISTS (
    SELECT 1 FROM ums_role_resource_relation relation
    WHERE relation.role_id=6 AND relation.resource_id=resource.id
  );
