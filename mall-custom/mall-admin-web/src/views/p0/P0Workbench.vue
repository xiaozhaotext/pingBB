<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'

type SectionKey = 'p0Members' | 'p0Partner' | 'p0Cofounder' | 'p0Points' | 'p0Finance' | 'p0Dividend' | 'p0Settings'
const sections: Record<SectionKey, { title: string; path: string; note: string }> = {
  p0Members: { title: '会员管理', path: 'members', note: '会员身份变更写入操作审计，推荐关系只读展示。' },
  p0Partner: { title: '合伙人管理', path: 'partner', note: '权益方案与购买记录均来自测试数据库；模拟购买入口尚未开放给消费者。' },
  p0Cofounder: { title: '联创管理', path: 'cofounder', note: '所有金额与权益仅用于模拟展示，不产生真实分红。' },
  p0Points: { title: '双积分管理', path: 'points', note: '调整先申请后审核，余额和流水在同一数据库事务中更新。' },
  p0Finance: { title: '财务中心', path: 'finance', note: '提现审核仅改变模拟状态；审核通过不触发打款。' },
  p0Dividend: { title: '每日贡献奖励池', path: 'dividend', note: '展示数据库中的贡献、模拟快照和异常；自动核验与实际分红未启用。' },
  p0Settings: { title: '系统配置与日志', path: 'settings', note: '平台管理员可调整会员购买与直推奖励参数，保存前后值均写入审计。' },
}
const route = useRoute()
const sectionKey = computed(() => (typeof route.name === 'string' && route.name in sections ? route.name : 'p0Members') as SectionKey)
const section = computed(() => sections[sectionKey.value])
const loading = ref(false)
const data = ref<Record<string, unknown>>({})
const memberKeyword = ref('')
const memberId = ref<number | null>(null)
const drawerOpen = ref(false)
const memberDetail = ref<Record<string, unknown> | null>(null)
const identityType = ref(0)
const identityReason = ref('')
const pointsForm = ref({ memberId: 0, pointsType: 1, delta: 0, reason: '', idempotencyKey: '' })
const settingForm = ref({ key: 'site.title', value: '' })
const settingOptions = [
  { key: 'site.title', label: '站点标题' }, { key: 'site.notice', label: '站点公告' },
  { key: 'membership.partner.price', label: '合伙人价格（元）' },
  { key: 'membership.partner.product_points', label: '合伙人赠送产品积分' },
  { key: 'membership.partner.repurchase_points', label: '合伙人赠送复购积分' },
  { key: 'membership.cofounder.price', label: '联创价格（元）' },
  { key: 'membership.cofounder.product_points', label: '联创赠送产品积分' },
  { key: 'membership.cofounder.repurchase_points', label: '联创赠送复购积分' },
  { key: 'membership.referral.cash_rate', label: '直推现金奖励比例（0–1）' },
  { key: 'membership.referral.partner.cash_cap', label: '合伙人直推现金封顶（空为不封顶）' },
  { key: 'membership.referral.cofounder.cash_cap', label: '联创直推现金封顶（元）' },
  { key: 'membership.referral.partner.repurchase_points', label: '合伙人直推复购积分' },
  { key: 'membership.referral.cofounder.repurchase_points', label: '联创直推复购积分' },
]
const labels: Record<string, string> = {
  id: '编号', member_id: '会员编号', username: '账号', nickname: '昵称', phone: '手机', status: '状态',
  identity_type: '身份', inviter_id: '推荐人', points_type: '积分类型', balance: '余额', available: '可用',
  frozen: '冻结', amount: '金额', delta: '调整量', reason: '原因', pay_status: '模拟支付状态',
  simulate_flag: '模拟标记', actual_amount: '扣费后金额', fee_rate: '手续费比例', fee_amount: '手续费',
  contribution_value: '贡献值', estimated_amount: '模拟分配金额', ratio: '贡献占比', stat_date: '统计日期',
  create_time: '创建时间', apply_time: '申请时间', update_time: '更新时间',
}

async function load() {
  loading.value = true
  memberDetail.value = null
  try {
    const params = sectionKey.value === 'p0Members' ? { keyword: memberKeyword.value } : undefined
    const response = await getP0(section.value.path, params)
    const value = response.data
    data.value = Array.isArray(value) ? { members: value } : (value as Record<string, unknown>)
    if (sectionKey.value === 'p0Settings') selectSetting(settingForm.value.key)
  } catch {
    data.value = {}
  } finally {
    loading.value = false
  }
}
function selectSetting(key: string) {
  const current = rows(data.value.configs).find(row => row.config_key === key)
  settingForm.value.value = current?.config_value == null ? '' : String(current.config_value)
}
watch(sectionKey, load, { immediate: true })

