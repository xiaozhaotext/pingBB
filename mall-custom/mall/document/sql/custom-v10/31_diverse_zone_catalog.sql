-- Add existing published apparel, footwear and home goods to the demo zones.
-- The SELECT guards keep this migration safe on databases without these sample products.
INSERT IGNORE INTO pms_product_zone(product_id, zone_code, sort_order)
SELECT id, 'PICK', CASE id WHEN 30 THEN 230 WHEN 35 THEN 220 WHEN 43 THEN 210 ELSE 200 END
FROM pms_product
WHERE publish_status = 1 AND id IN (30, 31, 32, 35, 36, 43)
  AND (name LIKE 'HLA%' OR name LIKE '耐克%' OR name LIKE '万和%');

INSERT IGNORE INTO pms_product_zone(product_id, zone_code, sort_order)
SELECT id, 'REBUY', CASE id WHEN 35 THEN 230 WHEN 30 THEN 220 WHEN 43 THEN 210 ELSE 200 END
FROM pms_product
WHERE publish_status = 1 AND id IN (30, 31, 32, 35, 36, 43)
  AND (name LIKE 'HLA%' OR name LIKE '耐克%' OR name LIKE '万和%');
