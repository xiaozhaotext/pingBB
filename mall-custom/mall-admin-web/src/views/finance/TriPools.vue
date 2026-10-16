<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

type Row = Record<string, any>
const route = useRoute()
const pool = computed(() => String(route.meta.pool || 'PARTNER'))
const names: Record<string, string> = { PARTNER: '合伙人池', COFOUNDER: '联创池', OPERATIONS: '运营池' }
const identities: Record<string, string> = { VISITOR: '游客', MEMBER: '会员', PARTNER: '合伙人', COFOUNDER: '联创' }
const data = ref<Row>({ enabled: false, pools: [], products: [], operationsMembers: [] })
const records = ref<Row[]>([]), ledger = ref<Row[]>([]), members = ref<Row[]>([])
const page = ref(1), memberPage = ref(1), busy = ref(false), error = ref('')
const operationsRequestId = ref('')
const roster = ref({ memberId: undefined as number | undefined, shares: 1, enabled: true, reason: '' })
const product = ref({ productId: undefined as number | undefined, enabled: true })
const current = computed<Row>(() => data.value.pools?.find((p: Row) => p.code === pool.value) || {})
const money = (n: unknown) => Number(n || 0).toFixed(2)
async function load() {
  busy.value = true; error.value = ''
  try {
    data.value = (await getP0<Row>('tri-pools')).data
    if (!data.value.enabled) { records.value = []; ledger.value = []; members.value = []; return }
    const responses = await Promise.all([
      getP0<Row[]>('tri-pools/records', { pool: pool.value, page: page.value }),
      getP0<Row[]>('tri-pools/ledger', { pool: pool.value, page: page.value }),
      getP0<Row[]>('tri-pools/members', { page: memberPage.value }),
    ])
    records.value = responses[0].data; ledger.value = responses[1].data; members.value = responses[2].data
  } catch { error.value = '加载失败，请检查权限并重试' }
  finally { busy.value = false }
}
async function saveRoster() {
  if (!roster.value.memberId || roster.value.reason.trim().length < 5) { ElMessage.warning('请填写用户编号及至少5字调整依据'); return }
  busy.value = true
  try { await postP0('tri-pools/operations/members', { ...roster.value }); ElMessage.success('运营份额已保存'); await load() }
  finally { busy.value = false }
}
async function saveProduct() {
  if (!product.value.productId) { ElMessage.warning('请填写3999元身份牌商品编号'); return }
  busy.value = true
  try { await postP0('tri-pools/products', { ...product.value }); ElMessage.success('身份牌商品已保存'); await load() }
  finally { busy.value = false }
}
async function distribute() {
  busy.value = true
  try {
    const preview = (await getP0<Row>('tri-pools/operations/preview')).data
    if (!preview.awards?.length) { ElMessage.info('没有可发放金额或有效运营份额'); return }
    await ElMessageBox.confirm(`本次发放给${preview.awards.length}人，共${preview.totalShares}份，合计¥${money(preview.paidTotal)}，入账到会员收益账户。确认发放？`, '运营池发放预览', { type: 'warning' })
    if (!operationsRequestId.value) operationsRequestId.value = crypto.randomUUID()
    await postP0('tri-pools/operations/distribute', {
      requestId: operationsRequestId.value, balance: preview.balance, totalShares: preview.totalShares, previewToken: preview.previewToken,
    })
    operationsRequestId.value = ''
    ElMessage.success('运营池已发放'); await load()
  } catch (e) { if (e !== 'cancel' && e !== 'close') error.value = '发放未完成，请核对批次记录后重新预览' }
  finally { busy.value = false }
}
watch(pool, () => { page.value = 1; load() }, { immediate: true })
</script>

