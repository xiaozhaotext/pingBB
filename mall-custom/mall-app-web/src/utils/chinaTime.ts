import { formatDate } from './date'

export function chinaDateTime(value?: string | number | Date | null): string {
  if (value == null || value === '') return '—'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return formatDate(date, 'yyyy-MM-dd hh:mm', 'Asia/Shanghai')
}
