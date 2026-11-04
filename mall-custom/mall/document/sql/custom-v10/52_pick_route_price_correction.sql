-- Correct the existing Yunnan route's listing typo. Historical order snapshots remain unchanged.
START TRANSACTION;
UPDATE pms_sku_stock s
JOIN pms_product p ON p.id=s.product_id
JOIN pms_product_zone z ON z.product_id=p.id AND z.zone_code='PICK'
JOIN tri_membership_product t ON t.product_id=p.id AND t.enabled=1
SET s.price=3999.00
WHERE p.id=46 AND p.name='云南雪山湖泊6日游' AND p.price=39999.00 AND s.price=39999.00;
UPDATE pms_product p
JOIN pms_product_zone z ON z.product_id=p.id AND z.zone_code='PICK'
JOIN tri_membership_product t ON t.product_id=p.id AND t.enabled=1
SET p.price=3999.00,p.original_price=3999.00
WHERE p.id=46 AND p.name='云南雪山湖泊6日游' AND p.price=39999.00
  AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price<>3999.00);
COMMIT;