<template>
  <div class="tri-pools" v-loading="busy">
    <header><h2>{{ names[pool] }}管理</h2><el-button @click="load">刷新</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="!data.enabled && !error" title="新身份与三分红池尚未启用，请先完成迁移核对和测试。" type="info" :closable="false" />
    <template v-if="data.enabled">
      <div class="stats"><el-statistic title="池内余额（元）" :value="Number(current.balance || 0)" :precision="2" /><el-statistic title="累计入池（元）" :value="Number(current.total_in || 0)" :precision="2" /><el-statistic title="累计分配（元）" :value="Number(current.total_out || 0)" :precision="2" /></div>
      <el-alert :title="pool === 'OPERATIONS' ? '每笔3999元身份牌支付入池200元，按后台指定运营份额手动发放。' : `每笔3999元身份牌支付入池${pool === 'PARTNER' ? 500 : 200}元，并按有效总份数自动分配。零头及封顶剩余留池。`" :closable="false" type="info" />
      <section v-if="pool === 'OPERATIONS'">
        <h3>运营人员及份额</h3>
        <el-form inline><el-form-item label="用户编号"><el-input-number v-model="roster.memberId" :min="1" :precision="0" /></el-form-item><el-form-item label="份数"><el-input-number v-model="roster.shares" :min="1" :max="1000000" :precision="0" /></el-form-item><el-form-item label="启用"><el-switch v-model="roster.enabled" /></el-form-item><el-form-item label="调整依据"><el-input v-model="roster.reason" :maxlength="500" /></el-form-item><el-button :disabled="busy" @click="saveRoster">保存份额</el-button></el-form>
        <el-table :data="data.operationsMembers"><el-table-column prop="member_id" label="用户编号" /><el-table-column prop="username" label="用户名" /><el-table-column prop="shares" label="份数" /><el-table-column label="状态"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column><el-table-column prop="reason" label="调整依据" /></el-table>
        <el-button type="primary" :disabled="busy" @click="distribute">预览并发放运营池</el-button>
      </section>
      <section><h3>分红明细</h3><el-table :data="records" empty-text="暂无分红记录"><el-table-column prop="member_id" label="用户编号" /><el-table-column prop="username" label="用户名" /><el-table-column label="发放时身份"><template #default="{ row }">{{ identities[row.identity_snapshot] }}</template></el-table-column><el-table-column prop="created_at" label="发放时间" width="180" /><el-table-column prop="amount" label="金额（元）" /><el-table-column prop="shares" label="个人份数" /><el-table-column prop="total_shares" label="全池总份数" /><el-table-column prop="cap_amount" label="累计上限" /><el-table-column prop="received_before" label="此前已领" /><el-table-column prop="batch_id" label="批次" /></el-table>
        <div class="paging"><el-button :disabled="page === 1 || busy" @click="page--; load()">上一页</el-button><span>第{{ page }}页</span><el-button :disabled="(records.length < 50 && ledger.length < 50) || busy" @click="page++; load()">下一页</el-button></div>
      </section>
      <section><h3>池资金流水</h3><el-table :data="ledger"><el-table-column prop="created_at" label="时间" /><el-table-column prop="entry_type" label="入池／发放" /><el-table-column prop="amount" label="变动金额" /><el-table-column prop="balance_after" label="变动后余额" /><el-table-column prop="source_key" label="来源凭据" /><el-table-column prop="operator_id" label="操作人编号" /></el-table></section>
      <section><h3>四级身份与权益</h3><el-table :data="members"><el-table-column prop="id" label="用户编号" /><el-table-column prop="username" label="用户名" /><el-table-column label="身份"><template #default="{ row }">{{ identities[row.tier] }}</template></el-table-column><el-table-column prop="direct_paid_count" label="有效直邀会员数" /><el-table-column prop="partnerShares" label="合伙人池份数" /><el-table-column prop="partnerCap" label="合伙人池累计上限" /></el-table><div class="paging"><el-button :disabled="memberPage === 1 || busy" @click="memberPage--; load()">上一页</el-button><span>第{{ memberPage }}页</span><el-button :disabled="members.length < 50 || busy" @click="memberPage++; load()">下一页</el-button></div></section>
      <section><h3>3999元会员身份牌商品</h3><el-form inline><el-form-item label="商品编号"><el-input-number v-model="product.productId" :min="1" :precision="0" /></el-form-item><el-form-item label="启用"><el-switch v-model="product.enabled" /></el-form-item><el-button :disabled="busy" @click="saveProduct">保存身份牌</el-button></el-form><el-table :data="data.products"><el-table-column prop="product_id" label="商品编号" /><el-table-column prop="name" label="商品名称" /><el-table-column label="状态"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column></el-table></section>
    </template>
  </div>
</template>

<style scoped>
.tri-pools{padding:24px}header,.paging{display:flex;align-items:center;gap:16px;justify-content:space-between}section{margin:24px 0;padding:20px;border:1px solid #e5e7eb;border-radius:8px}.stats{display:flex;gap:64px;margin:24px 0}.paging{justify-content:flex-end;margin-top:16px}h3{margin:0 0 18px}
</style>
