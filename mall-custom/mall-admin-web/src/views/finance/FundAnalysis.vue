<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboardOverviewAPI, type DashboardOverview, type PaidTrendDay } from '@/apis/dashboard'
import { ElMessage } from 'element-plus'

interface DayRow extends PaidTrendDay {
  chainRatio: string
}

const loading = ref(false)
const overview = ref<DashboardOverview | null>(null)
const dimension = ref<'day' | 'month'>('day')

const dayRows = computed<DayRow[]>(() => {
  const trend = overview.value ? overview.value.paidTrend : []
  return trend.map((item, index) => {
    if (index === 0) return { ...item, chainRatio: '—' }
    const prev = trend[index - 1]
    if (!prev || !prev.paidAmount) return { ...item, chainRatio: '—' }
    const ratio = ((item.paidAmount - prev.paidAmount) / prev.paidAmount) * 100
    return { ...item, chainRatio: (ratio >= 0 ? '+' : '') + ratio.toFixed(1) + '%' }
  })
})

const monthRows = computed(() => {
  const trend = overview.value ? overview.value.paidTrend : []
  const buckets: Record<string, { month: string; orderCount: number; paidAmount: number }> = {}
  trend.forEach(item => {
    const month = item.day.slice(0, 7)
    if (!buckets[month]) buckets[month] = { month, orderCount: 0, paidAmount: 0 }
    buckets[month].orderCount += item.orderCount
    buckets[month].paidAmount += item.paidAmount
  })
  return Object.values(buckets).sort((a, b) => (a.month < b.month ? 1 : -1))
})

async function load() {
  loading.value = true
  try {
    overview.value = (await getDashboardOverviewAPI()).data
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
        <p>按日、按月统计已支付金额与订单数，并给出环比，数据表形式呈现。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <section class="cards">
      <el-card shadow="never" class="metric"><span>累计支付金额</span><strong>{{ overview ? money(overview.totalPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计退款金额</span><strong>{{ overview ? money(overview.totalRefundedAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计净收入</span><strong>{{ overview ? money(overview.totalNetPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>今日支付金额</span><strong>{{ overview ? money(overview.todayPaidAmount) : '—' }}</strong></el-card>
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
          <el-table-column label="已支付金额" width="160"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
          <el-table-column prop="chainRatio" label="环比（较前一日）" width="180" />
        </el-table>
      </template>
      <template v-else>
        <el-table :data="monthRows" stripe empty-text="暂无已支付资金数据">
          <el-table-column prop="month" label="月份" min-width="150" />
          <el-table-column prop="orderCount" label="已支付订单数" width="140" />
          <el-table-column label="已支付金额" width="160"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
        </el-table>
        <el-alert
          title="按月口径基于当前已支付流水累计；历史月份数据随业务运行逐步累积。"
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
