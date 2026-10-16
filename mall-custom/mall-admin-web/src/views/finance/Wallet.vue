<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getP0, type P0Row } from '@/apis/p0'

const tab = ref('accounts'), memberId = ref(''), page = ref(1)
const loading = ref(false), error = ref(''), total = ref(0)
const rows = ref<P0Row[]>([]), summary = ref<P0Row>({})
let revision = 0
const types: Record<string,string> = { sandbox_recharge: '沙盒充值', sandbox_withdrawal: '沙盒提现转入', order_pay: '订单余额支付', order_refund: '订单余额退款' }
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
    <p>核对会员充值到账、自选区余额支付和提现转入记录。金额单位：元。</p>
    <el-form inline @submit.prevent="load(true)">
      <el-form-item label="会员ID"><el-input v-model="memberId" placeholder="留空查看全部会员" clearable /></el-form-item>
      <el-form-item><el-button type="primary" native-type="submit">查询 / 刷新</el-button></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <section class="cards">
      <el-card v-for="item in [['balance','当前消费余额'],['recharged','累计充值到账'],['spent','累计余额支付'],['refunded','累计余额退款'],['withdrawalTransferred','提现转入余额']]" :key="item[0]" shadow="never">
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
        <el-table-column prop="created_at" label="创建时间" min-width="180" />
        <el-table-column prop="paid_at" label="到账时间" min-width="180" />
      </template>
      <template v-if="tab==='ledger'">
        <el-table-column label="类型" width="160"><template #default="{row}">{{types[String(row.change_type)] || row.change_type}}</template></el-table-column>
        <el-table-column prop="amount" label="变动金额（元）" width="150" />
        <el-table-column prop="balance_after" label="变动后余额（元）" width="170" />
        <el-table-column prop="biz_id" label="业务单号 / 订单ID" min-width="220" />
        <el-table-column prop="remark" label="说明" min-width="220" />
        <el-table-column prop="create_time" label="时间" min-width="180" />
      </template>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load()" />
  </main>
</template>

<style scoped>
.wallet{padding:24px}.wallet p{color:#667085}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:16px;margin:20px 0}.cards strong{display:block;font-size:24px;margin-top:12px}.el-pagination{margin-top:20px}
</style>
