<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getDashboardOverviewAPI, type DashboardOverview } from '@/apis/dashboard'

use([CanvasRenderer, LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const overview = ref<DashboardOverview | null>(null)
const cards = computed(() => [
  { title: '商品总数', value: overview.value?.productCount ?? '—' },
  { title: '在售商品', value: overview.value?.productOnSaleCount ?? '—' },
  { title: '已下架商品', value: overview.value?.productOffSaleCount ?? '—' },
  { title: '库存预警SKU', value: overview.value?.lowStockSkuCount ?? '—' },
  { title: '订单总数', value: overview.value?.orderCount ?? '—' },
  { title: '待发货订单', value: overview.value?.pendingDeliveryCount ?? '—' },
  { title: '总用户数', value: overview.value?.totalUser ?? '—' },
  { title: '活跃用户(近30日)', value: overview.value?.activeUserCount ?? '—' },
  { title: '新增用户(近30日)', value: overview.value?.newUserCount ?? '—' },
  { title: '今日新增用户', value: overview.value?.todayNewMemberCount ?? '—' },
  { title: '今日订单', value: overview.value?.todayOrderCount ?? '—' },
  { title: '今日已支付金额', value: overview.value ? `¥${Number(overview.value.todayPaidAmount).toFixed(2)}` : '—' },
  { title: '累计已支付金额', value: overview.value ? `¥${Number(overview.value.totalPaidAmount).toFixed(2)}` : '—' },
  { title: '累计退款金额', value: overview.value ? `¥${Number(overview.value.totalRefundedAmount).toFixed(2)}` : '—' },
  { title: '累计净收入(已扣退款)', value: overview.value ? `¥${Number(overview.value.totalNetPaidAmount).toFixed(2)}` : '—' },
])
const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['已支付订单数', '已支付金额'] },
  grid: { left: 52, right: 64, bottom: 28 },
  xAxis: { type: 'category', data: overview.value?.paidTrend.map(item => item.day) ?? [] },
  yAxis: [{ type: 'value', name: '订单数' }, { type: 'value', name: '金额（元）' }],
  series: [
    { name: '已支付订单数', type: 'line', data: overview.value?.paidTrend.map(item => item.orderCount) ?? [], smooth: true },
    { name: '已支付金额', type: 'line', yAxisIndex: 1, data: overview.value?.paidTrend.map(item => item.paidAmount) ?? [], smooth: true },
  ],
}))
const userTrend = computed(() => overview.value?.userTrend ?? [])
const regionData = computed(() => overview.value?.memberRegionStats ?? [])
const genderData = computed(() => overview.value?.memberGenderStats ?? [])
const userChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['总用户', '活跃用户', '新增用户'] },
  grid: { left: 52, right: 24, bottom: 28 },
  xAxis: { type: 'category', data: userTrend.value.map(item => item.day) },
  yAxis: { type: 'value', name: '人数' },
  series: [
    { name: '总用户', type: 'line', data: userTrend.value.map(item => item.totalUser), smooth: true },
    { name: '活跃用户', type: 'line', data: userTrend.value.map(item => item.activeUser), smooth: true },
    { name: '新增用户', type: 'line', data: userTrend.value.map(item => item.newUser), smooth: true },
  ],
}))
const regionOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 52, right: 24, bottom: 40 },
  xAxis: { type: 'category', data: regionData.value.map(item => item.name), axisLabel: { interval: 0, rotate: 30 } },
  yAxis: { type: 'value', name: '人数' },
  series: [{ name: '用户数', type: 'bar', barMaxWidth: 36, data: regionData.value.map(item => item.cnt), itemStyle: { color: '#719680' } }],
}))
const genderOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    name: '性别分布',
    type: 'pie',
    radius: ['40%', '66%'],
    data: genderData.value.map(item => ({ name: item.name, value: item.cnt })),
  }],
}))

async function refresh() {
  loading.value = true
  try {
    const response = await getDashboardOverviewAPI()
    overview.value = response.data
  } catch {
    overview.value = null
    ElMessage.error('真实统计加载失败，请检查后台接口与权限')
  } finally {
    loading.value = false
  }
}

