import http from '@/utils/http'

export interface PaidTrendDay {
  day: string
  orderCount: number
  paidAmount: number
}

export interface DashboardOverview {
  productCount: number
  orderCount: number
  memberCount: number
  todayOrderCount: number
  todayPaidAmount: number
  totalPaidAmount: number
  paidTrend: PaidTrendDay[]
  amountNote: string
}

export function getDashboardOverviewAPI() {
  return http<DashboardOverview>({ url: '/dashboard/overview', method: 'get' })
}
