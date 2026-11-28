-- 消费区改造：目的地 -> 线路 两级结构（仿自选区活动模式）。
-- 只新增目的地/线路表，并把消费区原有日用品从专区归属中移除（不删除商品主数据、不影响历史订单）。
-- 封面、归属、排序、启停均存表，由后台管理页配置，不写死前端。

CREATE TABLE IF NOT EXISTS pms_consume_destination (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL COMMENT '目的地名称，如三亚、海南',
    image_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '目的地封面',
    description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '目的地简介',
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    KEY idx_consume_dest_visible (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费区目的地';

CREATE TABLE IF NOT EXISTS pms_consume_route (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    destination_id BIGINT NOT NULL COMMENT '归属目的地',
    name VARCHAR(120) NOT NULL COMMENT '线路名称，如海南环岛5天4夜',
    image_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '线路封面',
    description VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '线路简介/行程亮点',
    product_id BIGINT NOT NULL COMMENT '关联商品ID，点线路进商品详情购买',
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    KEY idx_consume_route_dest (destination_id, enabled, sort_order, id),
    CONSTRAINT fk_consume_route_dest FOREIGN KEY (destination_id) REFERENCES pms_consume_destination(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费区旅游线路';

-- 备份消费区原有专区归属，便于审计/回滚，不删除 pms_product 主数据。
CREATE TABLE IF NOT EXISTS pms_product_zone_consume_bak_20261128 AS
    SELECT * FROM pms_product_zone WHERE zone_code='CONSUME';

-- 清除消费区日用品：仅移除专区归属，商品资料与其他专区归属保留。
DELETE FROM pms_product_zone WHERE zone_code='CONSUME';

-- 分类筛选管理页不再展示消费区旧分类；这些分类仅归属消费区（不与其他专区共用），
-- 仅移除专区归属关系，分类本身与其他专区分类保留。
DELETE FROM pms_category_zone WHERE zone_code='CONSUME';

-- 预置示例数据（幂等：仅在表为空时写入），后续全部可在后台调整。
INSERT INTO pms_consume_destination (name, image_url, description, enabled, sort_order)
SELECT '三亚', '/static/travel-photo/ocean-terrace.jpg', '热带海岸 · 阳光沙滩 · 度假天堂', 1, 10
WHERE NOT EXISTS (SELECT 1 FROM pms_consume_destination);
INSERT INTO pms_consume_destination (name, image_url, description, enabled, sort_order)
SELECT '海南', '/static/travel-photo/coastal-retreat.jpg', '环岛风光 · 雨林秘境 · 海岛慢行', 1, 9
WHERE EXISTS (SELECT 1 FROM pms_consume_destination WHERE name='三亚')
  AND NOT EXISTS (SELECT 1 FROM pms_consume_destination WHERE name='海南');

-- 线路关联现有旅游商品：47=三亚海岸度假5日游，46=云南雪山湖泊6日游（价格均为3999）。
-- 商品需上架才能进详情购买，这里一并上架并加入消费区专区归属。
UPDATE pms_product SET publish_status=1 WHERE id IN (46,47) AND delete_status=0;
INSERT IGNORE INTO pms_product_zone (product_id, zone_code, sort_order, consume_points_per_yuan)
SELECT id, 'CONSUME', 100, 100 FROM pms_product WHERE id IN (46,47) AND delete_status=0;

INSERT INTO pms_consume_route (destination_id, name, image_url, description, product_id, enabled, sort_order)
SELECT d.id, '三亚海岸度假5日游', '/static/travel-photo/ocean-terrace.jpg', '5天4晚 · 海岸、沙滩与热带风光', 47, 1, 10
FROM pms_consume_destination d WHERE d.name='三亚'
  AND NOT EXISTS (SELECT 1 FROM pms_consume_route WHERE name='三亚海岸度假5日游');

INSERT INTO pms_consume_route (destination_id, name, image_url, description, product_id, enabled, sort_order)
SELECT d.id, '海南环岛5天4夜', '/static/travel-photo/coastal-retreat.jpg', '环岛自驾 · 海岸线全景 · 雨林探秘', 47, 1, 9
FROM pms_consume_destination d WHERE d.name='海南'
  AND NOT EXISTS (SELECT 1 FROM pms_consume_route WHERE name='海南环岛5天4夜');

INSERT INTO pms_consume_route (destination_id, name, image_url, description, product_id, enabled, sort_order)
SELECT d.id, '云南雪山湖泊6日游', '/static/travel-photo/alpine-lake.jpg', '雪山、湖泊与高原风光', 46, 1, 8
FROM pms_consume_destination d WHERE d.name='海南'
  AND NOT EXISTS (SELECT 1 FROM pms_consume_route WHERE name='云南雪山湖泊6日游');
