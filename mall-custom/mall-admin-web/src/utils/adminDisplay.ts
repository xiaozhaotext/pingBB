/** Display-only translations. Original ledger identifiers remain unchanged. */
export function formatMoney(value: unknown): string {
  if (value === null || value === undefined || value === '' || !Number.isFinite(Number(value))) return '—'
  return new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value) || 0)
}

export function businessText(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
    .replace(/^DAILY:(\d{4}-\d{2}-\d{2})$/, '每日分红结算（$1）')
    .replace(/QA sandbox demonstration only/gi, '沙箱演示记录')
    .replace(/tri:batch:(\d+):(\d+)/gi, '三池分红（批次 $1，会员 $2）')
    .replace(/tri:MEMBERSHIP:(\d+):referral/gi, '会员购买直推奖励（会员单 $1）')
    .replace(/(?:tri:)?MEMBERSHIP:(\d+)/gi, '会员购买（会员单 $1）')
    .replace(/ADMIN:(\d+)/g, '平台加款（操作人 $1）')
    .replace(/WITHDRAW:(\d+)/g, '提现转入（申请 $1）')
    .replace(/\bRC([a-zA-Z0-9]+)/g, '充值单 $1')
    .replace(/\bPARTNER\b/g, '合伙人').replace(/\bCOFOUNDER\b/g, '联创').replace(/\bOPERATIONS\b/g, '运营')
}

export function businessSource(value: unknown, type?: unknown, orderSn?: unknown): string {
  if (orderSn) return `商品订单 ${orderSn}`
  const raw = value == null ? '' : String(value)
  if (/^\d+$/.test(raw)) {
    const label = /membership|referral|会员购买/.test(String(type)) ? '会员购买权益' : /dividend/.test(String(type)) ? '分红业务' : /consume|消费/.test(String(type)) ? '商品消费' : /refund|退款/.test(String(type)) ? '订单退款' : '关联业务'
    return `${label}（编号 ${raw}）`
  }
  return businessText(value)
}

export const contributionTypes: Record<string, string> = {
  earn: '获得贡献', consume: '消耗贡献', order_complete: '订单完成', order_refund: '订单退款', order_cancel: '订单取消',
}
