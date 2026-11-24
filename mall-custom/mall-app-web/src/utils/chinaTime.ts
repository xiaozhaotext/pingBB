import { formatDate } from './date'

export function chinaDateTime(value?: string | number | Date | null): string {
  if (value == null || value === '') return '—'
  // 无时区的后端日期属于北京时间；显式偏移避免 iOS 对空格/短横线日期解析不一致。
  const normalized = typeof value === 'string'
    ? value.replace(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2}:\d{2}(?:\.\d+)?)$/, '$1T$2+08:00')
    : value
  const date = normalized instanceof Date ? normalized : new Date(normalized)
  if (Number.isNaN(date.getTime())) return '—'
  return formatDate(date, 'yyyy-MM-dd hh:mm', 'Asia/Shanghai')
}
