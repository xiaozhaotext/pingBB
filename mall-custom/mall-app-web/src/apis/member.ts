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
/** 后台变更会员身份（运营用） */
export const changeIdentityAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/identity/change', data })

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
/** 积分变动(幂等) */
export const changePointsAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/points/change', data })

/** 获取合伙人权益方案（模拟） */
export const getPartnerConfigAPI = () => http<any>({ method: 'GET', url: '/member/partner/config' })
/** 创建合伙人模拟购买订单 */
export const createPartnerOrderAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/partner/order', data })
/** 模拟支付并升级为合伙人 */
export const payPartnerAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/partner/pay', data })

/** 获取联创权益方案（模拟） */
export const getCofounderConfigAPI = () => http<any>({ method: 'GET', url: '/member/cofounder/config' })
/** 创建联创模拟购买订单 */
export const createCofounderOrderAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/cofounder/order', data })
/** 模拟支付并升级为联创 */
export const payCofounderAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/cofounder/pay', data })

/** 查询收益账户（模拟） */
export const getEarningsAccountAPI = () => http<any>({ method: 'GET', url: '/member/earnings/account' })
/** 查询收益流水（模拟） */
export const getEarningsLedgerAPI = () => http<any[]>({ method: 'GET', url: '/member/earnings/ledger' })
/** 模拟发放收益(仅演示) */
export const simulateRewardAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/earnings/simulateReward', data })

/** 申请提现（模拟） */
export const applyWithdrawAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/withdraw/apply', data })
/** 我的提现申请列表（模拟） */
export const getWithdrawListAPI = () => http<any[]>({ method: 'GET', url: '/member/withdraw/list' })

/** 记录分红贡献事件 */
export const recordContributionAPI = (data: Record<string, any>) =>
  http<any>({ method: 'POST', url: '/member/dividend/contribution', data })
/** 模拟每日分红分配(基于贡献比例) */
export const allocateDividendAPI = (data: Record<string, any>) =>
  http<number>({ method: 'POST', url: '/member/dividend/allocate', data })
/** 查询模拟分配快照 */
export const getDividendSnapshotAPI = (statDate: number) =>
  http<any[]>({ method: 'GET', url: '/member/dividend/snapshot', params: { statDate } })
/** 查询模拟分配明细 */
export const getDividendDetailAPI = (statDate: number) =>
  http<any[]>({ method: 'GET', url: '/member/dividend/detail', params: { statDate } })
