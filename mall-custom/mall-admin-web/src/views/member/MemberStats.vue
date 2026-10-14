<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getDashboardOverviewAPI, type DashboardOverview } from '@/apis/dashboard'
import { ElMessage } from 'element-plus'

use([CanvasRenderer, BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

const loading = ref(false)
const overview = ref<DashboardOverview | null>(null)
const userTrend = computed(() => (overview.value ? overview.value.userTrend : []))
const regionData = computed(() => (overview.value ? overview.value.memberRegionStats : []))
const genderData = computed(() => (overview.value ? overview.value.memberGenderStats : []))

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['总用户', '活跃用户', '新增用户'] },
  grid: { left: 56, right: 24, bottom: 32 },
  xAxis: { type: 'category', data: userTrend.value.map(item => item.day) },
  yAxis: { type: 'value', name: '人数' },
  series: [
    { name: '总用户', type: 'line', smooth: true, data: userTrend.value.map(item => item.totalUser) },
    { name: '活跃用户', type: 'line', smooth: true, data: userTrend.value.map(item => item.activeUser) },
    { name: '新增用户', type: 'line', smooth: true, data: userTrend.value.map(item => item.newUser) },
  ],
}))
const regionOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 56, right: 24, bottom: 56 },
  xAxis: { type: 'category', data: regionData.value.map(item => item.name), axisLabel: { interval: 0, rotate: 30 } },
  yAxis: { type: 'value', name: '人数' },
  series: [{ name: '用户数', type: 'bar', barMaxWidth: 36, data: regionData.value.map(item => item.cnt), itemStyle: { color: '#719680' } }],
}))
const genderOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      name: '性别分布',
      type: 'pie',
      radius: ['40%', '66%'],
      data: genderData.value.map(item => ({ name: item.name, value: item.cnt })),
    },
  ],
}))

async function refresh() {
  loading.value = true
  try {
    overview.value = (await getDashboardOverviewAPI()).data
  } catch {
    overview.value = null
    ElMessage.error('会员统计加载失败，请检查后台接口与权限')
  } finally {
    loading.value = false
  }
}
onMounted(refresh)
</script>

<template>
  <main class="member-stats" v-loading="loading">
    <header class="head">
      <div>
        <small>会员</small>
        <h1>会员统计</h1>
        <p>数字统计与图表统计均取自当前数据库真实会员与订单数据。</p>
      </div>
      <el-button @click="refresh">刷新</el-button>
    </header>
    <el-card shadow="never" class="section">
      <template #header><strong>数字统计</strong></template>
      <div class="cards">
        <div class="metric"><span>会员总数</span><strong>{{ overview ? overview.totalUser : '—' }}</strong></div>
        <div class="metric"><span>今日新增会员</span><strong>{{ overview ? overview.todayNewMemberCount : '—' }}</strong></div>
        <div class="metric"><span>近 30 天新增会员</span><strong>{{ overview ? overview.newUserCount : '—' }}</strong></div>
        <div class="metric"><span>近 30 天活跃会员</span><strong>{{ overview ? overview.activeUserCount : '—' }}</strong></div>
        <div class="metric"><span>今日支付金额</span><strong>￥{{ overview ? overview.todayPaidAmount.toFixed(2) : '—' }}</strong></div>
        <div class="metric"><span>会员累计消费</span><strong>￥{{ overview ? overview.totalPaidAmount.toFixed(2) : '—' }}</strong></div>
      </div>
      <el-alert
        :title="overview ? overview.userMetricNote : ''"
        type="info"
        :closable="false"
      />
      <el-alert
        title="会员所在区域与男女比例来自会员资料字段；未采集时显示为空，不做推算。"
        type="warning"
        :closable="false"
      />
    </el-card>
    <section class="charts">
      <el-card shadow="never">
        <template #header><strong>近 14 日用户趋势（折线图）</strong></template>
        <v-chart v-if="userTrend.length" :option="trendOption" autoresize class="chart" />
        <el-empty v-else description="暂无用户趋势数据" />
      </el-card>
      <el-card shadow="never">
        <template #header><strong>会员区域分布（柱状图）</strong></template>
        <v-chart v-if="regionData.length" :option="regionOption" autoresize class="chart" />
        <el-empty v-else description="暂无会员区域数据" />
      </el-card>
      <el-card shadow="never">
        <template #header><strong>会员性别比例（饼图）</strong></template>
        <v-chart v-if="genderData.length" :option="genderOption" autoresize class="chart" />
        <el-empty v-else description="暂无性别数据" />
      </el-card>
    </section>
  </main>
</template>

<style scoped>
.member-stats { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.section { margin-bottom: 18px; }
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 14px; }
.metric { background: #f3f7f4; border-radius: 10px; padding: 14px; }
.metric span { display: block; color: #75847b; font-size: 13px; }
.metric strong { display: block; margin-top: 10px; color: #214a34; font-size: 24px; }
.charts { display: grid; grid-template-columns: 1.4fr 1fr 1fr; gap: 16px; }
.chart { height: 300px; }
@media (max-width: 1200px) { .charts { grid-template-columns: 1fr 1fr; } }
@media (max-width: 900px) { .cards { grid-template-columns: repeat(2, 1fr); } .charts { grid-template-columns: 1fr; } }
</style>
