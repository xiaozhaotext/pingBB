import http from '@/utils/http'

export interface PaidTrendDay {
  day: string
  orderCount: number
  paidAmount: number
}

export interface UserTrendDay {
  day: string
  totalUser: number
  activeUser: number
  newUser: number
}

export interface NameValue {
  name: string
  cnt: number
}

export interface DashboardOverview {
  productCount: number
  productOnSaleCount: number
  productOffSaleCount: number
  lowStockSkuCount: number
  orderCount: number
  pendingDeliveryCount: number
  memberCount: number
  todayOrderCount: number
  todayPaidAmount: number
  totalPaidAmount: number
  todayRefundedAmount: number
  totalRefundedAmount: number
  todayNetPaidAmount: number
  totalNetPaidAmount: number
  paidTrend: PaidTrendDay[]
  amountNote: string
  totalUser: number
  todayNewMemberCount: number
  newUserCount: number
  activeUserCount: number
  userTrend: UserTrendDay[]
  memberRegionStats: NameValue[]
  memberGenderStats: NameValue[]
  userMetricNote: string
}

export function getDashboardOverviewAPI() {
  return http<DashboardOverview>({ url: '/dashboard/overview', method: 'get' })
}

export interface FundPeriod { period: string; orderCount: number; paidAmount: number }
export const getFundTrendAPI = () => http<{ days: FundPeriod[]; months: FundPeriod[] }>({ url: '/dashboard/fund-trend', method: 'get' })
