import { http } from '@/utils/http'
import type {
  ConfirmOrderResult,
  GenerateOrderResult,
  OrderParam,
  OmsOrderDetail,
  OmsOrderReturnApplyParam,
} from '@/types/order'
import type { CommonPage, PageParam } from '@/types/common'

/** 生成确认单信息 */
export const generateConfirmOrderAPI = (cartIds: number[]) => {
  return http<ConfirmOrderResult>({
    method: 'POST',
    url: '/order/generateConfirmOrder',
    data: cartIds,
  })
}

/**
 * 按专区隔离预览结算金额：切换优惠券或支付方式后重新计算。
 * 自选区可用现金或余额；复购区用复购积分抵扣，余款优先使用余额，不足部分走外部支付；消费区只用商品积分。
 */
export const settlementPreviewAPI = (data: {
  cartIds: number[]
  couponId?: number
  useBalance?: boolean
  useRepurchasePoints?: boolean
}) => {
  return http<ConfirmOrderResult>({
    method: 'POST',
    url: '/order/settlementPreview',
    data,
  })
}

/** 生成订单 */
export const generateOrderAPI = (data: OrderParam, idempotencyKey: string) => {
  return http<GenerateOrderResult>({
    method: 'POST',
    url: '/order/generateOrder',
    data,
    header: { 'Idempotency-Key': idempotencyKey },
  })
}

/** 按状态分页获取用户订单列表 */
export const getOrderListAPI = (params: PageParam & { status: number }) => {
  return http<CommonPage<OmsOrderDetail>>({
    method: 'GET',
    url: '/order/list',
    params,
  })
}

/** 根据ID获取订单详情 */
export const getOrderDetailAPI = (orderId: number) => {
  return http<OmsOrderDetail>({
    method: 'GET',
    url: `/order/detail/${orderId}`,
  })
}

/** 用户取消订单 */
export const cancelUserOrderAPI = (orderId: number) => {
  return http({
    method: 'POST',
    url: '/order/cancelUserOrder',
    params: { orderId },
  })
}

/** 用户确认收货 */
export const confirmReceiveOrderAPI = (orderId: number) => {
  return http({
    method: 'POST',
    url: '/order/confirmReceiveOrder',
    params: { orderId },
  })
}

/** 用户删除订单 */
export const deleteOrderAPI = (orderId: number) => {
  return http({
    method: 'POST',
    url: '/order/deleteOrder',
    params: { orderId },
  })
}

/** 支付成功回调 */
export const payOrderSuccessAPI = (params: { orderId: number; payType: number }) => {
  return http({
    method: 'POST',
    url: '/order/paySuccess',
    params,
  })
}

/** 仅本地测试进程启用；不代表真实资金支付。 */
export const confirmMockPaymentAPI = (orderId: number) =>
  http<string>({ method: 'POST', url: '/mockPayment/confirm', params: { orderId } })

/** 支付宝交易状态查询 */
export const fetchAliapyStatusAPI = (params: { outTradeNo: string }) => {
  return http<string>({
    method: 'GET',
    url: '/alipay/query',
    params,
  })
}

/** 微信H5下单：返回 { type, h5Url, outTradeNo } */
export const wechatOrderPayH5API = (orderSn: string) =>
  http<{ type: string; h5Url: string; outTradeNo: string }>({
    method: 'POST',
    url: '/wechatPay/h5',
    params: { orderSn },
  })

/** 申请退货 */
export const createReturnApplyAPI = (data: OmsOrderReturnApplyParam) => {
  return http({
    method: 'POST',
    url: '/returnApply/create',
    data,
  })
}

