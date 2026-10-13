import http from '@/utils/http'

export interface PaidTrendDay {
  day: string
  orderCount: number
  paidAmount: number
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
}

export function getDashboardOverviewAPI() {
  return http<DashboardOverview>({ url: '/dashboard/overview', method: 'get' })
}
