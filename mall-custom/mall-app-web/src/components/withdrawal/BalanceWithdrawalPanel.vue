<template>
  <view class="page">
    <view class="card"><text class="title">账户余额提现</text><text>当前可用余额 ¥{{ displayBalance }}</text></view>
    <view class="card withdrawal-rules">
      <text class="title">提现规则说明</text>
      <text class="withdrawal-rule">1. 可提现额度：当前可用余额 ¥{{ displayBalance }}。{{ thresholdText }}金额须大于 0 且最多两位小数，单笔最高 {{ policy?.maximum ?? '—' }} 元，且不得超过可用余额。申请提交后，该笔金额从可用余额中扣除。</text>
      <text class="withdrawal-rule">2. 每日提现次数：每天不限申请次数，每笔单独审核，须有足够可用余额。</text>
      <text class="withdrawal-rule">3. 提现时间：每天 24 小时可提交申请。平台在工作日 9:00–18:00 处理，其他时间提交的申请顺延至下一个工作日。</text>
      <text class="withdrawal-rule">4. 审核与到账时间：预计 1 个工作日内审核。审核通过后，预计 1–3 个工作日人工打款至所选收款账户。法定节假日顺延，非即时到账。以提现记录及实际收款为准。</text>
      <text class="withdrawal-rule">5. 手续费：{{ feePercent === null ? '费率待加载。' : `按申请金额的 ${feePercent}% 收取，从申请金额中扣除。` }}实际打款金额以提交时显示的费率计算。</text>
      <text class="withdrawal-rule">6. 审核未通过或打款失败：申请金额原额退回可用余额，不收取手续费。请核对收款人姓名及账户信息。</text>
    </view>
    <view class="card">
      <text>真实姓名</text><input v-model="name" placeholder="请输入收款人真实姓名" />
      <text>收款方式</text><picker :range="channels" range-key="label" @change="selectChannel"><view class="picker">{{channels[channelIndex].label}}　⌄</view></picker>
      <template v-if="channelIndex===2"><text>开户银行</text><input v-model="bankName" placeholder="例如中国工商银行" /><text>收款支行</text><input v-model="bankBranch" placeholder="请输入开户支行" /></template>
      <text>收款账户</text><input v-model="account" :placeholder="channelIndex===2?'请输入银行卡号':channelIndex===0?'请输入微信收款账号':'请输入支付宝收款账号'" />
      <text>提现金额（元）</text><input v-model="amount" type="digit" placeholder="请输入提现金额" />
      <text v-if="estimate" class="hint">预计手续费 ¥{{ estimate.fee }}，预计实际打款 ¥{{ estimate.net }}（费率 {{ feePercent }}%）</text>
      <button class="primary" :disabled="busy || refreshing" @click="submit">提交提现申请</button><text v-if="error" class="error">{{error}}</text>
    </view>
    <view class="card"><text class="title">提现记录</text><view v-for="item in rows" :key="item.id" class="record"><text>申请 ¥{{money(item.gross_cents)}}　手续费 ¥{{money(item.fee_cents)}}　实际打款 ¥{{money(item.net_cents)}}</text><text>{{item.channel==='BANK'?'银行卡':item.channel==='ALIPAY'?'支付宝':'微信'}} · {{item.status==='PAID'?'已人工打款':item.status==='REJECTED'?'已拒绝并退回':'待后台处理'}}</text></view><text v-if="!rows.length" class="hint">暂无提现记录</text></view>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { ref } from 'vue'
