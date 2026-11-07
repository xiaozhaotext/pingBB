<template>
  <view class="page">
    <view class="card"><text class="title">账户余额提现</text><text>可用余额 ¥ {{ balance }}</text><text class="hint">提交后扣减并冻结申请金额；后台拒绝时原额退回。确认人工打款后状态变为已打款。</text></view>
    <view class="card">
      <text>真实姓名</text><input v-model="name" placeholder="请输入收款人真实姓名" />
      <text>收款方式</text><picker :range="channels" range-key="label" @change="selectChannel"><view class="picker">{{channels[channelIndex].label}}　⌄</view></picker>
      <template v-if="channelIndex===2"><text>开户银行</text><input v-model="bankName" placeholder="例如中国工商银行" /><text>收款支行</text><input v-model="bankBranch" placeholder="请输入开户支行" /></template>
      <text>收款账户</text><input v-model="account" :placeholder="channelIndex===2?'请输入银行卡号':channelIndex===0?'请输入微信收款账号':'请输入支付宝收款账号'" />
      <text>提现金额（元）</text><input v-model="amount" type="number" placeholder="100、200、300等整百金额" />
      <text class="hint">每次至少100元，只能提100元的整数倍。实际手续费以申请时后台设置为准。</text>
      <button class="primary" :disabled="busy" @click="submit">提交提现申请</button><text v-if="error" class="error">{{error}}</text>
    </view>
    <view class="card"><text class="title">提现记录</text><view v-for="item in rows" :key="item.id" class="record"><text>申请 ¥{{money(item.gross_cents)}}　手续费 ¥{{money(item.fee_cents)}}　实际打款 ¥{{money(item.net_cents)}}</text><text>{{item.channel==='BANK'?'银行卡':item.channel==='ALIPAY'?'支付宝':'微信'}} · {{item.status==='PAID'?'已人工打款':item.status==='REJECTED'?'已拒绝并退回':'待后台处理'}}</text></view><text v-if="!rows.length" class="hint">暂无提现记录</text></view>
  </view>
</template>
<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { http } from '@/utils/http'
import { getMemberInfoAPI } from '@/apis/member'
type Row={id:number;gross_cents:number;fee_cents:number;net_cents:number;channel:string;status:string}
const channels=[{label:'微信',value:'WECHAT'},{label:'支付宝',value:'ALIPAY'},{label:'银行卡',value:'BANK'}]
const name=ref(''),account=ref(''),bankName=ref(''),bankBranch=ref(''),amount=ref(''),balance=ref('—'),error=ref(''),rows=ref<Row[]>([]),busy=ref(false),loaded=ref(false),channelIndex=ref(0)
let requestKey=''
const money=(value:number)=>(Number(value)/100).toFixed(2)
function selectChannel(event:any){channelIndex.value=Number(event.detail.value);account.value=''}
async function load(){
  loaded.value=false
  try{
    const member=await getMemberInfoAPI();requestKey='wallet-withdraw-'+member.data.id
    const [wallet,history]=await Promise.all([http<{balance:string}>({url:'/member/recharge/account'}),http<Row[]>({url:'/member/recharge/withdrawals'})])
    balance.value=wallet.data.balance;rows.value=history.data||[];loaded.value=true
  }catch(e:any){error.value=e?.data?.message||'余额或提现记录加载失败'}
}
onShow(load)
async function submit(){
  if(busy.value)return
  if(!loaded.value||!requestKey){error.value='账户余额尚未加载，请刷新后重试';return}
  const value=amount.value.trim()
  if(!/^\d+$/.test(value)||Number(value)<100||Number(value)>50000||Number(value)%100!==0){error.value='提现金额只能为100至50000元之间的整百数';return}
  if(!name.value.trim()||!account.value.trim()||(channelIndex.value===2&&(!bankName.value.trim()||!bankBranch.value.trim()))){error.value='请完整填写收款信息';return}
  busy.value=true;error.value=''
  try{
    let pending=uni.getStorageSync(requestKey)
    const payload={amount:value,recipientName:name.value.trim(),channel:channels[channelIndex.value].value,bankName:bankName.value.trim(),bankBranch:bankBranch.value.trim(),recipientAccount:account.value.trim()}
    if(pending&&JSON.stringify(pending.payload)!==JSON.stringify(payload)){error.value='上一笔申请结果待确认，请先刷新记录或按原信息重试';return}
    if(!pending){pending={payload,requestId:'WW'+Date.now()+Math.random().toString(36).slice(2,15)};uni.setStorageSync(requestKey,pending)}
    await http({url:'/member/recharge/withdrawals',method:'POST',data:{...pending.payload,requestId:pending.requestId}})
    uni.removeStorageSync(requestKey);amount.value='';uni.showToast({title:'已提交审核',icon:'success'});await load()
  }catch(e:any){error.value=e?.data?.message||e?.message||'申请结果未确认，请按原信息重试'}finally{busy.value=false}
}
</script>
<style scoped>.page{padding:24rpx;background:#f5f7f5;min-height:100vh}.card{background:#fff;border-radius:18rpx;padding:30rpx;margin-bottom:22rpx}.title{display:block;font-size:34rpx;font-weight:bold;color:#285b45;margin-bottom:18rpx}.hint{display:block;color:#687a75;font-size:24rpx;margin:18rpx 0}.error{color:#b42318;display:block;margin-top:16rpx}input,.picker{border:1px solid #ddd;border-radius:10rpx;padding:16rpx;margin:12rpx 0 24rpx;font-size:30rpx;height:auto;line-height:1.5;box-sizing:border-box}input :deep(.uni-input-input){font-size:30rpx;font-family:inherit;height:auto}input :deep(.uni-input-placeholder){font-size:30rpx}.primary{background:#285b45;color:#fff}.record{border-top:1px solid #eee;padding:20rpx 0}.record text{display:block;margin:8rpx 0}</style>
