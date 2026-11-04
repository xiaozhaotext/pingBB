-- 旅游路线使用独立属性类型，不复用服装或演示分类。
INSERT INTO pms_product_attribute_category (name, attribute_count, param_count)
SELECT '旅游路线', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM pms_product_attribute_category WHERE name = '旅游路线');
