/** Only a persisted server order status can confirm completion. */
export const isOrderPaid = (status: unknown) => [1, 2, 3].includes(Number(status))

export const orderPromotionRules = (order: { orderItemList?: { promotionName?: string }[] }) =>
  Array.from(new Set((order.orderItemList || []).map(item => item.promotionName?.trim())
    .filter((name): name is string => Boolean(name && name !== '无优惠'))))

export const wechatReturnUrl = (currentUrl: string, orderId: number) => {
  const url = new URL(currentUrl)
  url.search = ''
  url.hash = `/pages/money/pay?orderId=${orderId}&checkPayment=1`
  return url.toString()
}

export const withWechatReturnUrl = (paymentUrl: string, returnUrl: string) => {
  const url = new URL(paymentUrl)
  url.searchParams.set('redirect_url', returnUrl)
  return url.toString()
}
