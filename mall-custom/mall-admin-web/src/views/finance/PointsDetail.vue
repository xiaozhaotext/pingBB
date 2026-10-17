<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getP0, type P0Row } from '@/apis/p0'
import { formatDateTime } from '@/utils/datetime'
import { ElMessage } from 'element-plus'
import { businessText } from '@/utils/adminDisplay'
import { useLedgerDisplay } from '@/composables/useLedgerDisplay'
const { source } = useLedgerDisplay()

const route = useRoute()
const pointsType = computed(() => (route.name === 'financeRepurchasePoints' ? 2 : 1))
const title = computed(() => (pointsType.value === 2 ? '复购积分明细' : '产品积分明细'))
// 变动类型中英映射
const changeTypeText = (t: string) => {
  const map: Record<string, string> = {
    earn: '获得', consume: '消费', freeze: '冻结',
    unfreeze: '解冻', adjust: '调整', dividend: '分红',
  }
  return map[t] || t
}
const typeLabel = computed(() => (pointsType.value === 2 ? '复购积分' : '产品积分'))

const loading = ref(false)
const accounts = ref<P0Row[]>([])
const ledger = ref<P0Row[]>([])
const keyword = ref('')

const typeAccounts = computed(() => accounts.value.filter(row => Number(row.points_type) === pointsType.value))
const typeLedger = computed(() => {
  const rows = ledger.value.filter(row => Number(row.points_type) === pointsType.value)
  const key = keyword.value.trim()
  if (!key) return rows
  return rows.filter(row => String(row.member_id) === key || String(row.remark || '').includes(key))
})
const totalBalance = computed(() => typeAccounts.value.reduce((sum, row) => sum + Number(row.balance || 0), 0))
const totalFrozen = computed(() => typeAccounts.value.reduce((sum, row) => sum + Number(row.frozen || 0), 0))

async function load() {
  loading.value = true
  try {
    const data = (await getP0<Record<string, P0Row[]>>('points')).data
    accounts.value = data.accounts || []
    ledger.value = data.ledger || []
  } catch {
    ElMessage.error('积分明细加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <main class="points-detail" v-loading="loading">
    <header class="head">
      <div>
        <small>财务中心</small>
        <h1>{{ title }}</h1>
        <p>{{ typeLabel }}余额、冻结与全部变动流水，数据来自积分账本。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <section class="cards">
      <el-card shadow="never" class="metric"><span>持有会员数</span><strong>{{ typeAccounts.length }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>{{ typeLabel }}总余额</span><strong>{{ totalBalance }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>冻结总量</span><strong>{{ totalFrozen }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>流水条数</span><strong>{{ typeLedger.length }}</strong></el-card>
    </section>
    <el-card shadow="never" class="section">
      <template #header><strong>{{ typeLabel }}账户余额</strong></template>
      <el-table :data="typeAccounts" stripe empty-text="暂无账户">
        <el-table-column prop="member_id" label="会员 ID" width="100" />
        <el-table-column prop="username" label="会员账号" min-width="150" />
        <el-table-column prop="balance" label="总余额" width="120" />
        <el-table-column prop="frozen" label="冻结" width="120" />
        <el-table-column prop="available" label="可用" width="120" />
      </el-table>
    </el-card>
    <el-card shadow="never" class="section">
      <template #header>
        <div class="table-head"><strong>{{ typeLabel }}流水明细</strong><el-input v-model="keyword" placeholder="搜索会员 ID / 备注" clearable style="max-width: 260px" /></div>
      </template>
      <el-table :data="typeLedger" stripe empty-text="暂无流水">
        <el-table-column prop="id" label="流水号" width="90" />
        <el-table-column prop="member_id" label="会员 ID" width="100" />
        <el-table-column label="类型" width="120"><template #default="{ row }">{{ changeTypeText(String(row.change_type || '')) }}</template></el-table-column>
        <el-table-column prop="amount" label="变动" width="110" />
        <el-table-column prop="balance_after" label="变动后余额" width="120" />
        <el-table-column label="业务来源" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ source(row) }}</template></el-table-column>
        <el-table-column label="备注" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ businessText(row.remark) }}</template></el-table-column>
        <el-table-column label="时间" min-width="170"><template #default="{ row }">{{ formatDateTime(String(row.create_time || '')) }}</template></el-table-column>
      </el-table>
    </el-card>
  </main>
</template>

<style scoped>
.points-detail { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric span { display: block; color: #75847b; font-size: 13px; }
.metric strong { display: block; margin-top: 10px; color: #214a34; font-size: 24px; }
.section { margin-bottom: 18px; }
.table-head { display: flex; justify-content: space-between; align-items: center; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, 1fr); } }
</style>

