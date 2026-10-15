export function formatOrderMoney(value: unknown): string {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : '0.00'
}
export function showOrderLogistics(order: { deliveryCompany?: string; deliverySn?: string }) {
  uni.showModal({ title: '物流信息', content: order.deliverySn
    ? `物流公司：${order.deliveryCompany || '未填写'}\n运单号：${order.deliverySn}`
    : '商家尚未填写运单号，请联系在线客服。', showCancel: false })
}
