-- Existing sandbox routes from migration 55 need a non-null attribute category
-- for portal versions that query attributes without guarding a null category.
DELIMITER $$
DROP PROCEDURE IF EXISTS sandbox_tour_detail_attributes$$
CREATE PROCEDURE sandbox_tour_detail_attributes()
BEGIN
  IF DATABASE() <> 'mall_demo_sandbox' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tour detail fix is restricted to mall_demo_sandbox';
  END IF;
  UPDATE pms_product
  SET product_attribute_category_id=0
  WHERE product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND product_attribute_category_id IS NULL;
END$$
CALL sandbox_tour_detail_attributes()$$
DROP PROCEDURE sandbox_tour_detail_attributes$$
DELIMITER ;
