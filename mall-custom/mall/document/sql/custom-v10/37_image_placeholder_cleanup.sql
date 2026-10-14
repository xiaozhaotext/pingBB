-- Remove only known placeholder values from image-reference columns.
-- This does not delete products, categories, files, or valid image URLs; repeat-safe.
UPDATE pms_product
SET album_pics=NULL
WHERE album_pics IN ('string','/');

UPDATE pms_product
SET pic=NULL
WHERE pic IN ('string','/');

UPDATE pms_product_category
SET icon=NULL
WHERE icon IN ('string','/');

UPDATE pms_sku_stock
SET pic=NULL
WHERE pic IN ('string','/');

UPDATE pms_brand
SET logo=NULL
WHERE logo IN ('string','/');

UPDATE sms_home_advertise
SET pic=NULL
WHERE pic IN ('string','/');
