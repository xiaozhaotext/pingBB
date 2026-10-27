import { http } from '@/utils/http'
import type { LoginResult, MemberInfo, LoginParam, RegisterParam } from '@/types/member'

/** 登录 */
export const loginAPI = (data: LoginParam) => {
  return http<LoginResult>({
    method: 'POST',
    url: '/sso/login',
    header: {
      'content-type': 'application/x-www-form-urlencoded;charset=utf-8',
    },
    data,
  })
}

/** 获取用户信息 */
export const getMemberInfoAPI = () => {
  return http<MemberInfo>({
    method: 'GET',
    url: '/sso/info',
  })
}

/** 注册 */
export const registerAPI = (data: RegisterParam) => {
  return http({
    method: 'POST',
    url: '/sso/register',
    header: {
      'content-type': 'application/x-www-form-urlencoded;charset=utf-8',
    },
    data,
  })
}

/** 获取验证码 */
export const getAuthCodeAPI = (telephone: string) => {
  return http({
    method: 'GET',
    url: '/sso/getAuthCode',
    params: { telephone },
  })
}

// ===================== 会员身份 / 邀请 / 双积分 / 合伙人 / 联创 / 收益提现 / 分红池 =====================
// 注意：合伙人、联创、收益、分红等全部为"模拟"能力，不涉及真实资金。

/** 查询我的当前身份 */
export const getMyIdentityAPI = () => http<any>({ method: 'GET', url: '/member/identity/current' })

/** 获取我的邀请码(自动生成) */
export const getMyInviteCodeAPI = () => http<string>({ method: 'GET', url: '/member/invitation/myCode' })
/** 我的邀请码详情 */
export const getInvitationDetailAPI = () => http<any>({ method: 'GET', url: '/member/invitation/detail' })
/** 通过邀请码绑定推荐关系 */
export const bindInviterAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/invitation/bind', data })
/** 我的推荐人 */
export const getMyInviterAPI = () => http<any>({ method: 'GET', url: '/member/invitation/inviter' })
/** 我邀请的下级 */
export const getMyInviteesAPI = () => http<any[]>({ method: 'GET', url: '/member/invitation/invitees' })

/** 查询某类积分账户(1=商品积分 2=复购积分) */
export const getPointsAccountAPI = (pointsType: number) =>
  http<any>({ method: 'GET', url: '/member/points/account', params: { pointsType } })
/** 查询积分流水 */
export const getPointsLedgerAPI = (pointsType?: number) =>
  http<any[]>({ method: 'GET', url: '/member/points/ledger', params: pointsType ? { pointsType } : {} })

// ===================== 会员购买（真实支付链路） =====================
// 后端：MembershipPaymentController
//   POST /member/membership/orders         统一下单（价格/积分快照 + 支付网关下单）
//   POST /member/membership/notify/alipay   支付宝异步回调（由支付平台调用，前端不调）
//   POST /member/membership/notify/wechat   微信支付异步回调（由支付平台调用，前端不调）
// 业务错误码：56001 参数不合法 / 56002 支付渠道未配置 / 56003 购买规则未启用 /
//             56004 已拥有该身份 / 56005 请求号冲突 / 56008 支付渠道异常

export type MembershipLevel = 'MEMBER' | 'PARTNER' | 'COFOUNDER'
export type MembershipPayMode = 'WECHAT_H5' | 'WECHAT_JSAPI' | 'ALIPAY_WAP'

export interface MembershipPurchaseParams {
  /** 必须与登录会员一致，后端会校验 */
  memberId: number
  level: MembershipLevel
  payChannel: 'WECHAT' | 'ALIPAY'
  payMode: MembershipPayMode
  /** 幂等键：8-64 位字母数字 */
  requestId: string
  /** 兼容旧字段；服务端忽略客户端 OpenID，使用公众号网页授权结果 */
  wechatOpenId?: string
}

