/** payAmount is the external-channel remainder; balanceAmount is a separate debit. */
export const orderFundsAmount = (order: { payAmount?: number | null; balanceAmount?: number | null }) =>
  Number(order.payAmount ?? 0) + Number(order.balanceAmount ?? 0)

export const orderPayTypeLabel = (value: number) => ({
  0: '未支付', 1: '支付宝', 2: '微信', 3: '沙盒模拟支付', 4: '余额 / 积分支付',
} as Record<number, string>)[value] || '未知支付方式'

export const orderPaymentStatusLabel = (status: number) => ({
  0: '未支付', 1: '已支付', 2: '已支付', 3: '已支付', 4: '已关闭', 5: '无效订单',
} as Record<number, string>)[status] || '未知支付状态'
