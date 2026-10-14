<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboardOverviewAPI, type DashboardOverview } from '@/apis/dashboard'
import { getP0, type P0Row } from '@/apis/p0'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const overview = ref<DashboardOverview | null>(null)
const finance = ref<Record<string, unknown>>({})

const commissionAccounts = computed<P0Row[]>(() => (finance.value.accounts as P0Row[]) || [])
const summary = computed<P0Row>(() => (finance.value.summary as P0Row) || {})
const withdrawStats = computed<P0Row>(() => (finance.value.withdrawStats as P0Row) || {})

async function load() {
  loading.value = true
  try {
    overview.value = (await getDashboardOverviewAPI()).data
    finance.value = (await getP0<Record<string, unknown>>('finance')).data as Record<string, unknown>
  } catch {
    ElMessage.error('资金数据加载失败')
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
  <main class="fund-overview" v-loading="loading">
    <header class="head">
      <div>
        <small>财务中心</small>
        <h1>资金管理</h1>
        <p>查看平台总交易资金与佣金账户规模；金额均为当前数据库真实汇总。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <section class="cards">
      <el-card shadow="never" class="metric"><span>平台累计交易资金</span><strong>{{ overview ? money(overview.totalPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计退款</span><strong>{{ overview ? money(overview.totalRefundedAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>累计净收入</span><strong>{{ overview ? money(overview.totalNetPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>今日交易资金</span><strong>{{ overview ? money(overview.todayPaidAmount) : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>订单总数</span><strong>{{ overview ? overview.orderCount : '—' }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>待发货订单</span><strong>{{ overview ? overview.pendingDeliveryCount : '—' }}</strong></el-card>
    </section>
    <el-card shadow="never" class="section">
      <template #header><strong>佣金与提现概览</strong></template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="提现申请总数">{{ withdrawStats.count ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="待审核">{{ withdrawStats.pending ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="已通过">{{ withdrawStats.approved ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="已拒绝">{{ withdrawStats.rejected ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        :title="Object.keys(summary).length ? JSON.stringify(summary) : '暂无佣金汇总数据'"
        type="info"
        :closable="false"
        class="summary-note"
      />
    </el-card>
    <el-card shadow="never" class="section">
      <template #header><strong>会员佣金账户</strong></template>
      <el-table :data="commissionAccounts" stripe empty-text="暂无佣金账户">
        <el-table-column prop="member_id" label="会员 ID" width="100" />
        <el-table-column prop="username" label="会员账号" min-width="150" />
        <el-table-column label="佣金总金额" width="140"><template #default="{ row }">{{ money(row.total_earnings) }}</template></el-table-column>
        <el-table-column label="可用佣金" width="140"><template #default="{ row }">{{ money(row.available_earnings) }}</template></el-table-column>
        <el-table-column label="冻结佣金" width="140"><template #default="{ row }">{{ money(row.frozen_earnings) }}</template></el-table-column>
        <el-table-column label="已提现佣金" width="140"><template #default="{ row }">{{ money(row.withdrawn) }}</template></el-table-column>
      </el-table>
    </el-card>
  </main>
</template>

<style scoped>
.fund-overview { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric span { display: block; color: #75847b; font-size: 13px; }
.metric strong { display: block; margin-top: 10px; color: #214a34; font-size: 24px; }
.section { margin-bottom: 18px; }
.summary-note { margin-top: 12px; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, 1fr); } }
</style>
