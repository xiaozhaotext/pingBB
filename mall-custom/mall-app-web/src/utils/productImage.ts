/** Keep OSS product images on the site's working /oss proxy in H5. */
export function productImageUrl(pic?: string | null): string {
  let value = String(pic || '').trim()
  if (!value) return '/static/temp/cate1.jpg'
  value = value.replace(/^https?:\/\/macro-oss\.oss-cn-shenzhen\.aliyuncs\.com\//i, '/oss/')
  if (/^\/?oss\//i.test(value)) value = '/' + value.replace(/^\//, '')
  if (value.startsWith('/oss/')) {
    // #ifdef H5
    return window.location.origin + value
    // #endif
  }
  return value
}
