<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'

type SectionKey = 'p0Members' | 'p0Partner' | 'p0Cofounder' | 'p0Points' | 'p0Finance' | 'p0Dividend' | 'p0Settings' | 'p0SmsSettings'
const sections: Record<SectionKey, { title: string; path: string; note: string }> = {
  p0Members: { title: '会员列表', path: 'members', note: '选择会员查看身份、会员操作、登录权限和列表信息。身份及推荐关系变更写入审计。' },
  p0Partner: { title: '合伙人管理', path: 'partner', note: '权益方案与购买记录均来自测试数据库；模拟购买入口尚未开放给消费者。' },
  p0Cofounder: { title: '联创管理', path: 'cofounder', note: '所有金额与权益仅用于模拟展示，不产生真实分红。' },
  p0Points: { title: '双积分管理', path: 'points', note: '调整先申请后审核，余额和流水在同一数据库事务中更新。' },
  p0Finance: { title: '财务中心', path: 'finance', note: '提现审核仅改变模拟状态；审核通过不触发打款。' },
  p0Dividend: { title: '每日贡献奖励池', path: 'dividend', note: '展示数据库中的贡献、模拟快照和异常；自动核验与实际分红未启用。' },
  p0Settings: { title: '系统配置与日志', path: 'settings', note: '平台管理员可调整会员购买与直推奖励参数，保存前后值均写入审计。' },
  p0SmsSettings: { title: '短信设置', path: 'settings', note: '配置短信服务商、账号、密钥、签名和模板；敏感密钥在列表中脱敏展示。' },
}
const route = useRoute()
const sectionKey = computed(() => {
  const metaSection = typeof route.meta.section === 'string' ? route.meta.section : ''
  if (metaSection && metaSection in sections) return metaSection as SectionKey
  return (typeof route.name === 'string' && route.name in sections ? route.name : 'p0Members') as SectionKey
})
const section = computed(() => sections[sectionKey.value])
const loading = ref(false)
const data = ref<Record<string, unknown>>({})
const poolStats = computed(() => data.value.poolStats as Record<string, unknown> | undefined)
const poolTodayIn = computed(() => data.value.poolTodayIn as Record<string, unknown> | undefined)
const memberKeyword = ref('')
const memberId = ref<number | null>(null)
const drawerOpen = ref(false)
const memberDetail = ref<Record<string, unknown> | null>(null)
const memberDetailTab = ref('identity')
const memberStats = ref<Record<string, unknown> | null>(null)
const memberZone = ref('')
const identityType = ref(0)
const identityReason = ref('')
const referrerId = ref<number | null>(null)
const referrerReason = ref('')
const pointsForm = ref({ memberId: 0, pointsType: 1, delta: 0, reason: '', idempotencyKey: '' })
const benefitTypeLabel = (type: unknown) => type === 'TRAVEL' ? '一次旅游权益' : '每月200元奖励'
const benefitStatusLabel = (status: unknown) => (({ PENDING: '待人工审核', APPROVED: '已审核入账', REDEEMED: '已核销', REJECTED: '已拒绝' } as Record<string, string>)[String(status)] || String(status))
const money = (value: unknown) => Number.isFinite(Number(value)) ? Number(value).toFixed(2) : '—'
const benefitRows = ref<P0Row[]>([])
const benefitForm = ref({ memberId: 0, type: 'MONTHLY_REWARD', period: new Date().toISOString().slice(0, 7), reason: '' })
async function loadBenefits() { benefitRows.value = (await getP0<P0Row[]>('benefits')).data }
async function requestBenefit() {
  await postP0('benefits', benefitForm.value)
  ElMessage.success('已提交，等待人工审核或核销')
  await loadBenefits()
}
async function reviewBenefit(id: number, approve: boolean) {
  const { value } = await ElMessageBox.prompt('填写审核依据或旅游核销凭据（至少5字）', approve ? '通过审核 / 确认核销' : '拒绝申请', { inputValidator: (v: string) => v.trim().length >= 5 || '至少填写5字' })
  await postP0(`benefits/${id}/review`, { approve, note: value })
  ElMessage.success('已处理并记录审核信息')
  await loadBenefits()
}
const settingForm = ref({ key: 'site.title', value: '' })
const settingOptions = [
  { key: 'site.title', label: '站点标题' }, { key: 'site.notice', label: '站点公告' },
  { key: 'membership.partner.product_id', label: '合伙人旅游商品ID（留空取消）' },
  { key: 'membership.cofounder.product_id', label: '联创旅游商品ID（留空取消）' },
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
  { key: 'pay.wechat.mch_id', label: '微信支付商户号' },
  { key: 'pay.wechat.api_key', label: '微信支付 API 密钥（敏感）' },
  { key: 'pay.wechat.app_id', label: '微信支付 AppID' },
  { key: 'pay.alipay.app_id', label: '支付宝 AppID' },
  { key: 'pay.alipay.private_key', label: '支付宝应用私钥（敏感）' },
  { key: 'pay.alipay.public_key', label: '支付宝公钥（敏感）' },
  { key: 'sms.provider', label: '短信服务商（aliyun/tencent）' },
  { key: 'sms.access_key', label: '短信 AccessKey' },
  { key: 'sms.access_secret', label: '短信 AccessSecret（敏感）' },
  { key: 'sms.sign_name', label: '短信签名' },
  { key: 'sms.template_code', label: '短信模板 Code' },
  { key: 'dividend.pool.ratio', label: '其它价格会员购买划入旧总池比例（0–1）' },
  { key: 'dividend.daily.base', label: '每日每份分红基数（元）' },
]
const visibleSettingOptions = computed(() => sectionKey.value === 'p0SmsSettings'
  ? settingOptions.filter(option => option.key.startsWith('sms.'))
  : settingOptions)
