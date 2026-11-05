CREATE TABLE IF NOT EXISTS pms_category_zone (category_id BIGINT NOT NULL PRIMARY KEY,zone_code VARCHAR(16) NOT NULL,KEY idx_category_zone(zone_code));
INSERT IGNORE INTO pms_category_zone SELECT id,'PICK' FROM pms_product_category;
