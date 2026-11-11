import { http } from '@/utils/http'

export const isWechatBrowser = () =>
  typeof navigator !== 'undefined' && /MicroMessenger/i.test(navigator.userAgent)

export const ensureWechatOauth = async (purpose: 'ORDER' | 'MEMBERSHIP' | 'RECHARGE', orderId?: number) => {
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

export interface MiniProgramPayment {
  type: 'WECHAT_MINIPROGRAM'
  appId: string
  timeStamp: string
  nonceStr: string
  package: string
  signType: 'RSA'
  paySign: string
  outTradeNo: string
}

/** wx.login's short-lived code goes to the backend; only signed payment parameters return. */
export async function prepareMiniProgramPayment(orderSn: string): Promise<MiniProgramPayment> {
  const code = await new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: result => result.code ? resolve(result.code) : reject(new Error('微信登录失败，请重试')),
      fail: () => reject(new Error('微信登录失败，请重试')),
    })
  })
  const authorization = await http<{ appId: string; paymentTicket: string }>({
    method: 'POST', url: '/member/wechat-mini/login', data: { code },
  })
  // #ifdef MP-WEIXIN
  if (authorization.data.appId !== uni.getAccountInfoSync().miniProgram.appId) {
    throw new Error('小程序与服务器的 AppID 不一致，请更新应用后重试')
  }
  // #endif
  const result = await http<MiniProgramPayment>({
    method: 'POST', url: '/wechatPay/mini',
    data: { orderSn, paymentTicket: authorization.data.paymentTicket },
  })
  const payment = result.data
  if (payment.type !== 'WECHAT_MINIPROGRAM' || payment.appId !== authorization.data.appId
      || !/^\d+$/.test(payment.timeStamp) || !payment.nonceStr || !payment.package?.startsWith('prepay_id=')
      || payment.signType !== 'RSA' || !payment.paySign) {
    throw new Error('微信预支付参数无效，请重新发起支付')
  }
  return payment
}

export function requestMiniProgramPayment(payment: MiniProgramPayment): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: String(payment.timeStamp),
      nonceStr: payment.nonceStr,
      package: payment.package,
      signType: payment.signType,
      paySign: payment.paySign,
      success: () => resolve(),
      fail: result => reject(new Error(result.errMsg || '微信支付未完成')),
    })
  })
}