export interface MembershipOrderData {
  orderId: number
  merchantOrderNo: string
  amount: number | string
  payChannel: string
  payMode: string
  /**
   * 支付网关返回的支付参数：
   * - 微信 H5：{ type: 'WECHAT_H5_URL', h5Url }
   * - 支付宝 WAP：{ type: 'ALIPAY_FORM', formHtml }
   */
  payment: Record<string, string>
}

/** 创建会员购买订单（真实链路） */
export const createMembershipOrderAPI = (data: MembershipPurchaseParams) =>
  http<MembershipOrderData>({ method: 'POST', url: '/member/membership/orders', data })

export interface MembershipPaymentOptions {
  purchaseEnabled: boolean
  sandbox: boolean
  membershipV2: boolean
  wechatReady: boolean
  wechatJsapiReady?: boolean
  alipayReady: boolean
}
export const getMembershipPaymentOptionsAPI = () =>
  http<MembershipPaymentOptions>({ method: 'GET', url: '/member/membership/payment-options' })

/**
 * 确认本地支付桩订单（仅 dev 环境下后端返回 payment.mock=true 时调用）。
 * 上线后对接真实支付渠道，该入口不可用。
 */
export const confirmMockMembershipPaymentAPI = (orderId: number | string) =>
  http<string>({ method: 'POST', url: '/mockPayment/membership/confirm', params: { orderId } })

/** 查询收益账户（模拟） */
export const getEarningsAccountAPI = () => http<any>({ method: 'GET', url: '/member/earnings/account' })
/** 查询收益流水（模拟） */
export const getEarningsLedgerAPI = () => http<any[]>({ method: 'GET', url: '/member/earnings/ledger' })

/** 申请提现（模拟） */
export const applyWithdrawAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/withdraw/apply', data })
/** 我的提现申请列表（模拟） */
export const getWithdrawListAPI = () => http<any[]>({ method: 'GET', url: '/member/withdraw/list' })

/** 仅查询当前登录会员自己的模拟分配结果 */
export const getMyDividendSnapshotAPI = (statDate: number) =>
  http<any[]>({ method: 'GET', url: '/member/dividend/mySnapshot', params: { statDate } })

/** 我的分红信息（份数/今日/累计/封顶，真实数据） */
export const getRechargeAccountAPI = () => http<{balance:string}>({method:'GET',url:'/member/recharge/account'})
export const getWithdrawalOptionsAPI = () => http<{sandbox:boolean}>({method:'GET',url:'/member/withdraw/options'})

export const getDividendInfoAPI = () =>
  http<any>({ method: 'GET', url: '/member/dividend/info' })

/** 我的分红记录（真实数据） */
export const getDividendRecordsAPI = (params: { page?: number; size?: number }) =>
  http<any[]>({ method: 'GET', url: '/member/dividend/records', params })

// ===== 以下旧模拟购买接口已下线，统一走上方真实链路 createMembershipOrderAPI =====

/** 收益流水（模拟） */
export const getEarningsLedgerAPIAlready = undefined
/** 模拟发放收益到收益账户（后台真实入库；幂等键防重复） */
export const simulateRewardAPI = (data: {
  amount: string
  bizId?: string
  idempotencyKey: string
  remark?: string
}) => http<any>({ method: 'POST', url: '/member/earnings/simulateReward', data })

/** 记录贡献事件（可核验真实业务行为，幂等） */
export const recordContributionAPI = (data: {
  eventType: 'order_complete' | 'order_refund' | 'order_cancel'
  bizId: string
  contributionValue: string
  statDate: number
}) => http<any>({ method: 'POST', url: '/member/dividend/contribution', data })

/** 模拟每日分红分配（ratio = 个人贡献/当日总贡献，幂等可复算） */
export const allocateDividendAPI = (data: {
  statDate: number
  poolTotal?: string
  calcBatch: string
}) => http<any>({ method: 'POST', url: '/member/dividend/allocate', data })