const labels: Record<string, string> = {
  wechat_account: '微信账号', total_earnings: '累计收益', available_earnings: '可提现收益', frozen_earnings: '冻结收益', withdrawn: '已提现', balance_after: '变动后余额', change_type: '变动类型', biz_id: '业务编号', remark: '备注', version: '版本',
  id: '会员 ID', member_id: '会员编号', username: '会员名称 / 账号', nickname: '昵称', phone: '手机号', status: '登录状态',
  real_name: '真实姓名（本人填写）', email: '邮箱', gender: '性别', wechat: '关联微信', login_count: '登录次数',
  identity_type: '身份', inviter_id: '推荐人', member_balance: '会员余额', product_points: '产品积分', rebuy_points: '复购积分', points_type: '积分类型', balance: '余额', available: '可用',
  frozen: '冻结', amount: '金额', delta: '调整量', reason: '原因', pay_status: '模拟支付状态',
  simulate_flag: '模拟标记', actual_amount: '扣费后金额', fee_rate: '手续费比例', fee_amount: '手续费',
  contribution_value: '贡献值', estimated_amount: '模拟分配金额', ratio: '贡献占比', stat_date: '统计日期',
  create_time: '创建时间', apply_time: '申请时间', update_time: '更新时间',
  pool_balance: '分红池余额', total_in: '累计注入', total_out: '累计发放', today_in: '今日注入',
  total_shares: '总份数', recipients: '发放人数', total_amount: '发放总额', per_share_amount: '每份金额', calc_batch: '发放批次',
}

async function load() {
  loading.value = true
  memberDetail.value = null
  try {
    const params = sectionKey.value === 'p0Members' ? { keyword: memberKeyword.value } : undefined
    const response = await getP0(section.value.path, params)
    const value = response.data
    if (['p0Partner', 'p0Cofounder', 'p0Finance'].includes(sectionKey.value)) await loadBenefits()
    data.value = Array.isArray(value) ? { members: value } : (value as Record<string, unknown>)
    if (sectionKey.value === 'p0Settings' || sectionKey.value === 'p0SmsSettings') {
      if (sectionKey.value === 'p0SmsSettings' && !settingForm.value.key.startsWith('sms.')) settingForm.value.key = 'sms.provider'
      selectSetting(settingForm.value.key)
    }
    if (sectionKey.value === 'p0Members') {
      try {
        const stats = await getP0<Record<string, unknown>>('members/stats')
        memberStats.value = stats.data
      } catch { memberStats.value = null }
    } else {
      memberStats.value = null
    }
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
const isMaskedSettingValue = computed(() => String(settingForm.value.value || '').includes('****'))
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
    memberDetailTab.value = 'identity'
    drawerOpen.value = true
    const current = rows(response.data.identity)[0]
    identityType.value = Number(current?.identity_type ?? 0)
    identityReason.value = ''
    const inviter = rows(response.data.inviter)[0]
    referrerId.value = inviter?.inviter_id == null ? null : Number(inviter.inviter_id)
    referrerReason.value = ''
  } finally { loading.value = false }
}
async function loadMemberOrders(zone: string | number | boolean | undefined) {
  if (!memberId.value) return
  const code = zone ? String(zone) : ''
  memberZone.value = code
  try {
    const response = await getP0<Record<string, unknown>[]>(
      'members/' + memberId.value + '/orders' + (code ? '?zone=' + code : ''))
    if (memberDetail.value) memberDetail.value.orders = response.data
  } catch { /* 保持原有列表，不阻断详情 */ }
}

