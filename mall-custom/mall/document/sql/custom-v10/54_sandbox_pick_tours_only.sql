-- Apply ONLY to mall_demo_sandbox after checking the demo route and its SKU prices.
-- This removes PICK zone assignments, not products, orders, or other zone assignments.
DELIMITER $$
DROP PROCEDURE IF EXISTS sandbox_pick_tours_only$$
CREATE PROCEDURE sandbox_pick_tours_only()
BEGIN
  DECLARE valid_demo_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF DATABASE() <> 'mall_demo_sandbox' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='This cleanup is restricted to mall_demo_sandbox';
  END IF;
  SELECT COUNT(*) INTO valid_demo_count
  FROM pms_product p
  WHERE p.name LIKE '%3999旅游权益演示%'
    AND p.publish_status=1 AND p.delete_status=0 AND p.price=3999.00
    AND EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price=3999.00)
    AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id
                    AND (s.price IS NULL OR s.price<>3999.00));
  IF valid_demo_count=0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='No eligible 3999 demo route; no PICK assignment was changed';
  END IF;
  START TRANSACTION;
  INSERT INTO tri_membership_product(product_id,enabled,updated_by)
  SELECT p.id,1,0 FROM pms_product p
  WHERE p.name LIKE '%3999旅游权益演示%'
    AND p.publish_status=1 AND p.delete_status=0 AND p.price=3999.00
    AND EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price=3999.00)
    AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id
                    AND (s.price IS NULL OR s.price<>3999.00))
  ON DUPLICATE KEY UPDATE enabled=1,updated_by=VALUES(updated_by);
  INSERT IGNORE INTO pms_product_zone(product_id,zone_code,sort_order)
  SELECT p.id,'PICK',1000 FROM pms_product p
  WHERE p.name LIKE '%3999旅游权益演示%'
    AND p.publish_status=1 AND p.delete_status=0 AND p.price=3999.00
    AND EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price=3999.00)
    AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id
                    AND (s.price IS NULL OR s.price<>3999.00));
  DELETE z FROM pms_product_zone z
  LEFT JOIN pms_product p ON p.id=z.product_id
  LEFT JOIN tri_membership_product t ON t.product_id=z.product_id AND t.enabled=1
  WHERE z.zone_code='PICK' AND (p.id IS NULL OR p.publish_status<>1 OR p.delete_status<>0
    OR p.price IS NULL OR p.price<>3999.00 OR t.product_id IS NULL
    OR NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=z.product_id AND s.price=3999.00)
    OR EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=z.product_id
               AND (s.price IS NULL OR s.price<>3999.00)));
  COMMIT;
END$$
CALL sandbox_pick_tours_only()$$
DROP PROCEDURE sandbox_pick_tours_only$$
DELIMITER ;
