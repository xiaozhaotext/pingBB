-- 演示数据修复 2026-10-13（可重复执行；只做追加与字段替换，不含删表语句）
--
-- 起因一：上游把「删除品牌」「删除商品属性分类」声明成 GET 接口
--   （PmsBrandController#delete、PmsProductAttributeCategoryController#delete），
--   一次端到端 GET 探测因此删掉了两行演示数据；pms_product.id=43 的 brand_id=1 变成悬空引用。
-- 起因二：站点以 HTTPS 提供服务，库里 http:// 的图片被浏览器按「混合内容」拦截，
--   首页 banner、品牌 logo、商品详情描述图不显示。
--
-- 安全性：
--   * INSERT IGNORE：目标库已有同 id 行时不覆盖任何现有数据；
--   * UPDATE 只替换仍为 http:// 且域名已验证支持 HTTPS 的地址；
--   * 实测 macro-oss.oss-cn-shenzhen.aliyuncs.com 换 https 后仍返回 200 且是同一张图；
--     img10.360buyimg.com 换 https 会 404，故保留不改，只在报告里列出，需人工确认。
-- 数据依据：mall/document/sql/mall.sql 的原始记录。
-- 执行方式：mysql mall_demo < 23_demo_data_repair_2026-10-13.sql
--   （root 用 socket 认证可直接执行，不需要数据库密码；本文件不含任何凭据）

-- 1) 恢复被误删的两行演示数据
INSERT IGNORE INTO `pms_brand`
  (`id`,`name`,`first_letter`,`sort`,`factory_status`,`show_status`,`product_count`,`product_comment_count`,`logo`,`big_pic`,`brand_story`)
VALUES
  (1, '万和', 'W', 0, 1, 1, 100, 100,
   'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/images/20200607/5b07ca8aN4e127d2f.jpg',
   'http://img13.360buyimg.com/cms/jfs/t1/121860/35/2430/187800/5ec4e294E22f3ffcc/1e233b65b94ba192.jpg',
   '万和成立于1993年8月，总部位于广东顺德国家级高新技术开发区内，是国内生产规模最大的燃气具专业制造企业，也是中国燃气具发展战略的首倡者和推动者、中国五金制品协会燃气用具分会第三届理事长单位。');

INSERT IGNORE INTO `pms_product_attribute_category` (`id`,`name`,`attribute_count`,`param_count`)
VALUES (1, '服装-T恤', 2, 5);

-- 2) 替换前统计：仍然使用 http:// 的图片字段
SELECT 'pms_brand.logo' AS col, COUNT(*) AS http_rows FROM pms_brand WHERE logo LIKE 'http://%'
UNION ALL SELECT 'pms_brand.big_pic', COUNT(*) FROM pms_brand WHERE big_pic LIKE 'http://%'
UNION ALL SELECT 'sms_home_advertise.pic', COUNT(*) FROM sms_home_advertise WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.pic', COUNT(*) FROM pms_product WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.album_pics', COUNT(*) FROM pms_product WHERE album_pics LIKE 'http://%'
UNION ALL SELECT 'pms_product.detail_html', COUNT(*) FROM pms_product WHERE detail_html LIKE '%http://%'
UNION ALL SELECT 'pms_product.detail_mobile_html', COUNT(*) FROM pms_product WHERE detail_mobile_html LIKE '%http://%'
UNION ALL SELECT 'pms_product_category.icon', COUNT(*) FROM pms_product_category WHERE icon LIKE 'http://%'
UNION ALL SELECT 'pms_sku_stock.pic', COUNT(*) FROM pms_sku_stock WHERE pic LIKE 'http://%'
UNION ALL SELECT 'cms_subject.pic', COUNT(*) FROM cms_subject WHERE pic LIKE 'http://%'
UNION ALL SELECT 'ums_member.icon', COUNT(*) FROM ums_member WHERE icon LIKE 'http://%'
UNION ALL SELECT 'ums_admin.icon', COUNT(*) FROM ums_admin WHERE icon LIKE 'http://%';

-- 3) 只把已验证支持 HTTPS 的图床由 http 改成 https（幂等：已是 https 的行不再匹配）
UPDATE pms_brand SET logo = REPLACE(logo, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE logo LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_brand SET big_pic = REPLACE(big_pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE big_pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE sms_home_advertise SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET album_pics = REPLACE(album_pics, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE album_pics LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET detail_html = REPLACE(detail_html, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE detail_html LIKE '%http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product SET detail_mobile_html = REPLACE(detail_mobile_html, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE detail_mobile_html LIKE '%http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_product_category SET icon = REPLACE(icon, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE icon LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE pms_sku_stock SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE cms_subject SET pic = REPLACE(pic, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE pic LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE ums_member SET icon = REPLACE(icon, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE icon LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';
UPDATE ums_admin SET icon = REPLACE(icon, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/', 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/') WHERE icon LIKE 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/%';

-- 4) 结果核对：两行演示数据必须回 1 行；第 2 步的 http_rows 应下降
SELECT 'pms_brand id=1' AS check_item, COUNT(*) AS rows_found FROM pms_brand WHERE id = 1
UNION ALL SELECT 'pms_product_attribute_category id=1', COUNT(*) FROM pms_product_attribute_category WHERE id = 1
UNION ALL SELECT 'pms_product.id=43 引用的品牌是否存在', COUNT(*) FROM pms_brand WHERE id = (SELECT brand_id FROM pms_product WHERE id = 43);

-- 5) 仍然残留的 http:// 图片地址（其它域名需人工确认是否支持 https 后再处理）
SELECT 'pms_brand.logo' AS col, COUNT(*) AS http_rows FROM pms_brand WHERE logo LIKE 'http://%'
UNION ALL SELECT 'pms_brand.big_pic', COUNT(*) FROM pms_brand WHERE big_pic LIKE 'http://%'
UNION ALL SELECT 'pms_product_category.icon', COUNT(*) FROM pms_product_category WHERE icon LIKE 'http://%'
UNION ALL SELECT 'pms_product.detail_html', COUNT(*) FROM pms_product WHERE detail_html LIKE '%http://%'
UNION ALL SELECT 'ums_member.icon', COUNT(*) FROM ums_member WHERE icon LIKE 'http://%';
