/** Keep OSS product images on the site's working /oss proxy in H5. */
export function productImageUrl(pic?: string | null): string {
  let value = String(pic || '').trim()
  if (!value) value = '/static/temp/cate1.jpg'
  value = value.replace(/^https?:\/\/macro-oss\.oss-cn-shenzhen\.aliyuncs\.com\//i, '/oss/')
  if (/^\/?oss\//i.test(value)) value = '/' + value.replace(/^\//, '')
  // #ifdef MP-WEIXIN
  // 小程序包不能放大量图片，所有站内路径统一走线上域名（后台换图即时同步）
  if (/^\/(oss|product-images|column-images|static)\//i.test(value)) {
    return 'https://zhiyoumei.com.cn' + value
  }
  // #endif
  if (value.startsWith('/oss/')) {
    // #ifdef H5
    return window.location.origin + value
    // #endif
  }
  return value
}
