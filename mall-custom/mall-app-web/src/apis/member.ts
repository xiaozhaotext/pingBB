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

/** 获取合伙人权益方案（模拟） */
export const getPartnerConfigAPI = () => http<any>({ method: 'GET', url: '/member/partner/config' })

/** 获取联创权益方案（模拟） */
export const getCofounderConfigAPI = () => http<any>({ method: 'GET', url: '/member/cofounder/config' })

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

// ===== 以下为「模拟业务动作」接口：全部真实调用后端、真实入库，仅资金来源为模拟，不涉及真实资金 =====

/** 创建合伙人入驻订单（模拟下单） */
export const createPartnerOrderAPI = (data: { bizId: string }) =>
  http<any>({ method: 'POST', url: '/member/partner/order', data })
/** 合伙人订单模拟支付 -> 身份升级为合伙人 + 双积分赠送（模拟支付、真实改数据） */
export const payPartnerAPI = (data: { bizId: string }) =>
  http<any>({ method: 'POST', url: '/member/partner/pay', data })

/** 创建联创入驻订单（模拟下单） */
export const createCofounderOrderAPI = (data: { bizId: string }) =>
  http<any>({ method: 'POST', url: '/member/cofounder/order', data })
/** 联创订单模拟支付 -> 身份升级为联创 + 积分配送（模拟支付、真实改数据） */
export const payCofounderAPI = (data: { bizId: string }) =>
  http<any>({ method: 'POST', url: '/member/cofounder/pay', data })

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
