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
