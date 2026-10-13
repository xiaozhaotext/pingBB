<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getP0, type P0Row } from '@/apis/p0'

const loading = ref(false)
const error = ref('')
const days = ref(30)
const totalVisits = ref(0)
const todayVisits = ref(0)
const todayVisitors = ref(0)
const daily = ref<P0Row[]>([])
const topPaths = ref<P0Row[]>([])
const recent = ref<P0Row[]>([])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getP0<Record<string, unknown>>('ops/visits', { days: days.value })
    const data = res.data
    totalVisits.value = Number(data.totalVisits || 0)
    todayVisits.value = Number(data.todayVisits || 0)
    todayVisitors.value = Number(data.todayVisitors || 0)
    daily.value = (data.daily as P0Row[]) || []
    topPaths.value = (data.topPaths as P0Row[]) || []
    recent.value = (data.recent as P0Row[]) || []
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
<template>
  <main class="visit-statistics" v-loading="loading">
    <header>
      <div><h1>访问统计</h1><p>应用端真实访问明细 · 独立访客按会员或 IP 去重</p></div>
      <div>
        <el-select v-model="days" style="width: 120px" @change="load">
          <el-option :value="7" label="近 7 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="cards">
      <el-card shadow="never"><div class="num">{{ totalVisits }}</div><div class="label">累计访问量</div></el-card>
      <el-card shadow="never"><div class="num">{{ todayVisits }}</div><div class="label">今日访问量</div></el-card>
      <el-card shadow="never"><div class="num">{{ todayVisitors }}</div><div class="label">今日独立访客</div></el-card>
    </div>
    <el-card shadow="never" class="block">
      <template #header>按天统计</template>
      <el-table :data="daily" stripe>
        <el-table-column prop="statDate" label="日期" min-width="140" />
        <el-table-column prop="client" label="端" width="90" />
        <el-table-column prop="visits" label="访问量" width="120" />
        <el-table-column prop="visitors" label="独立访客" width="120" />
      </el-table>
      <el-empty v-if="!daily.length" description="暂无访问数据" />
    </el-card>
    <el-card shadow="never" class="block">
      <template #header>热门页面</template>
      <el-table :data="topPaths" stripe>
        <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
        <el-table-column prop="visits" label="访问量" width="120" />
      </el-table>
      <el-empty v-if="!topPaths.length" description="暂无访问数据" />
    </el-card>
    <el-card shadow="never">
      <template #header>最近访问明细</template>
      <el-table :data="recent" stripe max-height="480">
        <el-table-column prop="create_time" label="时间" min-width="170" />
        <el-table-column prop="client" label="端" width="80" />
        <el-table-column prop="method" label="方法" width="90" />
        <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" min-width="130" />
        <el-table-column prop="member_id" label="会员ID" width="100" />
      </el-table>
      <el-empty v-if="!recent.length" description="暂无访问明细" />
    </el-card>
  </main>
</template>
<style scoped>
.visit-statistics{padding:28px;background:#f2f7f7;min-height:100vh}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-bottom:20px}.block{margin-bottom:20px}.num{font-size:28px;font-weight:600;color:#285a62}.label{color:#698789;font-size:13px;margin-top:6px}
</style>
