import type { OmsOrder, OrderQueryParam } from '@/types/order'
import { getOrderListAPI } from '@/apis/order'
import { formatDateTime } from '@/utils/datetime'

export type OrderExportFormat = 'csv' | 'xlsx'
const statusNames: Record<number, string> = { 0: '待付款', 1: '待发货', 2: '已发货', 3: '已完成', 4: '已关闭', 5: '无效订单' }
const columns = ['订单编号', '状态', '用户账号', '收货人', '手机号码', '商品原价', '外部应付', '余额支付', '运费', '优惠券抵扣', '促销优惠', '下单时间', '支付时间', '发货时间', '完成时间', '物流公司', '物流单号', '收货地址']

/** 按已应用的筛选条件读取全部分页，失败或数量变化时不生成不完整的表格。 */
export async function collectOrderExport(query: OrderQueryParam, isCurrent: () => boolean): Promise<OmsOrder[]> {
  const rows: OmsOrder[] = []
  const ids = new Set<number>()
  let expected = -1
  for (let pageNum = 1; ; pageNum++) {
    if (!isCurrent()) throw new Error('页面或筛选条件已变化，请重新导出')
    const response = await getOrderListAPI({ ...query, pageNum, pageSize: 200 })
    if (!isCurrent()) throw new Error('页面或筛选条件已变化，请重新导出')
    const page = response.data
    if (expected === -1) expected = page.total
    if (expected > 10000) throw new Error('筛选结果超过 10000 条，请缩小筛选范围后导出')
    if (page.total !== expected) throw new Error('订单数据已变化，请刷新后重新导出')
    for (const row of page.list) {
      if (ids.has(row.id) || row.status !== query.status) throw new Error('订单数据已变化，请刷新后重新导出')
      ids.add(row.id)
      rows.push(row)
    }
    if (rows.length === expected) return rows
    if (!page.list.length || rows.length > expected) throw new Error('订单分页数据不完整，请重试')
  }
}

function values(order: OmsOrder): (string | number)[] {
  return [order.orderSn || '', statusNames[order.status] || '未知', order.memberUsername || '',
    order.receiverName || '', order.receiverPhone || '', Number(order.totalAmount || 0),
    Number(order.payAmount || 0), Number(order.balanceAmount || 0), Number(order.freightAmount || 0),
    Number(order.couponAmount || 0), Number(order.promotionAmount || 0), formatDateTime(order.createTime),
    formatDateTime(order.paymentTime), formatDateTime(order.deliveryTime), formatDateTime(order.receiveTime),
    order.deliveryCompany || '', order.deliverySn || '',
    [order.receiverProvince, order.receiverCity, order.receiverRegion, order.receiverDetailAddress].filter(Boolean).join('')]
}

/** CSV 文本防止公式注入；号码按文本保存，避免长订单号丢失精度。 */
function csvCell(value: string | number, index: number): string {
  let text = String(value)
  if (typeof value === 'string' && (/^[\s]*[=+\-@]/.test(text) || [0, 4, 16].includes(index))) text = "'" + text
  return '"' + text.replace(/"/g, '""') + '"'
}

export async function createOrderExport(rows: OmsOrder[], format: OrderExportFormat): Promise<Blob> {
  const data = rows.map(values)
  if (format === 'csv') {
    return new Blob(['\uFEFF', [columns.map(value => csvCell(value, -1)).join(','),
      ...data.map(row => row.map(csvCell).join(','))].join('\r\n')], { type: 'text/csv;charset=utf-8' })
  }
  const { Workbook } = await import('exceljs')
  const book = new Workbook()
  const sheet = book.addWorksheet('订单列表')
  sheet.addRow(columns)
  sheet.addRows(data)
  sheet.getRow(1).font = { bold: true }
  sheet.views = [{ state: 'frozen', ySplit: 1 }]
  sheet.autoFilter = { from: 'A1', to: 'R1' }
  sheet.columns.forEach((column, index) => {
    column.width = index === 17 ? 48 : 22
    column.numFmt = index >= 5 && index <= 10 ? '0.00' : '@'
  })
  const buffer = await book.xlsx.writeBuffer()
  return new Blob([new Uint8Array(buffer)], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
}

export function downloadOrderExport(blob: Blob, title: string, format: OrderExportFormat) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${title}_${new Date().toISOString().replace(/[:.]/g, '-')}.${format}`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
