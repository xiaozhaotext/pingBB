#!/usr/bin/env bash
# 2026-10-13 线上功能测试后的数据修复脚本（在演示服务器以 root 执行，可重复执行）
#
# 背景：
#   1) 端到端 GET 探测误触发了两个被声明为 GET 的删除接口，删除了 pms_brand id=1 与
#      pms_product_attribute_category id=1 两行演示数据，本脚本按 mall.sql 原始记录补回。
#   2) 站点是 HTTPS，页面里的 http:// 图片会被浏览器按混合内容拦截，导致首页 banner、
#      品牌 logo、商品详情描述图不显示；--fix-images 会把已验证支持 HTTPS 的图床地址改为 https。
#
# 用法：
#   bash apply-test-fixes-2026-10-13.sh                # 只恢复误删数据并输出图片地址报告
#   bash apply-test-fixes-2026-10-13.sh --fix-images    # 恢复数据 + 修复 http 图片地址
set -euo pipefail

FIX_IMAGES=0
for arg in "$@"; do
  case "$arg" in
    --fix-images) FIX_IMAGES=1 ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) echo "未知参数：$arg（仅支持 --fix-images）" >&2; exit 2 ;;
  esac
done

command -v mysql >/dev/null 2>&1 || { echo '找不到 mysql 客户端' >&2; exit 5; }

env_file=/etc/mall-demo/db-password.env
[ -r "$env_file" ] || { echo "读不到 $env_file（需 root 执行）" >&2; exit 3; }
db_password="$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' "$env_file")"
[ -n "$db_password" ] || { echo "$env_file 中没有 SPRING_DATASOURCE_PASSWORD" >&2; exit 4; }
export MYSQL_PWD="$db_password"
db=(-umall_demo -h127.0.0.1)

echo '== 1) 恢复误删的两行演示数据（INSERT IGNORE，已有同 id 行则不覆盖）=='
mysql "${db[@]}" mall_demo <<'SQL'
INSERT IGNORE INTO `pms_brand` VALUES (1, '万和', 'W', 0, 1, 1, 100, 100, 'http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/images/20200607/5b07ca8aN4e127d2f.jpg', 'http://img13.360buyimg.com/cms/jfs/t1/121860/35/2430/187800/5ec4e294E22f3ffcc/1e233b65b94ba192.jpg', '万和成立于1993年8月，总部位于广东顺德国家级高新技术开发区内，是国内生产规模最大的燃气具专业制造企业，也是中国燃气具发展战略的首倡者和推动者、中国五金制品协会燃气用具分会第三届理事长单位。');

INSERT IGNORE INTO `pms_product_attribute_category` VALUES (1, '服装-T恤', 2, 5);
SQL
mysql "${db[@]}" -NBe "SELECT CONCAT('pms_brand id=1 行数=', COUNT(*)) FROM mall_demo.pms_brand WHERE id=1"
mysql "${db[@]}" -NBe "SELECT CONCAT('pms_product_attribute_category id=1 行数=', COUNT(*)) FROM mall_demo.pms_product_attribute_category WHERE id=1"

if [ "$FIX_IMAGES" = 1 ]; then
  echo '== 2) 把已验证支持 HTTPS 的图床地址由 http 改为 https =='
  mysql "${db[@]}" mall_demo <<'SQL'
SELECT 'pms_brand.logo' AS col, COUNT(*) FROM pms_brand WHERE logo LIKE 'http://%'
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

SELECT 'pms_brand.logo', id, logo FROM pms_brand WHERE logo LIKE 'http://%'
UNION ALL SELECT 'pms_brand.big_pic', id, big_pic FROM pms_brand WHERE big_pic LIKE 'http://%'
UNION ALL SELECT 'sms_home_advertise.pic', id, pic FROM sms_home_advertise WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.pic', id, pic FROM pms_product WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product_category.icon', id, icon FROM pms_product_category WHERE icon LIKE 'http://%'
UNION ALL SELECT 'ums_member.icon', id, icon FROM ums_member WHERE icon LIKE 'http://%';
SQL
else
  echo '== 2) 跳过图片地址修复（未加 --fix-images）=='
fi

echo '== 3) 仍然残留的 http:// 图片地址（其它域名请人工确认是否支持 https 后再处理）=='
mysql "${db[@]}" mall_demo -t <<'SQL'
SELECT 'pms_brand.logo' AS col, COUNT(*) AS rows_with_http FROM pms_brand WHERE logo LIKE 'http://%'
UNION ALL SELECT 'pms_brand.big_pic', COUNT(*) FROM pms_brand WHERE big_pic LIKE 'http://%'
UNION ALL SELECT 'sms_home_advertise.pic', COUNT(*) FROM sms_home_advertise WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product.pic', COUNT(*) FROM pms_product WHERE pic LIKE 'http://%'
UNION ALL SELECT 'pms_product_category.icon', COUNT(*) FROM pms_product_category WHERE icon LIKE 'http://%'
UNION ALL SELECT 'pms_product.detail_html', COUNT(*) FROM pms_product WHERE detail_html LIKE '%http://%'
UNION ALL SELECT 'ums_member.icon', COUNT(*) FROM ums_member WHERE icon LIKE 'http://%';
SQL

echo '完成。图片地址修复只处理了 macro-oss.oss-cn-shenzhen.aliyuncs.com（实测 https 可用）。'
