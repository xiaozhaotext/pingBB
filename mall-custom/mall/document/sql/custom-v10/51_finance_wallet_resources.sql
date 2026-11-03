-- The finance role can see the wallet menu; grant its two specific endpoints.
-- Keep read and manual-credit permissions separate for later role adjustments.
INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),'消费余额与充值查询','/p0/wallet','查看消费余额、充值订单和余额流水',7
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/p0/wallet');

INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),'会员余额手动加款','/p0/wallet/manual-recharge','为会员手动增加消费余额',7
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/p0/wallet/manual-recharge');

INSERT INTO ums_role_resource_relation(role_id,resource_id)
SELECT role.id,resource.id FROM ums_role role
JOIN ums_resource resource ON resource.url IN ('/p0/wallet','/p0/wallet/manual-recharge')
WHERE role.id=6 AND role.name='财务' AND role.status=1
  AND NOT EXISTS (
    SELECT 1 FROM ums_role_resource_relation relation
    WHERE relation.role_id=role.id AND relation.resource_id=resource.id
  );
