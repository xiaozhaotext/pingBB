<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getP0, postP0, type P0Row } from '@/apis/p0'
import { formatDateTime } from '@/utils/datetime'
import { ElMessage, ElMessageBox } from 'element-plus'

const tab = ref('accounts'), memberId = ref(''), page = ref(1)
const loading = ref(false), error = ref(''), total = ref(0)
const rows = ref<P0Row[]>([]), summary = ref<P0Row>({})
const creditOpen = ref(false), creditBusy = ref(false)
const credit = ref({ memberId: '', amount: '', reason: '' })
const creditRequestId = ref('')
let revision = 0
const types: Record<string,string> = { admin_recharge: '平台手动加款', sandbox_recharge: '沙盒充值', sandbox_withdrawal: '沙盒提现转入', order_pay: '订单余额支付', order_refund: '订单余额退款' }
async function submitCredit() {
  const member = credit.value.memberId.trim()
  const amount = credit.value.amount.trim()
  const reason = credit.value.reason.trim()
  if (!/^[1-9]\d*$/.test(member) || !/^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/.test(amount) || Number(amount) <= 0 || Number(amount) > 50000 || reason.length < 2 || reason.length > 150) {
    ElMessage.warning('请填写有效会员ID、0.01至50000元金额及2至150字原因'); return
  }
  try { await ElMessageBox.confirm(`确认给会员 ${member} 的消费余额加款 ¥${Number(amount).toFixed(2)}？`, '确认平台加款', { type: 'warning' }) }
  catch { return }
  if (!creditRequestId.value) creditRequestId.value = crypto.randomUUID()
  creditBusy.value = true
  try {
    const response = await postP0<{ balance: string }>('wallet/manual-recharge', { memberId: Number(member), amount, reason, requestId: creditRequestId.value })
    ElMessage.success(`加款成功，当前余额 ¥${response.data.balance}`)
    creditOpen.value = false
    credit.value = { memberId: '', amount: '', reason: '' }
    creditRequestId.value = ''
    await load(true)
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '加款失败，请检查后重试；重试将沿用同一请求编号') }
  finally { creditBusy.value = false }
}
async function load(reset = false) {
  if (reset) page.value = 1
  const current = ++revision
  error.value = ''
  if (memberId.value && !/^[1-9]\d*$/.test(memberId.value)) {
    error.value = '请输入有效的会员ID'; loading.value = false; rows.value = []; summary.value = {}; total.value = 0; return
  }
  loading.value = true
  try {
    const res = await getP0<{ rows:P0Row[]; summary:P0Row; total:number }>('wallet', {
      tab:tab.value, memberId:memberId.value || undefined, page:page.value, size:20,
    })
    if (current !== revision) return
    rows.value = res.data.rows; summary.value = res.data.summary; total.value = res.data.total
  } catch {
    if (current !== revision) return
    rows.value = []; summary.value = {}; total.value = 0; error.value = '消费钱包加载失败，请检查权限或连接后重试'
  } finally { if (current === revision) loading.value = false }
}
onMounted(() => load())
</script>

<template>
  <main class="wallet" v-loading="loading">
    <h1>消费余额与充值</h1>
    <p>核对会员充值到账、自选区余额支付和提现转入记录。平台手动加款单独记账，不计为现金收款。金额单位：元。</p>
    <el-button type="primary" @click="creditOpen = true">给会员手动加款</el-button>
    <el-form inline @submit.prevent="load(true)">
      <el-form-item label="会员ID"><el-input v-model="memberId" placeholder="留空查看全部会员" clearable /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">查询 / 刷新</el-button></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <section class="cards">
      <el-card v-for="item in [['balance','当前消费余额'],['recharged','支付充值到账'],['manualCredited','平台手动加款'],['spent','累计余额支付'],['refunded','累计余额退款'],['withdrawalTransferred','提现转入余额']]" :key="item[0]" shadow="never">
        <span>{{ item[1] }}</span><strong>¥ {{ summary[item[0]!] ?? '—' }}</strong>
      </el-card>
    </section>
    <el-tabs v-model="tab" @tab-change="load(true)">
      <el-tab-pane label="消费余额账户" name="accounts" />
      <el-tab-pane label="充值订单" name="recharges" />
      <el-tab-pane label="余额变动流水" name="ledger" />
    </el-tabs>
    <el-table :key="tab" :data="rows" stripe border>
      <el-table-column prop="member_id" label="会员ID" width="100" />
      <el-table-column prop="username" label="会员账号" min-width="140" />
      <el-table-column v-if="tab==='accounts'" prop="balance" label="可消费余额（元）" min-width="160" />
      <template v-if="tab==='recharges'">
        <el-table-column prop="order_no" label="充值单号" min-width="280" />
        <el-table-column prop="amount" label="充值金额（元）" width="150" />
        <el-table-column label="渠道" width="120"><template #default="{row}">{{ row.channel==='MOCK'?'沙盒模拟':row.channel==='WECHAT'?'微信':row.channel==='ALIPAY'?'支付宝':row.channel }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}">{{row.status==='PAID'?'已到账':row.status==='PENDING'?'待支付':row.status}}</template></el-table-column>
        <el-table-column prop="balance_after" label="充值后余额（元）" width="170" />
        <el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ formatDateTime(String(row.created_at || '')) }}</template></el-table-column>
        <el-table-column label="到账时间" min-width="180"><template #default="{ row }">{{ formatDateTime(String(row.paid_at || '')) }}</template></el-table-column>
      </template>
      <template v-if="tab==='ledger'">
        <el-table-column label="类型" width="160"><template #default="{row}">{{types[String(row.change_type)] || row.change_type}}</template></el-table-column>
        <el-table-column prop="amount" label="变动金额（元）" width="150" />
        <el-table-column prop="balance_after" label="变动后余额（元）" width="170" />
        <el-table-column prop="biz_id" label="业务单号 / 订单ID" min-width="220" />
        <el-table-column prop="remark" label="说明" min-width="220" />
        <el-table-column label="时间" min-width="180"><template #default="{ row }">{{ formatDateTime(String(row.create_time || '')) }}</template></el-table-column>
      </template>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load()" />
    <el-dialog v-model="creditOpen" title="平台手动加款" width="460px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="submitCredit">
        <el-form-item label="会员ID"><el-input v-model="credit.memberId" placeholder="输入收款会员ID" /></el-form-item>
        <el-form-item label="加款金额（元）"><el-input v-model="credit.amount" placeholder="0.01 至 50000.00" /></el-form-item>
        <el-form-item label="加款原因"><el-input v-model="credit.reason" type="textarea" :rows="3" maxlength="150" show-word-limit /></el-form-item>
        <el-alert title="确认后直接增加会员消费余额，并记录管理员和余额流水；不会生成支付充值订单。" type="info" :closable="false" />
      </el-form>
      <template #footer><el-button @click="creditOpen = false">取消</el-button><el-button type="primary" :loading="creditBusy" @click="submitCredit">确认加款</el-button></template>
    </el-dialog>
  </main>
</template>

<style scoped>
.wallet{padding:24px}.wallet p{color:#667085}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:16px;margin:20px 0}.cards strong{display:block;font-size:24px;margin-top:12px}.el-pagination{margin-top:20px}
</style>

