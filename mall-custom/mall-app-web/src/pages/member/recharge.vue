<template>
  <view class="page">
    <view v-if="sandbox && sandboxMode" class="card hint">沙盒演示：模拟充值不会扣取真实资金，到账余额可用于自选区余额下单。</view>
    <view class="card"><text class="title">账户消费余额</text><text class="balance">¥ {{ balance }}</text><text class="hint">充值及沙盒收益提现转入的余额，可用于自选区余额支付；与可提现收益、积分分别记账。</text></view>
    <view class="card">
      <text class="title">充值金额（元）</text>
      <input v-model="amount" type="digit" :maxlength="8" placeholder="0.01 – 50000.00" :disabled="busy || !!current" />
      <text class="hint">充多少到账多少，无赠送、无手续费。金额最多保留两位小数。</text>
      <view v-if="sandbox && sandboxMode" class="hint">支付方式：沙盒模拟充值</view>
      <view v-else class="choices"><button :disabled="busy || !!current" @click="channel = 'WECHAT'" :class="{ active: channel === 'WECHAT' }">微信支付</button><button :disabled="busy || !!current" @click="channel = 'ALIPAY'" :class="{ active: channel === 'ALIPAY' }">支付宝</button></view>
      <button class="primary" :disabled="busy || !loaded || !!current || !supported" @click="create">确认充值</button>
      <text v-if="!supported" class="hint">请在手机浏览器打开商城完成充值。</text>
      <view v-if="current" class="pending"><text>订单 {{ current.orderNo }}</text><text>应付 ¥ {{ current.amount }} · {{ current.status === 'PAID' ? '已到账' : '待支付 / 到账确认中' }}</text><button v-if="current.status !== 'PAID'" :disabled="busy" @click="pay">继续支付同一订单</button><button :disabled="busy" @click="refresh">我已支付，查询到账</button><button :disabled="busy" @click="current = null">返回充值列表</button></view>
      <text v-if="error" class="error">{{ error }}</text>
      <text class="hint">支付跳转或返回页面不代表到账，请以订单“已到账”为准。付款后请勿另建订单重复支付。</text>
    </view>
    <view class="card"><text class="title">最近50笔充值</text><button :disabled="busy" @click="refresh">刷新余额和记录</button><view v-for="o in orders" :key="o.orderNo" class="record" @click="select(o)"><text>¥ {{ o.amount }} · {{ o.channel === 'MOCK' ? '沙盒模拟' : o.channel === 'WECHAT' ? '微信' : '支付宝' }}</text><text>{{ o.status === 'PAID' ? '已到账' : '待支付 / 待确认，点击查看' }}</text><text class="hint">{{ o.orderNo }}</text></view><text v-if="loaded && !orders.length" class="hint">暂无充值记录</text></view>
    <view class="card"><text class="title">最近50笔余额变动</text><view v-for="(item,i) in movements" :key="i" class="record"><text>{{item.remark}}　{{(Number(item.amount_cents)/100).toFixed(2)}} 元</text><text class="hint">变动后 ¥ {{(Number(item.balance_after_cents)/100).toFixed(2)}} · {{chinaDateTime(item.create_time)}}</text></view><text v-if="loaded && !movements.length" class="hint">暂无余额变动</text></view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { chinaDateTime } from '@/utils/chinaTime'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/http'
