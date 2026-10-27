import { http } from '@/utils/http'

export const isWechatBrowser = () =>
  typeof navigator !== 'undefined' && /MicroMessenger/i.test(navigator.userAgent)

export const ensureWechatOauth = async (purpose: 'ORDER' | 'MEMBERSHIP', orderId?: number) => {
  const status = await http<{ authorized: boolean }>({
    method: 'GET', url: '/member/wechat-oauth/status', params: { purpose },
  })
  if (status.data.authorized) return true
  const result = await http<{ url: string }>({
    method: 'POST', url: '/member/wechat-oauth/authorize', params: { purpose, orderId },
  })
  window.location.assign(result.data.url)
  return false
}

export const invokeWechatJsapi = (payment: Record<string, string>): Promise<void> =>
  new Promise((resolve, reject) => {
    const invoke = () => {
      const bridge = (window as any).WeixinJSBridge
      if (!bridge) { reject(new Error('微信支付环境未就绪')); return }
      bridge.invoke('getBrandWCPayRequest', {
        appId: payment.appId,
        timeStamp: payment.timeStamp,
        nonceStr: payment.nonceStr,
        package: payment.package,
        signType: payment.signType,
        paySign: payment.paySign,
      }, (result: { err_msg?: string }) => {
        if (result?.err_msg === 'get_brand_wcpay_request:ok') resolve()
        else reject(new Error(result?.err_msg || '微信支付未完成'))
      })
    }
    if ((window as any).WeixinJSBridge) invoke()
    else document.addEventListener('WeixinJSBridgeReady', invoke, { once: true })
  })
