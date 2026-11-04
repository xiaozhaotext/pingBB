-- 海澜之家旧京东品牌大图已返回 404；使用已验证可访问的品牌图。
-- 仅修复原始失效链接，不覆盖后台之后自行上传的大图。
UPDATE pms_brand
SET big_pic = logo
WHERE id = 50
  AND name = '海澜之家'
  AND big_pic = 'http://img10.360buyimg.com/cms/jfs/t1/133148/4/1605/470028/5edaf5ccEd7a687a9/e0a007631361ff75.jpg'
  AND logo = 'https://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/images/20200607/5a5c69b9N5d6c5696.jpg';