import { getMemberInfoAPI } from '@/apis/member'
import { sandboxMode } from '@/utils/sandbox'
type Order = {orderNo:string; amount:string; channel:string; status:string}
const balance=ref('—'),amount=ref('100.00'),channel=ref('WECHAT'),error=ref('')
const busy=ref(false),loaded=ref(false),orders=ref<Order[]>([]),current=ref<Order|null>(null)
const sandbox=ref(false),modeReady=ref(false)
const movements=ref<any[]>([])
let storageKey='',supported=false
// #ifdef H5
supported=true
// #endif
const api=<T,>(url:string,method:'GET'|'POST'='GET',data?:any)=>http<T>({url:'/member/recharge'+url,method,data})
function amountCents(value:string):number | null {
  if(!/^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/.test(value)) return null
  const [yuan,fraction='']=value.split('.')
  const cents=Number(yuan)*100+Number(fraction.padEnd(2,'0'))
  return cents>=1 && cents<=5000000 ? cents : null
}
async function refresh() {
  error.value=''
  modeReady.value=false
  try {
    const member=await getMemberInfoAPI()
    const [a,h,options,ledger]=await Promise.all([api<{balance:string}>('/account'),api<Order[]>('/orders'),api<{sandbox:boolean;channels:string[]}>('/payment-options'),api<any[]>('/ledger')])
    sandbox.value=options.data?.sandbox===true
    modeReady.value=Boolean(sandboxMode)===sandbox.value
    if(!modeReady.value) throw new Error('充值环境与页面不匹配，请打开正确的沙盒或正式入口')
    const nextKey=(sandbox.value?'sandbox-':'')+'recharge-request-'+member.data.id
    if (storageKey && storageKey!==nextKey) current.value=null
    storageKey=nextKey
    movements.value=ledger.data || []
    balance.value=a.data.balance; orders.value=h.data; loaded.value=true
    const pending=uni.getStorageSync(storageKey)
    if(!current.value && pending && typeof pending.amount==='string' && amountCents(pending.amount)!==null
      && (sandbox.value?['MOCK']:['WECHAT','ALIPAY']).includes(pending.channel) && /^[A-Za-z0-9-]{16,64}$/.test(pending.requestId)) {
      amount.value=pending.amount;channel.value=pending.channel
    }
    else if(!current.value) channel.value=sandbox.value?'MOCK':'WECHAT'
    if (current.value) current.value=orders.value.find(o=>o.orderNo===current.value?.orderNo) || (await api<Order>('/orders/'+current.value.orderNo)).data
    if (current.value?.status==='PAID') uni.removeStorageSync(storageKey)
  } catch(e:any) {loaded.value=false;modeReady.value=false;balance.value='—';orders.value=[];current.value=null;error.value=e?.data?.message || e?.message || '请登录并重试，账户数据加载失败'}
}
onShow(refresh)
function select(o:Order) {current.value=o;amount.value=o.amount;channel.value=o.channel}
async function create() {
  if(busy.value) return
  if(!loaded.value || !modeReady.value) {error.value='账户尚未加载或充值环境不匹配，请刷新后重试';return}
  const cents=amountCents(amount.value)
  if(cents===null) {error.value='请输入0.01至50000.00元，最多两位小数';return}
  busy.value=true;error.value=''
  try {
    let pending=uni.getStorageSync(storageKey)
    if(pending && (amountCents(pending.amount)!==cents || pending.channel!==channel.value)) {
      error.value='上一笔请求结果尚未确认，请使用原金额和渠道重试，或刷新充值记录查看原订单';return
    }
    if(!pending) {pending={amount:amount.value,channel:channel.value,requestId:'RC'+Date.now()+Math.random().toString(36).slice(2,15)};uni.setStorageSync(storageKey,pending)}
    current.value=(await api<Order>('/orders','POST',pending)).data
    uni.removeStorageSync(storageKey)
  } catch(e:any) {
    if(e?.data?.code===56002) uni.removeStorageSync(storageKey)
    error.value=e?.data?.code===56002?'支付渠道尚未开通，请稍后再试':e?.data?.message || '下单结果未确认，请重试原请求'
  }
  finally {busy.value=false}
  if(current.value?.status==='PENDING') await pay()
}
async function pay() {
  if(busy.value || !current.value) return
  busy.value=true;error.value=''
  try {
    const payment=(await api<Record<string,string>>('/orders/'+current.value.orderNo+'/pay','POST')).data
    if(payment.type==='SANDBOX_RECHARGE') {
      if(!sandboxMode || !sandbox.value || current.value.channel!=='MOCK') throw new Error('充值环境不匹配，未确认模拟到账')
      current.value=(await api<Order>('/orders/'+current.value.orderNo+'/sandbox-confirm','POST')).data
      await refresh()
      return
    }
    // #ifdef H5
    if(payment.type==='WECHAT_H5_URL') window.location.assign(payment.h5Url)
    else if(payment.type==='ALIPAY_FORM') {
      const div=document.createElement('div');div.innerHTML=payment.formHtml
      document.body.appendChild(div);const form=div.querySelector('form')
      if(!form) {div.remove();throw new Error('支付表单无效')}
      form.submit();div.remove()
    } else throw new Error('暂不支持此支付方式')
    // #endif
  } catch(e:any) {error.value=e?.data?.message || e.message || '支付发起失败，请查询原订单后重试'}
  finally {busy.value=false}
}
</script>
<style scoped>
.page{padding:28rpx;background:#f5f7f5;min-height:100vh}.card{background:white;border-radius:20rpx;padding:32rpx;margin-bottom:24rpx}.title{display:block;font-size:32rpx;font-weight:600;color:#254d39}.balance{display:block;font-size:60rpx;margin:24rpx 0}.hint{display:block;font-size:24rpx;color:#737b77;margin:16rpx 0;overflow-wrap:anywhere}input{border-bottom:1px solid #ddd;height:100rpx;font-size:44rpx}.choices{display:flex;gap:20rpx;margin:28rpx 0}.choices button{flex:1;font-size:28rpx}.active{color:#285b40;border:1px solid #285b40}.primary{background:#285b40;color:white}.error{display:block;color:#b42318;margin-top:20rpx}.pending text,.record text{display:block;margin:14rpx 0}.pending,.record{border-top:1px solid #eee;margin-top:24rpx;padding-top:16rpx}.pending button{margin-top:16rpx}
</style>
