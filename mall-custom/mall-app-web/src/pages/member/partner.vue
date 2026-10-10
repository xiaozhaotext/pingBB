<template>
  <view class="app">
    <view class="mock-banner">合伙人权益为模拟方案：下单与支付均为模拟，不会产生真实扣款，也不会自动发放真实奖励。身份升级与积分赠送为可核验的系统行为。</view>

    <view class="card" v-if="config">
      <text class="title">合伙人 3999 权益方案（模拟）</text>
      <view class="row"><text>权益价格</text><text class="v">{{ config.price }} 元(模拟)</text></view>
      <view class="row"><text>赠送商品积分</text><text class="v">{{ config.productPoints }}</text></view>
      <view class="row"><text>赠送复购积分</text><text class="v">{{ config.repurchasePoints }}</text></view>
      <view class="row"><text>直接分享奖励比例</text><text class="v">{{ (config.directShareRate * 100).toFixed(0) }}%（待确认）</text></view>
    </view>

    <view class="card">
      <text class="title">模拟下单与支付（真实写入数据库）</text>
      <view class="row"><text>业务单号 bizId</text><input class="input" v-model="bizId" placeholder="如 PARTNER-1001" /></view>
      <view class="row"><text>订单状态</text><text class="v">{{ orderStatus || '未下单' }}</text></view>
      <button class="btn-primary mt" @click="handleCreateOrder">1. 创建模拟入驻订单</button>
      <button class="btn-primary mt" @click="handlePay">2. 模拟支付并完成身份升级</button>
      <view v-if="payResult" class="result">{{ payResult }}</view>
      <view v-if="actionError" class="result error">{{ actionError }}</view>
      <view class="hint">说明：支付为模拟，不产生真实扣款；身份升级与积分赠送是真实、可核验的系统行为。</view>
    </view>
    <view v-if="loadError" class="card error">{{ loadError }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPartnerConfigAPI, createPartnerOrderAPI, payPartnerAPI, getMyIdentityAPI } from '@/apis/member'

const config = ref<any>(null)
const loadError = ref('')
const bizId = ref('')
const orderStatus = ref('')
const payResult = ref('')
const actionError = ref('')

const loadConfig = async () => {
  try { const r = await getPartnerConfigAPI(); config.value = r.data; if (!r.data) loadError.value = '暂无已启用的权益方案。' }
  catch (e) { loadError.value = '权益方案加载失败，请登录后重试。' }
}

const handleCreateOrder = async () => {
  actionError.value = ''; payResult.value = ''
  if (!bizId.value) { actionError.value = '请先填写业务单号'; return }
  try {
    const r: any = await createPartnerOrderAPI({ bizId: bizId.value })
    if (r && r.code && r.code !== 200) { actionError.value = '下单失败：' + (r.message || r.code); return }
    if (r && r.code && r.code !== 200) { return }
    const o = r.data || r
    orderStatus.value = '已创建（orderId=' + (o.id ?? '-') + '，金额 ' + (o.amount ?? '-') + '）'
  } catch (e: any) { actionError.value = '下单异常：' + (e?.message || e) }
}

const handlePay = async () => {
  actionError.value = ''; payResult.value = ''
  if (!bizId.value) { actionError.value = '请先填写业务单号并完成下单'; return }
  try {
    const r: any = await payPartnerAPI({ bizId: bizId.value })
    if (r && r.code && r.code !== 200) { actionError.value = '模拟支付失败：' + (r.message || r.code); return }
    const id: any = await getMyIdentityAPI()
    payResult.value = '模拟支付成功。当前身份类型=' + (id.data ?? id) + '（0普通会员 / 1合伙人 / 2联创）；积分已入库。'
    orderStatus.value = '已支付'
  } catch (e: any) { actionError.value = '模拟支付异常：' + (e?.message || e) }
}

onMounted(loadConfig)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 32rpx; font-weight: 700; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.input { flex: 1; margin-left: 20rpx; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 12rpx; font-size: 26rpx; }
.btn-primary { background: #285b40; color: #fff; border-radius: 12rpx; }
.mt { margin-top: 16rpx; }
.result { margin-top: 16rpx; font-size: 26rpx; color: #285b40; line-height: 1.6; }
.hint { margin-top: 16rpx; font-size: 22rpx; color: #909399; line-height: 1.6; }
.error { color: #b42318; }
</style>