function rows(value: unknown): P0Row[] {
  return Array.isArray(value) ? value as P0Row[] : []
}
function columns(value: unknown): string[] {
  const first = rows(value)[0]
  return first ? Object.keys(first).filter(key => !['idempotency_key', 'password'].includes(key)) : []
}
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

async function openMember(id: number) {
  loading.value = true
  try {
    const response = await getP0<Record<string, unknown>>(`members/${id}`)
    memberDetail.value = response.data
    memberId.value = id
    drawerOpen.value = true
    const current = rows(response.data.identity)[0]
    identityType.value = Number(current?.identity_type ?? 0)
    identityReason.value = ''
  } finally { loading.value = false }
}
async function changeIdentity() {
  if (!memberId.value || identityReason.value.trim().length < 5) return ElMessage.warning('请填写至少 5 个字的变更原因')
  await ElMessageBox.confirm('确认调整会员身份并写入审计日志？', '身份调整', { type: 'warning' })
  await postP0(`members/${memberId.value}/identity`, { identityType: identityType.value, reason: identityReason.value })
  ElMessage.success('身份已更新')
  await load()
  await openMember(memberId.value)
}
async function requestAdjustment() {
  const form = pointsForm.value
  if (!form.memberId || !form.delta || form.reason.trim().length < 5) return ElMessage.warning('请填写会员、调整量和原因')
  if (!form.idempotencyKey) form.idempotencyKey = crypto.randomUUID().replace(/-/g, '')
  await postP0('points/adjustments', { ...form })
  ElMessage.success('调整申请已记录，请审核')
  pointsForm.value = { memberId: 0, pointsType: 1, delta: 0, reason: '', idempotencyKey: '' }
  await load()
}
async function reviewAdjustment(id: number, approve: boolean) {
  await ElMessageBox.confirm(approve ? '确认审核通过？积分将实时记账。' : '确认拒绝此申请？', '积分审核', { type: 'warning' })
  await postP0(`points/adjustments/${id}/review`, { approve })
  ElMessage.success('审核已完成')
  await load()
}
async function reviewWithdraw(id: number, approve: boolean) {
  const { value } = await ElMessageBox.prompt('填写审核原因（至少 3 个字）。审核通过也不会真实打款。', '模拟提现审核', {
    inputValidator: text => text.trim().length >= 3 || '请填写至少 3 个字',
  })
  await postP0(`finance/withdraws/${id}/review`, { approve, remark: value })
  ElMessage.success('模拟审核状态已更新')
  await load()
}
async function completeSimulatedWithdraw(id: number) {
  await ElMessageBox.confirm('仅在模拟账本中完成提现，确认不会发生真实打款。继续？', '完成模拟提现', { type: 'warning' })
  await postP0(`finance/withdraws/${id}/simulate-complete`, {})
  ElMessage.success('模拟提现已记账')
  await load()
}
async function updateSetting() {
  if (!settingForm.value.value.trim() && settingForm.value.key !== 'membership.referral.partner.cash_cap') return ElMessage.warning('请输入设置内容')
  await postP0('settings', settingForm.value)
  ElMessage.success('配置已保存并记录审计')
  await load()
}
const groupTitles: Record<string, string> = {
  members: '会员列表', config: '权益方案', orders: '模拟购买记录', identities: '当前身份',
  accounts: '账户余额', ledger: '流水记录', adjustments: '积分调整审核', withdraws: '提现申请',
  contributions: '贡献事件', snapshots: '模拟分配历史', exceptions: '异常记录',
  configs: '系统参数', audit: '操作日志',
}
</script>

