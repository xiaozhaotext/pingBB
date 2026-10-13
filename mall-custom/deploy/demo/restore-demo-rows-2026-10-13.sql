-- 2026-10-13 恢复：线上功能测试期间误删的演示数据
--
-- 背景：mall 上游把「删除品牌」「删除商品属性分类」声明为 GET
--   PmsBrandController#delete        @RequestMapping(value="/delete/{id}", method=RequestMethod.GET)
--   PmsProductAttributeCategoryController#delete 同上
-- 端到端 GET 探测因此触发了这两个删除接口，删除了下面两行演示数据。
-- 本脚本按 mall/document/sql/mall.sql 的原始记录恢复，使用 INSERT IGNORE，可重复执行。
-- 若目标库已存在同 id 行，脚本不会覆盖。

INSERT IGNORE INTO `pms_brand` VALUES (1, '万和', 'W', 0, 1, 1, 100, 100, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/images/20200607/5b07ca8aN4e127d2f.jpg', 'http://img13.360buyimg.com/cms/jfs/t1/121860/35/2430/187800/5ec4e294E22f3ffcc/1e233b65b94ba192.jpg', '万和成立于1993年8月，总部位于广东顺德国家级高新技术开发区内，是国内生产规模最大的燃气具专业制造企业，也是中国燃气具发展战略的首倡者和推动者、中国五金制品协会燃气用具分会第三届理事长单位。');

INSERT IGNORE INTO `pms_product_attribute_category` VALUES (1, '服装-T恤', 2, 5);