const sandboxMode = import.meta.env.VITE_SANDBOX_MODE === 'true'
onMounted(refresh)
</script>

<template>
  <main class="dashboard" v-loading="loading">
    <header class="heading"><div><p>{{ sandboxMode ? '智美游 · 演示环境' : '智美游 · 管理后台' }}</p><h1>经营概览</h1><span class="heading-note">让每一份经营清晰有序</span></div><el-button @click="refresh">刷新数据</el-button></header>
    <el-alert title="统计来自当前连接的数据库；历史示例数据可能影响汇总，请核对订单及资金流水后用于经营决策。" type="warning" :closable="false" show-icon />
    <section class="cards"><el-card v-for="card in cards" :key="card.title" shadow="never" class="metric"><span>{{ card.title }}</span><strong>{{ card.value }}</strong></el-card></section>
    <el-card shadow="never" class="trend"><template #header><div class="trend-head"><strong>最近 7 日已支付订单趋势</strong><small>{{ overview?.amountNote }}</small></div></template>
      <v-chart v-if="overview?.paidTrend.length" :option="chartOption" autoresize class="chart" />
      <el-empty v-else description="最近 7 日没有已支付订单数据" />
    </el-card>
    <section class="user-grid">
      <el-card shadow="never" class="user-chart">
        <template #header><div class="trend-head"><strong>近 14 日用户趋势</strong><small>{{ overview?.userMetricNote }}</small></div></template>
        <v-chart v-if="userTrend.length" :option="userChartOption" autoresize class="chart" />
        <el-empty v-else description="暂无用户趋势数据" />
      </el-card>
      <el-card shadow="never" class="user-chart">
        <template #header><strong>用户区域分布</strong></template>
        <v-chart v-if="regionData.length" :option="regionOption" autoresize class="chart" />
        <el-empty v-else description="暂无区域数据" />
      </el-card>
      <el-card shadow="never" class="user-chart">
        <template #header><strong>用户性别分布</strong></template>
        <v-chart v-if="genderData.length" :option="genderOption" autoresize class="chart" />
        <el-empty v-else description="暂无性别数据" />
      </el-card>
    </section>
  </main>
</template>

<style scoped>
.dashboard{padding:28px;background:#f6f7f4;min-height:100vh}.heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.heading p{color:#53765e;font-size:13px}.heading h1{font-size:28px;color:#234a34;margin:4px 0}.cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:20px 0}.metric span{display:block;color:#75847b;font-size:14px}.metric strong{display:block;margin-top:14px;color:#214a34;font-size:27px}.trend-head{display:flex;justify-content:space-between;gap:20px}.trend-head small{color:#7b8d81}.chart{height:330px}.user-grid{display:grid;grid-template-columns:1.4fr 1fr 1fr;gap:16px;margin-top:20px}.user-chart .chart{height:300px}@media(max-width:1200px){.user-grid{grid-template-columns:1fr 1fr}}@media(max-width:900px){.cards{grid-template-columns:repeat(2,1fr)}.user-grid{grid-template-columns:1fr}}
</style>

<style scoped>
.dashboard { background: #f7f3eb; }
.heading { padding: 28px 32px; min-height: 170px; border-radius: 22px; background: linear-gradient(90deg,#254236dc,#25423620),url('../../assets/images/travel-photo/coastal-retreat.jpg') center 55%/cover; box-shadow: 0 10px 30px #7b725313; }
.heading h1 { font-size: 32px; letter-spacing: 2px; }
.heading-note { display: block; margin-top: 12px; color: #697863; font-size: 14px; }
.metric { border-top: 3px solid #d3b275; }
.metric:nth-child(2n) { border-top-color: #719680; }
.metric strong { font-size: 32px; }
.trend { margin-top: 26px; }
@media(max-width:600px) { .heading { padding: 22px; } .heading h1 { font-size: 27px; } }
</style>

<style scoped>.heading p,.heading h1,.heading-note{color:#fff}.heading p{letter-spacing:2px;color:#ece1bd}.heading-note{color:#e2e9dc}.heading{min-height:190px}.metric{box-shadow:0 5px 20px #66553708!important}</style>