<template>
  <main class="p0-page" v-loading="loading">
    <div class="heading">
      <div><span class="eyebrow">PLATFORM OPERATIONS</span><h1>{{ section.title }}</h1><p>{{ section.note }}</p></div>
      <el-button @click="load">刷新真实数据</el-button>
    </div>
    <el-alert title="会员真实支付默认关闭；直推规则必须先配置。提现和分红仍为模拟，尚未真实打款。" type="warning" show-icon :closable="false" />

    <el-card v-if="sectionKey === 'p0Members'" class="action-card" shadow="never">
      <el-input v-model="memberKeyword" placeholder="按账号、昵称或手机号查找会员" clearable @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
    </el-card>
    <el-card v-if="sectionKey === 'p0Points'" class="action-card" shadow="never">
      <strong>提交积分调整申请</strong>
      <el-input-number v-model="pointsForm.memberId" :min="1" placeholder="会员 ID" />
      <el-select v-model="pointsForm.pointsType" style="width:125px"><el-option label="商品积分" :value="1" /><el-option label="复购积分" :value="2" /></el-select>
      <el-input-number v-model="pointsForm.delta" placeholder="正数增加 / 负数减少" />
      <el-input v-model="pointsForm.reason" placeholder="调整原因（至少 5 个字）" />
      <el-button type="primary" @click="requestAdjustment">提交待审核</el-button>
    </el-card>
    <el-card v-if="sectionKey === 'p0Settings'" class="action-card" shadow="never">
      <strong>平台参数</strong>
      <el-select v-model="settingForm.key" style="width:290px" @change="selectSetting"><el-option v-for="option in settingOptions" :key="option.key" :label="option.label" :value="option.key" /></el-select>
      <el-input v-model="settingForm.value" placeholder="配置值；积分填非负整数" />
      <el-button type="primary" @click="updateSetting">保存</el-button>
    </el-card>

    <section v-for="(value, key) in data" :key="key" class="data-section">
      <el-card v-if="Array.isArray(value)" shadow="never">
        <template #header><strong>{{ groupTitles[String(key)] || key }} <small>({{ rows(value).length }})</small></strong></template>
        <el-table v-if="rows(value).length" :data="rows(value)" stripe max-height="480">
          <el-table-column v-for="column in columns(value)" :key="column" :prop="column" :label="labels[column] || column" min-width="110" show-overflow-tooltip>
            <template #default="scope">{{ display(scope.row[column]) }}</template>
          </el-table-column>
          <el-table-column v-if="key === 'members'" label="操作" fixed="right" width="100"><template #default="scope"><el-button link type="primary" @click="openMember(Number(scope.row.id))">详情</el-button></template></el-table-column>
          <el-table-column v-if="key === 'adjustments'" label="审核" fixed="right" width="150"><template #default="scope"><template v-if="Number(scope.row.status) === 0"><el-button link type="primary" @click="reviewAdjustment(Number(scope.row.id), true)">通过</el-button><el-button link type="danger" @click="reviewAdjustment(Number(scope.row.id), false)">拒绝</el-button></template><span v-else>已处理</span></template></el-table-column>
          <el-table-column v-if="key === 'withdraws'" label="模拟审核" fixed="right" width="180"><template #default="scope"><template v-if="Number(scope.row.status) === 0"><el-button link type="primary" @click="reviewWithdraw(Number(scope.row.id), true)">通过</el-button><el-button link type="danger" @click="reviewWithdraw(Number(scope.row.id), false)">拒绝</el-button></template><el-button v-else-if="Number(scope.row.status) === 1" link type="primary" @click="completeSimulatedWithdraw(Number(scope.row.id))">完成模拟记账</el-button><span v-else>已处理</span></template></el-table-column>
        </el-table>
        <el-empty v-else description="数据库中暂无记录" />
      </el-card>
    </section>

    <el-drawer v-model="drawerOpen" title="会员详情与身份调整" size="520px" :modal="true" @closed="memberId = null">
      <template v-if="memberDetail">
        <el-descriptions :column="1" border><el-descriptions-item v-for="(value, key) in (memberDetail.profile as P0Row)" :key="key" :label="labels[String(key)] || String(key)">{{ display(value) }}</el-descriptions-item></el-descriptions>
        <h3>推荐关系</h3><p>推荐人：{{ display(rows(memberDetail.inviter)[0]?.inviter_name) }}</p><p>直接邀请：{{ rows(memberDetail.invitees).length }} 人</p>
        <h3>调整身份</h3>
        <el-select v-model="identityType" style="width:100%;margin-bottom:12px"><el-option label="普通会员" :value="0" /><el-option label="合伙人" :value="1" /><el-option label="联创" :value="2" /></el-select>
        <el-input v-model="identityReason" type="textarea" placeholder="必填：变更原因" />
        <el-button type="primary" style="margin-top:12px" @click="changeIdentity">保存并审计</el-button>
        <h3>身份变更记录</h3><el-table :data="rows(memberDetail.identityHistory)" max-height="260"><el-table-column prop="from_type" label="原身份" /><el-table-column prop="to_type" label="新身份" /><el-table-column prop="reason" label="原因" /></el-table>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.p0-page{min-height:100vh;padding:28px;background:#f5f7f5}.heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.heading h1{margin:4px 0;font-size:28px;color:#203a32}.heading p{margin:0;color:#61746b}.eyebrow{font-size:11px;letter-spacing:.2em;color:#478264}.action-card{margin-top:20px}.action-card :deep(.el-card__body){display:flex;align-items:center;gap:10px;flex-wrap:wrap}.action-card :deep(.el-input){max-width:300px}.data-section{margin-top:20px}.data-section strong{color:#203a32}.data-section small{font-weight:400;color:#84948c}
</style>
