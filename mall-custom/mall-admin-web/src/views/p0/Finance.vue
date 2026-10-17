<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const route = useRoute()
const error = ref('')
const tab = ref('withdraws')
const summary = ref<Record<string, unknown>>({})
const withdrawStats = ref<Record<string, unknown>>({})
const commissionByType = ref<P0Row[]>([])
const accounts = ref<P0Row[]>([])
const withdraws = ref<P0Row[]>([])
const ledger = ref<P0Row[]>([])

const statusText: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已完成', 3: '已完成', 4: '已拒绝' }
const changeTypeText: Record<string, string> = {
  referral_reward: '直推奖励',
  dividend: '每日分红',
  share_bonus: '分享下单奖励',
  simulate_reward: '后台模拟发放',
  withdraw_reject: '提现被拒退回',
  withdraw_done: '提现完成扣减',
}

const withdrawRows = computed<(P0Row & { statusLabel: string })[]>(() => withdraws.value.map(row => ({
  ...row,
  statusLabel: statusText[Number(row.status)] ?? '未知',
})))
const withdrawFilter = ref('all')
const filteredWithdrawRows = computed(() => withdrawFilter.value === 'all' ? withdrawRows.value
  : withdrawRows.value.filter(row => withdrawFilter.value === '2'
    ? [2, 3].includes(Number(row.status)) : Number(row.status) === Number(withdrawFilter.value)))

function money(value: unknown): string {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getP0<Record<string, unknown>>('finance')
    const data = res.data ?? {}
    summary.value = (data.summary as Record<string, unknown>) || {}
    withdrawStats.value = (data.withdrawStats as Record<string, unknown>) || {}
    commissionByType.value = (data.commissionByType as P0Row[]) || []
    accounts.value = (data.accounts as P0Row[]) || []
    withdraws.value = (data.withdraws as P0Row[]) || []
    ledger.value = (data.ledger as P0Row[]) || []
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

async function review(row: P0Row, approve: boolean) {
  const action = approve ? '通过' : '拒绝'
  try {
    const { value } = await ElMessageBox.prompt(
      `确定${action}会员「${row.username}」的提现申请（金额 ${money(row.amount)} 元）？请填写审核备注：`,
      `${action}提现申请`,
      { inputPattern: /^.{3,200}$/, inputErrorMessage: '备注需 3-200 字', inputValue: approve ? '核对无误，同意提现' : '资料不符，驳回' },
    )
    await postP0(`finance/withdraws/${row.id}/review`, { approve, remark: value })
    ElMessage.success(`已${action}`)
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`操作失败：${e instanceof Error ? e.message : String(e)}`)
  }
}

async function complete(row: P0Row) {
  try {
    await ElMessageBox.confirm(
      `确认将该笔提现标记为「已完成打款」（模拟）？金额 ${money(row.amount)} 元。`,
      '模拟完成打款', { type: 'warning' },
    )
    await postP0(`finance/withdraws/${row.id}/simulate-complete`, {})
    ElMessage.success('已完成（模拟）')
    await load()
  } catch { /* 用户取消 */ }
}

