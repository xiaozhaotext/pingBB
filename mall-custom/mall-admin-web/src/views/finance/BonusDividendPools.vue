<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

type PoolCode = 'PARTNER' | 'COFOUNDER' | 'OPERATIONS'
type Pool = { pool_code: PoolCode; pool_balance: number; total_in: number; total_out: number; update_time: string }
type Entry = { id: number; pool_code: PoolCode; change_amount: number; balance_after: number; source_type: string; source_id: number; receiver_member_id?: number; receiver_username?: string; receiver_identity?: string; remark?: string; create_time: string }
type Legacy = { id: number; member_id: number; username: string; stat_date: string; shares: number; dividend_amount: number; create_time: string }
type PayoutRequest = { id: number; receiver_member_id: number; receiver_username: string; amount: number; reason: string; status: string; created_by: number; reviewed_by?: number; review_remark?: string; create_time: string; review_time?: string }
type Response = { legacyPool?: { pool_balance: number }; pools: Pool[]; ledger: Entry[]; ledgerTotal: number; payouts: Entry[]; payoutTotal: number; legacyDistributions: Legacy[]; payoutRequests: PayoutRequest[] }

const route = useRoute()
const code = computed(() => (route.meta.poolCode || 'PARTNER') as PoolCode)
const names: Record<PoolCode, string> = { PARTNER: '合伙人池', COFOUNDER: '联创池', OPERATIONS: '运营池' }
const data = ref<Response>({ pools: [], ledger: [], ledgerTotal: 0, payouts: [], payoutTotal: 0, legacyDistributions: [], payoutRequests: [] })
const loading = ref(false)
const error = ref('')
const page = ref(1)
const payoutPage = ref(1)
const pool = computed(() => data.value.pools.find(item => item.pool_code === code.value))
const legacy = computed(() => data.value.legacyDistributions.filter(item =>
  code.value === 'PARTNER' ? item.shares === 1 : code.value === 'COFOUNDER' ? item.shares === 3 : false))
const money = (value: number | undefined) => value == null ? '—' : `¥${Number(value).toFixed(2)}`
const requestForm = ref({ memberId: '', amount: '', reason: '' })
const submitting = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
let requestId = 0

