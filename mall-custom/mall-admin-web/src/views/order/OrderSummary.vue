<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getOrderListAPI } from '@/apis/order'
import { getDashboardOverviewAPI, type DashboardOverview } from '@/apis/dashboard'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

interface StatusItem {
  status: number
  label: string
  count: number
}

const loading = ref(false)
const counts = ref<StatusItem[]>([
  { status: 0, label: '待付款', count: 0 },
  { status: 1, label: '待发货', count: 0 },
  { status: 2, label: '待收货', count: 0 },
  { status: 3, label: '已完成', count: 0 },
  { status: 4, label: '已关闭', count: 0 },
  { status: 5, label: '无效订单', count: 0 },
])
const overview = ref<DashboardOverview | null>(null)
const totalOrders = computed(() => counts.value.reduce((sum, item) => sum + item.count, 0))

const statusChart = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 56, right: 24, bottom: 32 },
  xAxis: { type: 'category', data: counts.value.map(item => item.label) },
  yAxis: { type: 'value', name: '订单数' },
  series: [
    {
      name: '订单数',
      type: 'bar',
      barMaxWidth: 46,
      data: counts.value.map(item => item.count),
      itemStyle: { color: '#719680' },
    },
  ],
}))

const moneyChart = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['已支付订单数', '已支付金额'] },
  grid: { left: 56, right: 64, bottom: 32 },
  xAxis: { type: 'category', data: overview.value ? overview.value.paidTrend.map(item => item.day) : [] },
  yAxis: [{ type: 'value', name: '订单数' }, { type: 'value', name: '金额（元）' }],
  series: [
    {
      name: '已支付订单数',
      type: 'line',
      smooth: true,
      data: overview.value ? overview.value.paidTrend.map(item => item.orderCount) : [],
    },
    {
      name: '已支付金额',
      type: 'line',
      smooth: true,
      yAxisIndex: 1,
      data: overview.value ? overview.value.paidTrend.map(item => item.tradeAmount) : [],
    },
  ],
}))

async function load() {
  loading.value = true
  try {
    const results = await Promise.all(
      counts.value.map(item => getOrderListAPI({ pageNum: 1, pageSize: 1, status: item.status })),
    )
    counts.value = counts.value.map((item, index) => ({ ...item, count: results[index]?.data.total ?? 0 }))
    overview.value = (await getDashboardOverviewAPI()).data
  } catch {
    // 接口异常时保留上一次结果，避免展示错误数字
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <main class="order-summary" v-loading="loading">
    <header class="head">
      <div>
        <small>订单管理</small>
        <h1>订单销量汇总</h1>
        <p>按订单状态统计数量，并汇总平台已支付金额与近 7 日趋势。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <el-alert
      title="数据来自当前数据库真实订单；包含导入的官方示例订单，不得直接作为经营报表对外披露。"
      type="warning"
      :closable="false"
      show-icon
    />
    <section class="cards">
      <el-card shadow="never" class="metric"><span>订单总数</span><strong>{{ totalOrders }}</strong></el-card>
      <el-card v-for="item in counts" :key="item.status" shadow="never" class="metric">
        <span>{{ item.label }}</span><strong>{{ item.count }}</strong>
      </el-card>
      <el-card shadow="never" class="metric"><span>今日支付金额（含余额）</span><strong>￥{{ overview ? overview.todayTradeAmount.toFixed(2) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计支付金额（含余额）</span><strong>￥{{ overview ? overview.totalTradeAmount.toFixed(2) : '—' }}</strong></el-card>
    </section>
    <section class="charts">
      <el-card shadow="never">
        <template #header><strong>各状态订单数量</strong></template>
        <v-chart v-if="totalOrders > 0" :option="statusChart" autoresize class="chart" />
        <el-empty v-else description="暂无订单数据" />
      </el-card>
      <el-card shadow="never">
        <template #header><strong>近 7 日已支付订单趋势</strong></template>
        <v-chart v-if="overview && overview.paidTrend.length" :option="moneyChart" autoresize class="chart" />
        <el-empty v-else description="近 7 日没有已支付订单数据" />
      </el-card>
    </section>
  </main>
</template>

<style scoped>
.order-summary { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin: 18px 0; }
.metric span { display: block; color: #75847b; font-size: 13px; }
.metric strong { display: block; margin-top: 10px; color: #214a34; font-size: 24px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.chart { height: 320px; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, 1fr); } .charts { grid-template-columns: 1fr; } }
</style>
