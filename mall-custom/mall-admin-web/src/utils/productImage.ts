/** Serve the copied product catalog through this site's working OSS proxy. */
export function productImageUrl(pic?: string | null): string {
  let value = String(pic || '').trim()
  if (!value) return ''
  value = value.replace(/^https?:\/\/macro-oss\.oss-cn-shenzhen\.aliyuncs\.com\//i, '/oss/')
  if (/^\/?oss\//i.test(value)) value = '/' + value.replace(/^\//, '')
  return value.startsWith('/oss/') ? window.location.origin + value : value
}