async function load() {
  const current = ++requestId
  loading.value = true
  try {
    const result = await getP0<Response>('bonus-pools', { poolCode: code.value, page: page.value, payoutPage: payoutPage.value })
    if (current === requestId) { data.value = result.data; error.value = '' }
  } catch {
    if (current === requestId) error.value = '奖金池数据加载失败，请检查接口与数据表'
  } finally {
    if (current === requestId) loading.value = false
  }
}
async function submitRequest() {
  const memberId = Number(requestForm.value.memberId)
  const amount = Number(requestForm.value.amount)
  if (!Number.isInteger(memberId) || memberId <= 0 || !Number.isFinite(amount) || amount <= 0 || !requestForm.value.reason.trim()) {
    ElMessage.warning('请填写有效会员 ID、金额和申请原因')
    return
  }
  submitting.value = true
  try {
    await postP0('bonus-pools/operations/requests', { memberId, amount: requestForm.value.amount, reason: requestForm.value.reason.trim() })
    requestForm.value = { memberId: '', amount: '', reason: '' }
    ElMessage.success('已提交，待后台审核')
    await load()
  } catch { ElMessage.error('提交失败，请检查会员与金额') }
  finally { submitting.value = false }
}
async function reviewRequest(item: PayoutRequest, approve: boolean) {
  try {
    const result = await ElMessageBox.prompt(
      approve ? `确认将 ${money(item.amount)} 模拟发放给会员 ${item.receiver_username}（ID ${item.receiver_member_id}）？请输入审核说明。` : '请输入驳回原因',
      approve ? '审核发放' : '驳回申请',
      { inputValidator: value => value.trim().length >= 3 || '审核说明至少 3 个字' },
    )
    await postP0(`bonus-pools/operations/requests/${item.id}/review`, { approve, remark: result.value.trim() })
    ElMessage.success(approve ? '已审核并模拟入账' : '已驳回')
    await load()
  } catch { /* 用户取消或接口提示已显示 */ }
}
watch(code, () => { page.value = 1; payoutPage.value = 1; load() }, { immediate: true })
timer = setInterval(load, 15000)
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="bonus-pools">
    <div class="heading"><div><h2>{{ names[code] }}</h2><p>每 15 秒刷新，记录以数据库账本为准。</p></div><el-button :loading="loading" @click="load">立即刷新</el-button></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert :title="`旧总池余额 ${money(data.legacyPool?.pool_balance)}；既有历史资金未自动划入三个新池。`" type="info" show-icon :closable="false" />
    <div class="cards">
      <div><span>当前池余额</span><strong>{{ money(pool?.pool_balance) }}</strong></div>
      <div><span>累计划入</span><strong>{{ money(pool?.total_in) }}</strong></div>
      <div><span>累计已发放</span><strong>{{ money(pool?.total_out) }}</strong></div>
    </div>
    <el-card v-if="code !== 'OPERATIONS'" shadow="never" class="section">
      <template #header>个人累计分红上限</template>
      <el-table v-if="code === 'PARTNER'" :data="[
        { identity: '会员', referrals: '0–2 人', shares: '1–3 份', cap: 1200 },
        { identity: '合伙人', referrals: '3–5 人', shares: '4–6 份', cap: 8000 },
        { identity: '合伙人', referrals: '6–8 人', shares: '7–9 份', cap: 16000 },
        { identity: '联创', referrals: '9 人及以上', shares: '10 份起', cap: 24000 },
      ]" border>
        <el-table-column prop="identity" label="身份" min-width="100" />
        <el-table-column prop="referrals" label="有效直邀会员" min-width="140" />
        <el-table-column prop="shares" label="持有份数" min-width="100" />
        <el-table-column label="个人累计上限" min-width="150"><template #default="{ row }">{{ money(row.cap) }}</template></el-table-column>
      </el-table>
      <el-table v-else :data="[{ identity: '联创', referrals: '9 人及以上', shares: '1 份', cap: 1200 }]" border>
        <el-table-column prop="identity" label="身份" min-width="100" />
        <el-table-column prop="referrals" label="有效直邀会员" min-width="140" />
        <el-table-column prop="shares" label="持有份数" min-width="100" />
        <el-table-column label="个人累计上限" min-width="150"><template #default="{ row }">{{ money(row.cap) }}</template></el-table-column>
      </el-table>
      <p class="limit-note">合伙人池份数＝本人 1 份＋每位有效直邀会员 1 份。上限按每人、每池累计领取金额计算，不限制分红池总余额；联创两池个人累计上限合计 ¥25,200.00。</p>
    </el-card>
    <el-card v-if="code === 'OPERATIONS'" shadow="never" class="section">
      <template #header>运营池指定领取人（审核后模拟入账）</template>
      <el-form :model="requestForm" inline>
        <el-form-item label="会员 ID"><el-input v-model="requestForm.memberId" placeholder="领取人会员 ID" /></el-form-item>
        <el-form-item label="金额"><el-input v-model="requestForm.amount" placeholder="金额（元）" /></el-form-item>
        <el-form-item label="申请原因"><el-input v-model="requestForm.reason" placeholder="说明用途" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="submitting" @click="submitRequest">提交审核</el-button></el-form-item>
      </el-form>
      <el-table :data="data.payoutRequests" empty-text="暂无运营池发放申请" border>
        <el-table-column prop="create_time" label="申请时间" min-width="170" />
        <el-table-column prop="receiver_username" label="领取人" min-width="120" />
        <el-table-column prop="receiver_member_id" label="会员 ID" min-width="90" />
        <el-table-column label="金额" min-width="110"><template #default="{ row }">{{ money(Number(row.amount)) }}</template></el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" />
        <el-table-column prop="status" label="状态" min-width="100" />
        <el-table-column prop="reviewed_by" label="审核人 ID" min-width="100" />
        <el-table-column prop="review_remark" label="审核说明" min-width="150" />
        <el-table-column label="操作" min-width="160" fixed="right"><template #default="{ row }"><template v-if="row.status === 'PENDING'"><el-button link type="primary" @click="reviewRequest(row, true)">审核发放</el-button><el-button link type="danger" @click="reviewRequest(row, false)">驳回</el-button></template></template></el-table-column>
      </el-table>
    </el-card>
    <el-card shadow="never" class="section">
      <template #header>取走明细（已发放分红）</template>
      <el-table :data="data.payouts" empty-text="当前池暂无已发放记录" border>
        <el-table-column prop="create_time" label="发放时间" min-width="170" />
        <el-table-column prop="receiver_member_id" label="会员 ID" min-width="100" />
        <el-table-column prop="receiver_username" label="领取人" min-width="130" />
        <el-table-column prop="receiver_identity" label="发放时身份" min-width="120" />
        <el-table-column label="取走金额" min-width="120"><template #default="{ row }">{{ money(-Number(row.change_amount)) }}</template></el-table-column>
        <el-table-column prop="source_id" label="关联发放单" min-width="120" />
        <el-table-column prop="remark" label="说明" min-width="180" />
      </el-table>
      <el-pagination v-if="data.payoutTotal > 30" v-model:current-page="payoutPage" layout="prev, pager, next, total" :page-size="30" :total="data.payoutTotal" @current-change="load" />
    </el-card>
    <el-card shadow="never" class="section">
      <template #header>资金变动明细</template>
      <el-table :data="data.ledger" empty-text="暂无资金变动" border>
        <el-table-column prop="create_time" label="时间" min-width="170" />
        <el-table-column prop="source_type" label="来源/用途" min-width="180" />
        <el-table-column prop="source_id" label="关联单号" min-width="120" />
        <el-table-column label="变动金额" min-width="120"><template #default="{ row }">{{ money(Number(row.change_amount)) }}</template></el-table-column>
        <el-table-column label="变动后余额" min-width="120"><template #default="{ row }">{{ money(Number(row.balance_after)) }}</template></el-table-column>
        <el-table-column prop="remark" label="说明" min-width="220" />
      </el-table>
      <el-pagination v-if="data.ledgerTotal > 30" v-model:current-page="page" layout="prev, pager, next, total" :page-size="30" :total="data.ledgerTotal" @current-change="load" />
    </el-card>
    <el-card v-if="code !== 'OPERATIONS'" shadow="never" class="section">
      <template #header>旧单池历史发放（不计入当前池余额）</template>
      <el-table :data="legacy" empty-text="暂无历史发放记录" border>
        <el-table-column prop="create_time" label="发放时间" min-width="170" />
        <el-table-column prop="stat_date" label="归属日期" min-width="130" />
        <el-table-column prop="member_id" label="会员 ID" min-width="100" />
        <el-table-column prop="username" label="领取人" min-width="140" />
        <el-table-column label="按份额推断身份" min-width="140"><template #default>{{ names[code] }}</template></el-table-column>
        <el-table-column label="取走金额" min-width="120"><template #default="{ row }">{{ money(Number(row.dividend_amount)) }}</template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.bonus-pools{padding:22px}.heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px}.heading h2{margin:0 0 6px;color:#245660}.heading p{margin:0;color:#7b8d91;font-size:13px}.cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:18px 0}.cards>div{background:#fff;border:1px solid #e5ecec;border-radius:12px;padding:20px}.cards span{display:block;color:#788b90;font-size:13px}.cards strong{display:block;margin-top:12px;color:#21777e;font-size:25px}.section{margin-top:18px}.limit-note{margin:14px 0 0;color:#7b8d91;font-size:13px}@media(max-width:800px){.cards{grid-template-columns:1fr}}
</style>
