-- Apply after 55 on mall_demo_sandbox. Repair the three seeded tour routes only.
-- Existing positive stock and reservations are preserved.
DELIMITER $$
DROP PROCEDURE IF EXISTS sandbox_tour_detail_and_stock$$
CREATE PROCEDURE sandbox_tour_detail_and_stock()
BEGIN
  DECLARE route_count INT DEFAULT 0;
  DECLARE sellable_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF DATABASE() <> 'mall_demo_sandbox' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tour stock repair is restricted to mall_demo_sandbox';
  END IF;
  SELECT COUNT(*) INTO route_count FROM pms_product
  WHERE product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND product_category_name='旅游路线' AND price=3999.00;
  IF route_count <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected three seeded tour routes; apply migration 55 first';
  END IF;

  START TRANSACTION;
  UPDATE pms_product SET product_attribute_category_id=0
  WHERE product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND product_attribute_category_id IS NULL;

  INSERT INTO pms_sku_stock(product_id,sku_code,price,stock,low_stock,pic,sale,lock_stock,sp_data)
  SELECT p.id,CONCAT(p.product_sn,'-STD'),3999.00,100,5,p.pic,0,0,'[]'
  FROM pms_product p
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id);

  UPDATE pms_sku_stock s JOIN pms_product p ON p.id=s.product_id
  SET s.stock=100
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND s.price=3999.00 AND (s.stock IS NULL OR s.stock=0)
    AND COALESCE(s.lock_stock,0)=0;

  UPDATE pms_product p SET p.stock=(
    SELECT COALESCE(SUM(GREATEST(COALESCE(s.stock,0)-GREATEST(COALESCE(s.lock_stock,0),0),0)),0)
    FROM pms_sku_stock s WHERE s.product_id=p.id)
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D');

  SELECT COUNT(*) INTO sellable_count FROM pms_product p
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND p.publish_status=1 AND p.delete_status=0
    AND p.product_attribute_category_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id
                AND s.price=3999.00 AND COALESCE(s.stock,0)-GREATEST(COALESCE(s.lock_stock,0),0)>0);
  IF sellable_count <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tour route stock remains unavailable; inspect SKU reservations';
  END IF;
  COMMIT;
END$$
CALL sandbox_tour_detail_and_stock()$$
DROP PROCEDURE sandbox_tour_detail_and_stock$$
DELIMITER ;
