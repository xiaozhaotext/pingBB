<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboardOverviewAPI, getFundTrendAPI, type DashboardOverview, type FundPeriod } from '@/apis/dashboard'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const overview = ref<DashboardOverview | null>(null)
const dimension = ref<'day' | 'month'>('day')
const trend = ref<{ days: FundPeriod[]; months: FundPeriod[] }>({ days: [], months: [] })
function ratio(current: number, previous?: FundPeriod) {
  if (!previous || !Number(previous.tradeAmount)) return '—'
  const change = (current - Number(previous.tradeAmount)) / Number(previous.tradeAmount) * 100
  return `${change >= 0 ? '+' : ''}${change.toFixed(1)}%`
}
function periodRows(monthly: boolean) {
  const source = monthly ? trend.value.months : trend.value.days
  const map = new Map(source.map(row => [row.period, row]))
  const today = new Date()
  const result = []
  for (let offset = monthly ? 11 : 29; offset >= 0; offset--) {
    const date = monthly ? new Date(today.getFullYear(), today.getMonth() - offset, 1) : new Date(today.getFullYear(), today.getMonth(), today.getDate() - offset)
    const key = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}${monthly ? '' : '-' + String(d.getDate()).padStart(2, '0')}`
    const period = key(date)
    const row = map.get(period) || { period, orderCount: 0, paidAmount: 0, cashAmount: 0, balanceAmount: 0, tradeAmount: 0 }
    const previous = new Date(date)
    if (monthly) previous.setMonth(previous.getMonth() - 1)
    else previous.setDate(previous.getDate() - 1)
    const priorYear = new Date(date)
    priorYear.setFullYear(priorYear.getFullYear() - 1)
    result.push({ ...row, day: period, month: period, chainRatio: ratio(Number(row.tradeAmount), map.get(key(previous))), yearRatio: ratio(Number(row.tradeAmount), map.get(key(priorYear))) })
  }
  return result.reverse()
}
const dayRows = computed(() => periodRows(false))
const monthRows = computed(() => periodRows(true))

async function load() {
  loading.value = true
  try {
    const [summary, periods] = await Promise.all([getDashboardOverviewAPI(), getFundTrendAPI()])
    overview.value = summary.data
    trend.value = periods.data
  } catch {
    ElMessage.error('资金分析加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)

function money(value: unknown) {
  const num = Number(value)
  return Number.isFinite(num) ? '￥' + num.toFixed(2) : '—'
}
</script>

<template>
  <main class="fund-analysis" v-loading="loading">
    <header class="head">
      <div>
        <small>财务中心</small>
        <h1>资金分析</h1>
        <p>按日、按月分别统计订单现金和余额支付，环比、同比按两者合计计算。当前日/月为截至今日数据。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <section class="cards">
      <el-card shadow="never" class="metric"><span>累计交易金额</span><strong>{{ overview ? money(overview.totalTradeAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计现金支付</span><strong>{{ overview ? money(overview.totalCashPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计余额支付</span><strong>{{ overview ? money(overview.totalBalancePaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计退款金额</span><strong>{{ overview ? money(overview.totalRefundedAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计现金净额</span><strong>{{ overview ? money(overview.totalNetPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>今日交易金额</span><strong>{{ overview ? money(overview.todayTradeAmount) : '—' }}</strong></el-card>
    </section>
    <el-card shadow="never" class="section">
      <template #header>
        <div class="table-head">
          <strong>资金数据表</strong>
          <el-radio-group v-model="dimension">
            <el-radio-button label="day">按日</el-radio-button>
            <el-radio-button label="month">按月</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <template v-if="dimension === 'day'">
        <el-table :data="dayRows" stripe empty-text="暂无已支付资金数据">
          <el-table-column prop="day" label="日期" min-width="150" />
          <el-table-column prop="orderCount" label="已支付订单数" width="140" />
          <el-table-column label="现金支付" width="150"><template #default="{ row }">{{ money(row.cashAmount) }}</template></el-table-column>
          <el-table-column label="余额支付" width="150"><template #default="{ row }">{{ money(row.balanceAmount) }}</template></el-table-column>
          <el-table-column label="交易合计" width="150"><template #default="{ row }">{{ money(row.tradeAmount) }}</template></el-table-column>
          <el-table-column prop="chainRatio" label="日环比" width="140" /><el-table-column prop="yearRatio" label="日同比" width="140" />
        </el-table>
      </template>
      <template v-else>
        <el-table :data="monthRows" stripe empty-text="暂无已支付资金数据">
          <el-table-column prop="month" label="月份" min-width="150" />
          <el-table-column prop="orderCount" label="已支付订单数" width="140" />
          <el-table-column label="现金支付" width="150"><template #default="{ row }">{{ money(row.cashAmount) }}</template></el-table-column>
          <el-table-column label="余额支付" width="150"><template #default="{ row }">{{ money(row.balanceAmount) }}</template></el-table-column>
          <el-table-column label="交易合计" width="150"><template #default="{ row }">{{ money(row.tradeAmount) }}</template></el-table-column>
          <el-table-column prop="chainRatio" label="月环比" width="140" /><el-table-column prop="yearRatio" label="月同比" width="140" />
        </el-table>
        <el-alert
          title="月统计查询完整月份；当前月统计截至今日。现金与余额为订单支付金额，退款另见上方指标。"
          type="info"
          :closable="false"
          class="note"
        />
      </template>
    </el-card>
  </main>
</template>

<style scoped>
.fund-analysis { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric span { display: block; color: #75847b; font-size: 13px; }
.metric strong { display: block; margin-top: 10px; color: #214a34; font-size: 24px; }
.table-head { display: flex; justify-content: space-between; align-items: center; }
.note { margin-top: 12px; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, 1fr); } }
</style>
