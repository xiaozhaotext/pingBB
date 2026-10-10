-- 仅用于 mall_phase3a 本地测试库；商品 26/27 是官方样例，请勿在运营库运行。
INSERT INTO pms_product_zone(product_id, zone_code, sort_order)
VALUES (26, 'PICK', 100), (27, 'REBUY', 100)
ON DUPLICATE KEY UPDATE sort_order=VALUES(sort_order);