onMounted(load)
watch(() => route.meta.section, section => {
  if (typeof section === 'string' && ['withdraws', 'accounts', 'ledger', 'byType'].includes(section)) tab.value = section
}, { immediate: true })
</script>
<template>
  <main class="finance" v-loading="loading">
    <header>
      <div>
        <h1>佣金与提现</h1>
        <p>会员佣金余额、提现审核与记录</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />

    <section class="cards">
      <el-card shadow="never"><div class="kpi"><span>累计佣金</span><strong>{{ money(summary.total_commission) }}</strong></div></el-card>
      <el-card shadow="never"><div class="kpi"><span>可提现佣金</span><strong>{{ money(summary.available_commission) }}</strong></div></el-card>
      <el-card shadow="never"><div class="kpi"><span>冻结中</span><strong>{{ money(summary.frozen_commission) }}</strong></div></el-card>
      <el-card shadow="never"><div class="kpi"><span>已提现佣金</span><strong>{{ money(summary.withdrawn_commission) }}</strong></div></el-card>
      <el-card shadow="never"><div class="kpi"><span>待审核提现</span><strong>{{ withdrawStats.pending ?? 0 }}</strong></div></el-card>
      <el-card shadow="never"><div class="kpi"><span>已完成提现实到金额</span><strong>{{ money(withdrawStats.paid_out) }}</strong></div></el-card>
    </section>

    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="提现审核" name="withdraws">
          <el-select v-model="withdrawFilter" style="width:160px;margin-bottom:12px"><el-option label="全部状态" value="all" /><el-option label="待审核" value="0" /><el-option label="已通过" value="1" /><el-option label="已完成" value="2" /><el-option label="已拒绝" value="4" /></el-select>
          <el-table :data="filteredWithdrawRows" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="会员" width="140" />
            <el-table-column label="申请金额" width="120"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="手续费" width="110"><template #default="{ row }">{{ money(row.fee_amount) }}</template></el-table-column>
            <el-table-column label="实到金额" width="120"><template #default="{ row }">{{ money(row.actual_amount) }}</template></el-table-column>
            <el-table-column prop="statusLabel" label="状态" width="100" />
            <el-table-column prop="audit_remark" label="审核备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="申请时间" min-width="170"><template #default="{ row }">{{ formatDateTime(String(row.apply_time || '')) }}</template></el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-if="Number(row.status) === 0" size="small" type="primary" @click="review(row, true)">通过</el-button>
                <el-button v-if="Number(row.status) === 0" size="small" type="danger" @click="review(row, false)">拒绝</el-button>
                <el-button v-if="Number(row.status) === 1" size="small" @click="complete(row)">模拟完成</el-button>
                <span v-if="Number(row.status) > 1" class="done">已处理</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!filteredWithdrawRows.length && !loading" description="当前状态暂无提现申请" />
        </el-tab-pane>

        <el-tab-pane label="会员佣金账户" name="accounts">
          <el-table :data="accounts" stripe>
            <el-table-column prop="member_id" label="会员ID" width="100" />
            <el-table-column prop="username" label="会员" width="160" />
            <el-table-column label="累计佣金" width="140"><template #default="{ row }">{{ money(row.total_earnings) }}</template></el-table-column>
            <el-table-column label="可提现" width="140"><template #default="{ row }">{{ money(row.available_earnings) }}</template></el-table-column>
            <el-table-column label="冻结中" width="140"><template #default="{ row }">{{ money(row.frozen_earnings) }}</template></el-table-column>
            <el-table-column label="已提现" width="140"><template #default="{ row }">{{ money(row.withdrawn) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!accounts.length && !loading" description="暂无佣金账户" />
        </el-tab-pane>

        <el-tab-pane label="佣金明细" name="ledger">
          <el-table :data="ledger" stripe>
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="member_id" label="会员ID" width="100" />
            <el-table-column label="类型" width="150">
              <template #default="{ row }">{{ changeTypeText[String(row.change_type)] ?? row.change_type }}</template>
            </el-table-column>
            <el-table-column label="金额" width="130"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="余额" width="130"><template #default="{ row }">{{ money(row.balance_after) }}</template></el-table-column>
            <el-table-column prop="biz_id" label="业务单号" min-width="140" />
            <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
            <el-table-column label="时间" min-width="170"><template #default="{ row }">{{ formatDateTime(String(row.create_time || '')) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!ledger.length && !loading" description="暂无佣金流水" />
        </el-tab-pane>

        <el-tab-pane label="佣金来源构成" name="byType">
          <el-table :data="commissionByType" stripe>
            <el-table-column label="来源" min-width="180">
              <template #default="{ row }">{{ changeTypeText[String(row.change_type)] ?? row.change_type }}</template>
            </el-table-column>
            <el-table-column prop="entries" label="笔数" width="120" />
            <el-table-column label="金额合计" width="160"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
          </el-table>
          <el-empty v-if="!commissionByType.length && !loading" description="暂无数据" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </main>
</template>
<style scoped>
.finance{padding:28px;background:#f2f7f7;min-height:100vh}
header{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;gap:16px}
h1{color:#285a62;margin:0 0 8px}
p{color:#698789;margin:0}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin-bottom:18px}
.kpi{display:flex;flex-direction:column;gap:8px}
.kpi span{color:#698789;font-size:13px}
.kpi strong{color:#285a62;font-size:22px}
.done{color:#909399;font-size:13px}
</style>

