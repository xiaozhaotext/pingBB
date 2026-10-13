-- 2026-10-13 线上演示库修复：SKU 库存/价格脏数据（应用端「有货却下不了单」「后台改价应用端不变」）
--
-- 在演示服务器上执行（可重复执行、幂等）：
--   mysql --default-character-set=utf8mb4 -umall_demo -p mall_demo < fix-sku-stock-price-2026-10-13.sql
--
-- 与 mall/document/sql/custom-v10/28_sku_stock_integrity.sql 内容一致，本文件是线上演示库的执行副本。
-- 只修数据，不改表结构、不删数据。
--
-- 实测背景：
--   /portal/cart/list 里会员 wufen 的购物车有 pms_product.id=27（pms_sku_stock.id=98，lock_stock=-24），
--   下单预占库存 SQL 要求 lock_stock >= 0，负值导致更新 0 行 → 「库存不足，无法下单」。
--   同时商品 27 的 pms_product.price=998 而 SKU 价为 2699，出现「首页 998、详情与购物车 2699」。
-- 0) 修复前报告
SELECT '修复前' AS stage, 'lock_stock < 0 的 SKU' AS check_item, COUNT(*) AS rows_found FROM pms_sku_stock WHERE lock_stock < 0
UNION ALL SELECT '修复前', 'lock_stock IS NULL 的 SKU', COUNT(*) FROM pms_sku_stock WHERE lock_stock IS NULL
UNION ALL SELECT '修复前', 'stock IS NULL 的 SKU', COUNT(*) FROM pms_sku_stock WHERE stock IS NULL
UNION ALL SELECT '修复前', 'price IS NULL 的 SKU', COUNT(*) FROM pms_sku_stock WHERE price IS NULL
UNION ALL SELECT '修复前', 'sp_data 为空的 SKU', COUNT(*) FROM pms_sku_stock WHERE sp_data IS NULL OR sp_data = ''
UNION ALL SELECT '修复前', 'SKU最低价与商品价不一致的商品', COUNT(*) FROM (
    SELECT s.product_id FROM pms_sku_stock s JOIN pms_product p ON p.id = s.product_id
     WHERE s.price IS NOT NULL AND p.price IS NOT NULL
     GROUP BY s.product_id, p.price HAVING MIN(s.price) <> p.price
) t;

-- 0.1) 具体受影响的商品（改价前请人工确认这些商品的售价是否符合预期）
SELECT p.id AS product_id, p.name AS product_name, p.price AS product_price,
       MIN(s.price) AS sku_min_price, MAX(s.price) AS sku_max_price, COUNT(*) AS sku_rows
  FROM pms_sku_stock s JOIN pms_product p ON p.id = s.product_id
 WHERE s.price IS NOT NULL AND p.price IS NOT NULL
 GROUP BY p.id, p.name, p.price
HAVING MIN(s.price) <> p.price;

-- 1) 锁定库存兜底：负值/空值一律归零，避免预占库存接口判定失败
UPDATE pms_sku_stock SET lock_stock = 0 WHERE lock_stock IS NULL OR lock_stock < 0;

-- 2) 库存为空时回填商品维度的库存（后台生成的 SKU 只写销售属性，stock 为空）
UPDATE pms_sku_stock s JOIN pms_product p ON p.id = s.product_id
   SET s.stock = COALESCE(p.stock, 0)
 WHERE s.stock IS NULL;

UPDATE pms_sku_stock SET stock = 0 WHERE stock IS NULL;

-- 3) SKU 价格为空时回填商品价（否则应用端详情页无价、购物车无法下单）
UPDATE pms_sku_stock s JOIN pms_product p ON p.id = s.product_id
   SET s.price = p.price
 WHERE s.price IS NULL AND p.price IS NOT NULL;

-- 4) 历史不一致：SKU 最低价与商品价不同的商品，统一以商品价为准。
--    应用端首页/列表用商品价、详情与购物车用 SKU 价，两者长期不一致会让运营以为「改了价没生效」。
--    如需按规格单独定价，请先把商品价设成最低规格价，再在「商品属性」步骤维护各 SKU 价格。
UPDATE pms_sku_stock s
  JOIN pms_product p ON p.id = s.product_id
  JOIN (
      SELECT product_id, MIN(price) AS min_price
        FROM pms_sku_stock
       WHERE price IS NOT NULL
       GROUP BY product_id
  ) m ON m.product_id = s.product_id
   SET s.price = p.price
 WHERE p.price IS NOT NULL AND m.min_price <> p.price;

-- 5) 销售属性为空会让应用端 JSON 解析失败（详情页规格/加购报错），补成空数组
UPDATE pms_sku_stock SET sp_data = '[]' WHERE sp_data IS NULL OR sp_data = '';

-- 6) 修复后报告：除「SKU最低价与商品价不一致」应为 0 外，其余应全部归零
SELECT '修复后' AS stage, 'lock_stock < 0 的 SKU' AS check_item, COUNT(*) AS rows_found FROM pms_sku_stock WHERE lock_stock < 0
UNION ALL SELECT '修复后', 'lock_stock IS NULL 的 SKU', COUNT(*) FROM pms_sku_stock WHERE lock_stock IS NULL
UNION ALL SELECT '修复后', 'stock IS NULL 的 SKU', COUNT(*) FROM pms_sku_stock WHERE stock IS NULL
UNION ALL SELECT '修复后', 'price IS NULL 的 SKU', COUNT(*) FROM pms_sku_stock WHERE price IS NULL
UNION ALL SELECT '修复后', 'sp_data 为空的 SKU', COUNT(*) FROM pms_sku_stock WHERE sp_data IS NULL OR sp_data = ''
UNION ALL SELECT '修复后', 'SKU最低价与商品价不一致的商品', COUNT(*) FROM (
    SELECT s.product_id FROM pms_sku_stock s JOIN pms_product p ON p.id = s.product_id
     WHERE s.price IS NOT NULL AND p.price IS NOT NULL
     GROUP BY s.product_id, p.price HAVING MIN(s.price) <> p.price
) t;