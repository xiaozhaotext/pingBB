CREATE TABLE IF NOT EXISTS pms_product_trash (
 product_id BIGINT NOT NULL PRIMARY KEY, zone_code VARCHAR(16) NOT NULL,
 deleted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, deleted_by BIGINT NOT NULL,
 KEY idx_trash_expiry(zone_code,deleted_at)
);
CREATE TABLE IF NOT EXISTS pms_trash_policy (
 zone_code VARCHAR(16) NOT NULL PRIMARY KEY, retention_days INT NOT NULL DEFAULT 30
);
INSERT IGNORE INTO pms_trash_policy VALUES ('PICK',30),('REBUY',30),('CONSUME',30);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT p.id,NOW(),'商品回收站',1,94,'pickTrash','product-list',0 FROM ums_menu p
WHERE p.name='pick' AND NOT EXISTS(SELECT 1 FROM ums_menu x WHERE x.name='pickTrash');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT rr.role_id,t.id FROM ums_menu p JOIN ums_role_menu_relation rr ON rr.menu_id=p.id JOIN ums_menu t ON t.name='pickTrash'
WHERE p.name='pick' AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=rr.role_id AND x.menu_id=t.id);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT p.id,NOW(),'商品回收站',1,94,'rebuyTrash','product-list',0 FROM ums_menu p
WHERE p.name='rebuy' AND NOT EXISTS(SELECT 1 FROM ums_menu x WHERE x.name='rebuyTrash');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT rr.role_id,t.id FROM ums_menu p JOIN ums_role_menu_relation rr ON rr.menu_id=p.id JOIN ums_menu t ON t.name='rebuyTrash'
WHERE p.name='rebuy' AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=rr.role_id AND x.menu_id=t.id);
INSERT INTO ums_menu(parent_id,create_time,title,level,sort,name,icon,hidden)
SELECT p.id,NOW(),'商品回收站',1,94,'consumeTrash','product-list',0 FROM ums_menu p
WHERE p.name='consume' AND NOT EXISTS(SELECT 1 FROM ums_menu x WHERE x.name='consumeTrash');
INSERT INTO ums_role_menu_relation(role_id,menu_id)
SELECT DISTINCT rr.role_id,t.id FROM ums_menu p JOIN ums_role_menu_relation rr ON rr.menu_id=p.id JOIN ums_menu t ON t.name='consumeTrash'
WHERE p.name='consume' AND NOT EXISTS(SELECT 1 FROM ums_role_menu_relation x WHERE x.role_id=rr.role_id AND x.menu_id=t.id);