import { http } from '@/utils/http'
import { getMemberInfoAPI, getWithdrawalOptionsAPI } from '@/apis/member'
import { withdrawalError, showWithdrawalError, type WithdrawalPolicy } from '@/utils/withdrawalPolicy'
const policy = ref<WithdrawalPolicy | null>(null)
const feePercent = computed(() => policy.value ? Number((Number(policy.value.feeRate) * 100).toFixed(4)) : null)
const thresholdText = computed(() => {
  if (!policy.value) return ''
  if (policy.value.mode === 'NONE') return '无最低门槛。'
  if (policy.value.mode === 'HUNDRED') return '最低 100 元，金额须为 100 元的整数倍。'
  return `最低 ${Number(policy.value.minimum)} 元。`
})
type Row={id:number;gross_cents:number;fee_cents:number;net_cents:number;channel:string;status:string}
const channels=[{label:'微信',value:'WECHAT'},{label:'支付宝',value:'ALIPAY'},{label:'银行卡',value:'BANK'}]
const name=ref(''),account=ref(''),bankName=ref(''),bankBranch=ref(''),amount=ref(''),balance=ref('—'),error=ref(''),rows=ref<Row[]>([]),busy=ref(false),loaded=ref(false),channelIndex=ref(0)
let requestKey=''
const money=(value:number)=>(Number(value)/100).toFixed(2)
const balanceCents = computed(() => Math.round(Number(balance.value) * 100))
const displayBalance = computed(() => Number.isFinite(balanceCents.value) ? money(balanceCents.value) : '—')
const estimate = computed(() => {
  const value = String(amount.value ?? '').trim()
  if (!loaded.value || !policy.value || withdrawalError(value, policy.value)) return null
  const cents = Math.round(Number(value) * 100)
  if (cents > balanceCents.value) return null
  const rateMicros = Math.round(Number(policy.value.feeRate) * 1_000_000)
  const fee = Math.floor((cents * rateMicros + 500_000) / 1_000_000)
  return { fee: money(fee), net: money(cents - fee) }
})
function selectChannel(event:any){channelIndex.value=Number(event.detail.value);account.value=''}
async function load(){
  loaded.value=false
  try{
    const member=await getMemberInfoAPI();requestKey='wallet-withdraw-'+member.data.id
    const [wallet,history,options]=await Promise.all([http<{balance:string}>({url:'/member/recharge/account'}),http<Row[]>({url:'/member/recharge/withdrawals'}),getWithdrawalOptionsAPI()])
    if (!options.data.policy) throw new Error('提现规则未加载')
    policy.value=options.data.policy
    balance.value=wallet.data.balance;rows.value=history.data||[];loaded.value=true
  }catch(e:any){error.value=e?.data?.message||'余额或提现记录加载失败'}
}
const refreshing = ref(false)
let refreshTask: Promise<void> = Promise.resolve()
function refresh() {
  refreshing.value = true
  const task = refreshTask.then(load, load)
  refreshTask = task
  return task.finally(() => {
    if (refreshTask === task) refreshing.value = false
  })
}
onMounted(refresh)
defineExpose({ refresh, busy })
async function submit(){
  if(busy.value || refreshing.value)return
  if(!loaded.value||!requestKey){error.value='账户余额尚未加载，请刷新后重试';return}
  const value=String(amount.value ?? '').trim()
  if (!policy.value) return
  const invalid=withdrawalError(value,policy.value)
  if (invalid) { error.value=invalid;showWithdrawalError(invalid);return }
  if(!name.value.trim()||!account.value.trim()||(channelIndex.value===2&&(!bankName.value.trim()||!bankBranch.value.trim()))){error.value='请完整填写收款信息';return}
  busy.value=true;error.value=''
  try{
    let pending=uni.getStorageSync(requestKey)
    const payload={amount:value,recipientName:name.value.trim(),channel:channels[channelIndex.value].value,bankName:bankName.value.trim(),bankBranch:bankBranch.value.trim(),recipientAccount:account.value.trim()}
    if(pending&&JSON.stringify(pending.payload)!==JSON.stringify(payload)){error.value='上一笔申请结果待确认，请先刷新记录或按原信息重试';return}
    if (!pending && Math.round(Number(value) * 100) > balanceCents.value) {
      error.value = `提现金额不得超过当前可用余额 ¥${displayBalance.value}`
      showWithdrawalError(error.value)
      return
    }
    if(!pending){pending={payload,requestId:'WW'+Date.now()+Math.random().toString(36).slice(2,15)};uni.setStorageSync(requestKey,pending)}
    await http({url:'/member/recharge/withdrawals',method:'POST',data:{...pending.payload,requestId:pending.requestId}})
    uni.removeStorageSync(requestKey);amount.value='';uni.showToast({title:'已提交审核',icon:'success'});await load()
  }catch(e:any){error.value=e?.data?.message||e?.message||'申请结果未确认，请按原信息重试';showWithdrawalError(error.value)}finally{busy.value=false}
}
</script>
<style scoped>.page{padding:0;background:#fff9f1}.card{background:#fff;border-radius:18rpx;padding:30rpx;margin-bottom:22rpx}.title{display:block;font-size:34rpx;font-weight:bold;color:#5b3728;margin-bottom:18rpx}.hint{display:block;color:#8d6655;font-size:24rpx;margin:18rpx 0}.error{color:#b42318;display:block;margin-top:16rpx}input,.picker{display:block;box-sizing:border-box;width:100%;min-height:76rpx;border:1px solid #ddd;border-radius:10rpx;margin:12rpx 0 24rpx;font-size:28rpx;color:#2d2d2d}input{padding:0 20rpx;line-height:normal}.picker{padding:16rpx 20rpx}.primary{background:#e85b36;color:#fff}.record{border-top:1px solid #eee;padding:20rpx 0}.record text{display:block;margin:8rpx 0}.withdrawal-rules{color:#2d2d2d}.withdrawal-rule{display:block;font-size:32rpx;line-height:1.8;margin:16rpx 0;word-break:break-word}
</style>
