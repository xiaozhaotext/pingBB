-- Sandbox catalog only. Keep historical products/orders; replace PICK assignments.
DELIMITER $$
DROP PROCEDURE IF EXISTS sandbox_real_tour_routes$$
CREATE PROCEDURE sandbox_real_tour_routes()
BEGIN
  DECLARE route_category_id BIGINT;
  DECLARE route_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  IF DATABASE() <> 'mall_demo_sandbox' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tour catalog migration is restricted to mall_demo_sandbox';
  END IF;

  START TRANSACTION;
  INSERT INTO pms_product_category
    (parent_id,name,level,product_count,product_unit,nav_status,show_status,sort,keywords,description)
  SELECT 0,'旅游路线',0,3,'条',1,1,1,'旅游路线','自选区旅游路线'
  WHERE NOT EXISTS (SELECT 1 FROM pms_product_category WHERE parent_id=0 AND name='旅游路线');
  SELECT id INTO route_category_id FROM pms_product_category
  WHERE parent_id=0 AND name='旅游路线' ORDER BY id LIMIT 1;

  SELECT COUNT(*) INTO route_count FROM pms_product
  WHERE product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D');
  IF route_count = 0 THEN
  INSERT INTO pms_product
    (product_category_id,name,pic,product_sn,delete_status,publish_status,new_status,
     recommand_status,verify_status,sort,sale,price,gift_growth,gift_point,use_point_limit,
     sub_title,description,original_price,stock,low_stock,unit,weight,preview_status,
     keywords,note,album_pics,detail_title,detail_desc,detail_html,detail_mobile_html,
     promotion_type,product_category_name)
  VALUES
    (route_category_id,'云南雪山湖泊6日游','/sandbox/static/travel-photo/alpine-lake.jpg',
     'SBX-TOUR-YUNNAN-6D',0,1,1,1,1,300,0,3999.00,0,0,0,
     '6天5晚 · 雪山、湖泊与古城','云南6天5晚参考行程，游览古城、雪山和高原湖泊。',
     3999.00,100,5,'条',0,0,'旅游路线,云南,雪山','沙箱路线占位图；实际出发安排以平台公布为准',
     '/sandbox/static/travel-photo/alpine-lake.jpg','云南雪山湖泊6日游',
     '6天5晚，古城、雪山、高原湖泊',
     '<h3>云南雪山湖泊6日游 · 6天5晚</h3><p>第1天：抵达昆明，入住休整。</p><p>第2天：前往大理，漫步洱海与古城。</p><p>第3天：大理周边山水游览。</p><p>第4天：前往丽江，游览丽江古城。</p><p>第5天：雪山与蓝月谷风景体验。</p><p>第6天：返程。</p><p>路线与图片为沙箱展示内容，正式出发日期、交通和服务以实际商品公告为准。</p>',
     '<h3>云南雪山湖泊6日游 · 6天5晚</h3><p>第1天：抵达昆明，入住休整。</p><p>第2天：前往大理，漫步洱海与古城。</p><p>第3天：大理周边山水游览。</p><p>第4天：前往丽江，游览丽江古城。</p><p>第5天：雪山与蓝月谷风景体验。</p><p>第6天：返程。</p><p>路线与图片为沙箱展示内容，正式出发日期、交通和服务以实际商品公告为准。</p>',
     0,'旅游路线'),
    (route_category_id,'三亚海岸度假5日游','/sandbox/static/travel-photo/ocean-terrace.jpg',
     'SBX-TOUR-SANYA-5D',0,1,1,1,1,200,0,3999.00,0,0,0,
     '5天4晚 · 海岸、沙滩与热带风光','三亚5天4晚参考行程，体验海岸、沙滩和热带风光。',
     3999.00,100,5,'条',0,0,'旅游路线,三亚,海岸','沙箱路线占位图；实际出发安排以平台公布为准',
     '/sandbox/static/travel-photo/ocean-terrace.jpg','三亚海岸度假5日游',
     '5天4晚，海岸、沙滩、热带风光',
     '<h3>三亚海岸度假5日游 · 5天4晚</h3><p>第1天：抵达三亚，入住休整。</p><p>第2天：亚龙湾海岸与沙滩漫游。</p><p>第3天：热带雨林风景体验。</p><p>第4天：滨海自由活动与落日观景。</p><p>第5天：返程。</p><p>路线与图片为沙箱展示内容，正式出发日期、交通和服务以实际商品公告为准。</p>',
     '<h3>三亚海岸度假5日游 · 5天4晚</h3><p>第1天：抵达三亚，入住休整。</p><p>第2天：亚龙湾海岸与沙滩漫游。</p><p>第3天：热带雨林风景体验。</p><p>第4天：滨海自由活动与落日观景。</p><p>第5天：返程。</p><p>路线与图片为沙箱展示内容，正式出发日期、交通和服务以实际商品公告为准。</p>',
     0,'旅游路线'),
    (route_category_id,'山水湖畔慢游5日游','/sandbox/static/travel-photo/coastal-retreat.jpg',
     'SBX-TOUR-LAKESIDE-5D',0,1,1,1,1,100,0,3999.00,0,0,0,
     '5天4晚 · 山水、湖畔与自然步道','5天4晚参考行程，沿湖畔步道漫游，欣赏山水风光。',
     3999.00,100,5,'条',0,0,'旅游路线,湖畔,山水','沙箱路线占位图；实际出发安排以平台公布为准',
     '/sandbox/static/travel-photo/coastal-retreat.jpg','山水湖畔慢游5日游',
     '5天4晚，山水、湖畔、自然步道',
     '<h3>山水湖畔慢游5日游 · 5天4晚</h3><p>第1天：抵达目的地，湖畔入住。</p><p>第2天：湖岸风景步道与观景平台。</p><p>第3天：山地自然步道与村落漫游。</p><p>第4天：湖畔休闲与日落观景。</p><p>第5天：返程。</p><p>路线与图片为沙箱展示内容，正式出发日期、交通和服务以实际商品公告为准。</p>',
     '<h3>山水湖畔慢游5日游 · 5天4晚</h3><p>第1天：抵达目的地，湖畔入住。</p><p>第2天：湖岸风景步道与观景平台。</p><p>第3天：山地自然步道与村落漫游。</p><p>第4天：湖畔休闲与日落观景。</p><p>第5天：返程。</p><p>路线与图片为沙箱展示内容，正式出发日期、交通和服务以实际商品公告为准。</p>',
     0,'旅游路线');
  END IF;

  -- The legacy product_sn column has no unique key, so reject partial or duplicate seeds.
  SELECT COUNT(*) INTO route_count FROM pms_product
  WHERE product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D');
  IF route_count <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly three sandbox tour products';
  END IF;

  INSERT INTO pms_sku_stock(product_id,sku_code,price,stock,low_stock,pic,sale,lock_stock,sp_data)
  SELECT p.id,CONCAT(p.product_sn,'-STD'),3999.00,100,5,p.pic,0,0,'[]'
  FROM pms_product p
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id);

  SELECT COUNT(*) INTO route_count FROM pms_product p
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
    AND p.publish_status=1 AND p.delete_status=0 AND p.price=3999.00
    AND p.product_category_name='旅游路线' AND p.detail_mobile_html LIKE '%第1天%'
    AND p.pic LIKE '/sandbox/static/travel-photo/%'
    AND EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id AND s.price=3999.00 AND s.stock>0)
    AND NOT EXISTS (SELECT 1 FROM pms_sku_stock s WHERE s.product_id=p.id
                    AND (s.price IS NULL OR s.price<>3999.00));
  IF route_count <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tour products are incomplete; PICK assignments were not changed';
  END IF;

  INSERT INTO tri_membership_product(product_id,enabled,updated_by)
  SELECT p.id,1,0 FROM pms_product p
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D')
  ON DUPLICATE KEY UPDATE enabled=1,updated_by=VALUES(updated_by);

  DELETE FROM pms_product_zone WHERE zone_code='PICK';
  INSERT INTO pms_product_zone(product_id,zone_code,sort_order)
  SELECT p.id,'PICK',p.sort FROM pms_product p
  WHERE p.product_sn IN ('SBX-TOUR-YUNNAN-6D','SBX-TOUR-SANYA-5D','SBX-TOUR-LAKESIDE-5D');
  COMMIT;
END$$
CALL sandbox_real_tour_routes()$$
DROP PROCEDURE sandbox_real_tour_routes$$
DELIMITER ;
