-- 消费区三级结构：主题(海南旅游) -> 目的地(三亚) -> 商品(旅游线路商品)。
-- 在目的地/线路之上增加"主题"表；目的地归属主题；线路直接指向商品，点进即商品详情。

-- 1) 主题表
CREATE TABLE IF NOT EXISTS pms_consume_theme (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL COMMENT '主题名称，如海南旅游',
    image_url VARCHAR(500) NOT NULL DEFAULT '' COMMENT '主题封面',
    description VARCHAR(500) NOT NULL DEFAULT '',
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    KEY idx_consume_theme_visible (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费区主题';

-- 2) 目的地表增加 theme_id 归属
ALTER TABLE pms_consume_destination ADD COLUMN theme_id BIGINT NOT NULL DEFAULT 0 COMMENT '归属主题ID' AFTER id;

-- 3) 线路表：name/image/description 逐步弃用，展示改用关联商品；保留字段兼容旧数据，product_id 为展示核心
-- 无需改结构，已有 product_id/cover 即可。

-- 4) 预置主题：海南旅游（幂等）
INSERT INTO pms_consume_theme (name, image_url, description, enabled, sort_order)
SELECT '海南旅游', '/static/travel-photo/ocean-terrace.jpg', '海岛度假 · 环岛风光 · 热带风情', 1, 10
WHERE NOT EXISTS (SELECT 1 FROM pms_consume_theme WHERE name='海南旅游');

-- 5) 现有目的地归入海南旅游主题（幂等：仅更新 theme_id=0 的）
UPDATE pms_consume_destination SET theme_id = (SELECT id FROM pms_consume_theme WHERE name='海南旅游' LIMIT 1)
WHERE theme_id = 0;

-- 6) 预置更多目的地（幂等）。第4项"三亚海岸度假"线路改用商品47，第2项"海南环岛"仍商品47。
INSERT INTO pms_consume_destination (theme_id, name, image_url, description, enabled, sort_order)
SELECT t.id, '三亚', '/static/travel-photo/ocean-terrace.jpg', '热带海岸 · 阳光沙滩', 1, 10
FROM pms_consume_theme t WHERE t.name='海南旅游'
  AND NOT EXISTS (SELECT 1 FROM pms_consume_destination WHERE name='三亚');
INSERT INTO pms_consume_destination (theme_id, name, image_url, description, enabled, sort_order)
SELECT t.id, '海口', '/static/travel-photo/coastal-retreat.jpg', '椰城海岸 · 骑楼老街', 1, 8
FROM pms_consume_theme t WHERE t.name='海南旅游'
  AND NOT EXISTS (SELECT 1 FROM pms_consume_destination WHERE name='海口');

-- 7) 线路精简为"目的地 -> 商品"，去掉重复的旧线路（幂等：仅删名称含"云南"且非海南主题目的地的）
DELETE FROM pms_consume_route WHERE name='云南雪山湖泊6日游'
  AND destination_id IN (SELECT id FROM pms_consume_destination WHERE name IN ('三亚','海口'));
