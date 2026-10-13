-- 2026-10-13 修复：HTTPS 页面被拦截的 http:// 图片地址（BUG-5）
--
-- 现象：站点以 HTTPS 提供服务，页面里的 http:// 图片会被浏览器按“混合内容”拦截，
--       导致首页 banner/品牌 logo、商品详情描述图、分类图标不显示。
--       实测 /portal/home/content 返回的图片里 http:// 共 47 处、https:// 0 处。
--
-- 已验证：http://macro-oss.oss-cn-shenzhen.aliyuncs.com 换成 https 后仍返回 200 image/jpeg
--         （http 与 https 取到同一张图，size 一致），所以下面的替换是安全的。
--
-- 注意：其它第三方图床不一定支持 https（实测 http://img10.360buyimg.com 换 https 后返回 404），
--       因此本脚本只替换已验证可用的 macro-oss 域名，其余地址由第 3 步的报告列出，请人工确认后处理。
--       长期建议把这些外部演示图床换成自托管地址（商品图已经在用 /oss/... 相对路径）。

-- 1) 先看清楚当前有多少 http:// 地址（执行前后各跑一次对比）
SELECT 'pms_brand.logo' AS col, COUNT(*) AS rows_with_http FROM pms_brand WHERE logo LIKE 'http://%'
UNION ALL SELECT 'pms_brand.big_pic', COUNT(*) FROM pms_brand WHERE big_pic LIKE 'http://%'
UNION ALL SELECT 'sms_home_advertise.pic', COUNT(*) FROM sms_home_advertise WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.pic', COUNT(*) FROM pms_product WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.album_pics', COUNT(*) FROM pms_product WHERE album_pics LIKE 'http://%'
UNION ALL SELECT 'pms_product.detail_html', COUNT(*) FROM pms_product WHERE detail_html LIKE '%http://%'
UNION ALL SELECT 'pms_product.detail_mobile_html', COUNT(*) FROM pms_product WHERE detail_mobile_html LIKE '%http://%'
UNION ALL SELECT 'pms_product_category.icon', COUNT(*) FROM pms_product_category WHERE icon LIKE 'http://%'
UNION ALL SELECT 'pms_sku_stock.pic', COUNT(*) FROM pms_sku_stock WHERE pic LIKE 'http://%'
UNION ALL SELECT 'cms_subject.pic', COUNT(*) FROM cms_subject WHERE pic LIKE 'http://%'
UNION ALL SELECT 'cms_subject.album_pics', COUNT(*) FROM cms_subject WHERE album_pics LIKE 'http://%'
UNION ALL SELECT 'ums_member.icon', COUNT(*) FROM ums_member WHERE icon LIKE 'http://%'
UNION ALL SELECT 'ums_admin.icon', COUNT(*) FROM ums_admin WHERE icon LIKE 'http://%';

-- 2) 只替换已验证支持 HTTPS 的域名
UPDATE pms_brand SET logo = REPLACE(logo, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE logo LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_brand SET big_pic = REPLACE(big_pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE big_pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE sms_home_advertise SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET album_pics = REPLACE(album_pics, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE album_pics LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET detail_html = REPLACE(detail_html, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE detail_html LIKE '%http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET detail_mobile_html = REPLACE(detail_mobile_html, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE detail_mobile_html LIKE '%http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product_category SET icon = REPLACE(icon, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE icon LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_sku_stock SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE cms_subject SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE cms_subject SET album_pics = REPLACE(album_pics, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE album_pics LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE ums_member SET icon = REPLACE(icon, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE icon LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE ums_admin SET icon = REPLACE(icon, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/')
 WHERE icon LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';

-- 3) 复查：仍然存在的 http:// 图片地址（含其它域名），人工确认是否支持 https
SELECT 'pms_brand.logo' AS col, id AS row_id, logo AS val FROM pms_brand WHERE logo LIKE 'http://%'
UNION ALL SELECT 'pms_brand.big_pic', id, big_pic FROM pms_brand WHERE big_pic LIKE 'http://%'
UNION ALL SELECT 'sms_home_advertise.pic', id, pic FROM sms_home_advertise WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.pic', id, pic FROM pms_product WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product_category.icon', id, icon FROM pms_product_category WHERE icon LIKE 'http://%'
UNION ALL SELECT 'ums_member.icon', id, icon FROM ums_member WHERE icon LIKE 'http://%';