async function changeMemberStatus() {
  if (!memberId.value || !memberDetail.value) return
  const { value: reason } = await ElMessageBox.prompt('请输入至少5个字的变更原因', '登录权限', { inputValidator: value => value.trim().length >= 5 || '原因至少5个字' })
  const status = Number((memberDetail.value.profile as P0Row).status) === 1 ? 0 : 1
  await postP0(`members/${memberId.value}/status`, {status,reason})
  await openMember(memberId.value)
  ElMessage.success('登录权限已更新')
}
async function changeIdentity() {
  if (!memberId.value || identityReason.value.trim().length < 5) return ElMessage.warning('请填写至少 5 个字的变更原因')
  await ElMessageBox.confirm('确认调整会员身份并写入审计日志？', '身份调整', { type: 'warning' })
  await postP0(`members/${memberId.value}/identity`, { identityType: identityType.value, reason: identityReason.value })
  ElMessage.success('身份已更新')
  await load()
  await openMember(memberId.value)
}
async function changeReferrer() {
  if (!memberId.value || !referrerId.value || referrerReason.value.trim().length < 5) {
    return ElMessage.warning('请填写推荐人 ID 和至少 5 个字的变更原因')
  }
  await ElMessageBox.confirm('确认调整直属推荐人及所有下级的推荐路径？', '调整推荐关系', { type: 'warning' })
  await postP0(`members/${memberId.value}/referrer`, {
    inviterId: referrerId.value, reason: referrerReason.value,
  })
  ElMessage.success('推荐关系已更新并记录审计')
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
  if (!settingForm.value.value.trim() && !['membership.referral.partner.cash_cap', 'membership.partner.product_id', 'membership.cofounder.product_id'].includes(settingForm.value.key)) return ElMessage.warning('请输入设置内容')
  await postP0('settings', settingForm.value)
  ElMessage.success('配置已保存并记录审计')
  await load()
}
const groupTitles: Record<string, string> = {
  members: '会员列表', config: '权益方案', orders: '模拟购买记录', identities: '当前身份',
  accounts: '账户余额', ledger: '流水记录', adjustments: '积分调整审核', withdraws: '提现申请',
  dailyDetails: '每日分红明细', contributions: '贡献事件', snapshots: '模拟分配历史', exceptions: '异常记录',
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
    <el-card v-if="sectionKey === 'p0Members' && memberStats" class="stat-card" shadow="never">
      <div class="stat-grid">
        <div class="stat"><span>会员总数</span><strong>{{ display(memberStats.totalMembers) }}</strong></div>
        <div class="stat"><span>今日新增</span><strong>{{ display(memberStats.todayNewMembers) }}</strong></div>
        <div class="stat"><span>近30天新增</span><strong>{{ display(memberStats.newMembers30d) }}</strong></div>
        <div class="stat"><span>近30天活跃</span><strong>{{ display(memberStats.activeMembers30d) }}</strong></div>
        <div class="stat"><span>今日活跃</span><strong>{{ display(memberStats.todayActiveMembers) }}</strong></div>
        <div class="stat"><span>今日下单会员</span><strong>{{ display(memberStats.todayConsumingMembers) }}</strong></div>
        <div class="stat"><span>今日消费额</span><strong>{{ display(memberStats.todayConsumingAmount) }}</strong></div>
        <div class="stat"><span>累计消费会员</span><strong>{{ display(memberStats.totalConsumingMembers) }}</strong></div>
        <div class="stat"><span>累计消费额</span><strong>{{ display(memberStats.totalConsumingAmount) }}</strong></div>
        <div class="stat"><span>客单价</span><strong>{{ display(memberStats.avgOrderAmount) }}</strong></div>
      </div>
      <div class="stat-trend">
        <span v-for="t in rows(memberStats.trend)" :key="String(t.day)">{{ t.day }} 新增 {{ display(t.newMembers) }} / 活跃 {{ display(t.activeMembers) }} / 金额 {{ display(t.amount) }}</span>
      </div>
    </el-card>
    <el-card v-if="sectionKey === 'p0Points'" class="action-card" shadow="never">
      <strong>提交积分调整申请</strong>
      <el-input-number v-model="pointsForm.memberId" :min="1" placeholder="会员 ID" />
      <el-select v-model="pointsForm.pointsType" style="width:125px"><el-option label="商品积分" :value="1" /><el-option label="复购积分" :value="2" /></el-select>
      <el-input-number v-model="pointsForm.delta" placeholder="正数增加 / 负数减少" />
      <el-input v-model="pointsForm.reason" placeholder="调整原因（至少 5 个字）" />
      <el-button type="primary" @click="requestAdjustment">提交待审核</el-button>
    </el-card>
    <el-card v-if="sectionKey === 'p0Settings' || sectionKey === 'p0SmsSettings'" class="action-card" shadow="never">
      <strong>{{ sectionKey === 'p0SmsSettings' ? '短信参数' : '平台参数' }}</strong>
      <el-select v-model="settingForm.key" style="width:290px" @change="selectSetting"><el-option v-for="option in visibleSettingOptions" :key="option.key" :label="option.label" :value="option.key" /></el-select>
      <el-input v-model="settingForm.value" placeholder="配置值；积分填非负整数" />
      <el-button type="primary" @click="updateSetting">保存</el-button>
      <el-text v-if="isMaskedSettingValue" type="warning" size="small">敏感信息已脱敏显示；如需修改请直接输入新值后保存，不修改请勿提交。</el-text>
    </el-card>

    <el-card v-if="poolStats" class="stat-card" shadow="never">
      <template #header><strong>分红池</strong></template>
      <div class="stat-grid">
        <div class="stat"><span>当前余额（元）</span><strong>{{ money(poolStats.pool_balance) }}</strong></div>
        <div class="stat"><span>累计注入（元）</span><strong>{{ money(poolStats.total_in) }}</strong></div>
        <div class="stat"><span>累计发放（元）</span><strong>{{ money(poolStats.total_out) }}</strong></div>
        <div class="stat"><span>今日注入（元）</span><strong>{{ money(poolTodayIn?.today_in ?? 0) }}</strong></div>
      </div>
    </el-card>

    <el-card v-if="['p0Partner', 'p0Cofounder', 'p0Finance'].includes(sectionKey)" shadow="never" class="action-card">
      <template #header><strong>人工权益审核与旅游核销</strong></template>
      <p>月奖励审核通过后计入可提现收益，未直接打款。旅游核销需填写实际服务凭据。</p>
      <el-input-number v-model="benefitForm.memberId" :min="1" placeholder="会员ID" />
      <el-select v-model="benefitForm.type" style="width:180px"><el-option label="联创每月200元奖励" value="MONTHLY_REWARD"/><el-option label="一次旅游权益核销" value="TRAVEL"/></el-select>
      <el-input v-if="benefitForm.type === 'MONTHLY_REWARD'" v-model="benefitForm.period" placeholder="YYYY-MM" />
      <el-input v-model="benefitForm.reason" placeholder="申请依据（至少5字）" />
      <el-button @click="requestBenefit">提交待审核</el-button>
      <el-table :data="benefitRows" max-height="400">
        <el-table-column prop="member_id" label="会员ID"/><el-table-column label="权益类型"><template #default="{row}">{{ benefitTypeLabel(row.benefit_type) }}</template></el-table-column><el-table-column label="所属月份/次数"><template #default="{row}">{{ row.benefit_period === 'ONCE' ? '一次' : row.benefit_period }}</template></el-table-column>
        <el-table-column label="金额（元）"><template #default="{row}">{{ money(row.amount) }}</template></el-table-column><el-table-column label="状态"><template #default="{row}">{{ benefitStatusLabel(row.status) }}</template></el-table-column><el-table-column prop="request_reason" label="申请依据"/><el-table-column prop="review_note" label="审核/核销凭据"/>
        <el-table-column label="操作"><template #default="{ row }"><template v-if="row.status === 'PENDING'"><el-button link @click="reviewBenefit(Number(row.id), true)">通过/核销</el-button><el-button link type="danger" @click="reviewBenefit(Number(row.id), false)">拒绝</el-button></template></template></el-table-column>
      </el-table>
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

    <el-drawer v-model="drawerOpen" title="会员列表 · 会员详情" size="min(720px, 100vw)" :modal="true" @closed="memberId = null">
      <template v-if="memberDetail">
        <el-tabs v-model="memberDetailTab" class="member-detail-tabs">
          <el-tab-pane label="会员身份" name="identity">
            <p>当前身份：{{ ['普通会员', '合伙人', '联创'][identityType] || '未设置' }}</p>
            <h3>调整身份</h3>
            <el-select v-model="identityType" style="width:100%;margin-bottom:12px"><el-option label="普通会员" :value="0" /><el-option label="合伙人" :value="1" /><el-option label="联创" :value="2" /></el-select>
            <el-input v-model="identityReason" type="textarea" placeholder="必填：变更原因" />
            <el-button type="primary" style="margin-top:12px" @click="changeIdentity">保存并审计</el-button>
            <h3>身份变更记录</h3><el-table :data="rows(memberDetail.identityHistory)" max-height="260"><el-table-column prop="from_type" label="原身份" /><el-table-column prop="to_type" label="新身份" /><el-table-column prop="reason" label="原因" /></el-table>
          </el-tab-pane>
          <el-tab-pane label="会员操作" name="operations">
            <h3>查看收货地址</h3><el-table :data="rows(memberDetail.addresses)"><el-table-column prop="name" label="收件人"/><el-table-column prop="phone_number" label="手机号"/><el-table-column prop="detail_address" label="地址"/></el-table>
            <h3>查看自选 / 复购 / 消费商品订单</h3>
            <el-radio-group v-model="memberZone" size="small" style="margin-bottom:10px" @change="loadMemberOrders">
              <el-radio-button :value="''">全部</el-radio-button>
              <el-radio-button :value="'PICK'">自选区</el-radio-button>
              <el-radio-button :value="'REBUY'">复购区</el-radio-button>
              <el-radio-button :value="'CONSUME'">消费区</el-radio-button>
            </el-radio-group><el-table :data="rows(memberDetail.orders)" max-height="300"><el-table-column prop="order_sn" label="订单号"/><el-table-column prop="product_zones" label="专区"/><el-table-column prop="status" label="状态"/><el-table-column prop="pay_amount" label="金额"/></el-table>
            <h3>消费统计（已支付毛额，未扣退款）</h3><el-table :data="rows(memberDetail.consumption)"><el-table-column prop="order_count" label="订单数"/><el-table-column prop="paid_amount" label="已支付金额"/></el-table>
            <h3>推荐关系</h3><p>推荐人：{{ display(rows(memberDetail.inviter)[0]?.inviter_name) }}</p><p>直接邀请：{{ rows(memberDetail.invitees).length }} 人</p>
            <el-input-number v-model="referrerId" :min="1" placeholder="新推荐人 ID" style="width:100%;margin-bottom:12px" />
            <el-input v-model="referrerReason" type="textarea" placeholder="必填：变更原因（至少 5 个字）" />
            <el-button type="primary" style="margin-top:12px" @click="changeReferrer">保存推荐人并审计</el-button>
          </el-tab-pane>
          <el-tab-pane label="允许登录" name="login">
            <p>当前状态：{{ Number((memberDetail.profile as P0Row).status) === 1 ? '允许登录' : '禁止登录' }}</p>
            <el-button :type="Number((memberDetail.profile as P0Row).status) === 1 ? 'danger' : 'primary'" @click="changeMemberStatus">{{ Number((memberDetail.profile as P0Row).status) === 1 ? '禁用登录' : '恢复登录' }}</el-button>
          </el-tab-pane>
          <el-tab-pane label="列表信息" name="profile">
            <el-descriptions :column="1" border><el-descriptions-item v-for="(value, key) in (memberDetail.profile as P0Row)" :key="key" :label="labels[String(key)] || String(key)">{{ display(value) }}</el-descriptions-item></el-descriptions>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.p0-page{min-height:100vh;padding:28px;background:#f5f7f5}.heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.heading h1{margin:4px 0;font-size:28px;color:#203a32}.heading p{margin:0;color:#61746b}.eyebrow{font-size:11px;letter-spacing:.2em;color:#478264}.action-card{margin-top:20px}.action-card :deep(.el-card__body){display:flex;align-items:center;gap:10px;flex-wrap:wrap}.action-card :deep(.el-input){max-width:300px}.stat-card{margin-top:20px}.stat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px}.stat{background:#f3f7f4;border-radius:10px;padding:12px;display:flex;flex-direction:column;gap:4px}.stat span{font-size:12px;color:#61746b}.stat strong{font-size:20px;color:#203a32}.stat-trend{display:flex;flex-wrap:wrap;gap:12px;margin-top:12px;font-size:12px;color:#61746b}.data-section{margin-top:20px}.data-section strong{color:#203a32}.data-section small{font-weight:400;color:#84948c}
</style>
