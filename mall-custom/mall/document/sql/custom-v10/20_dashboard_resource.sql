-- Register dashboard resource for permission system
-- Additive and repeatable on MySQL 8.

INSERT INTO ums_resource(create_time,name,url,description,category_id)
SELECT NOW(),'数据概览','/dashboard/**','后台数据概览接口',NULL
WHERE NOT EXISTS (SELECT 1 FROM ums_resource WHERE url='/dashboard/**');

-- Grant only to the reviewed platform administrator role (5)
INSERT IGNORE INTO ums_role_resource_relation(role_id,resource_id)
SELECT 5, id FROM ums_resource WHERE url='/dashboard/**';
