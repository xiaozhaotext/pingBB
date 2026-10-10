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
      <button class="btn-primary" v-if="!paid" @click="handleBuy">模拟购买并升级为合伙人</button>
      <view v-else class="paid">✓ 已模拟支付，身份已升级为合伙人（可在会员中心查看）</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPartnerConfigAPI, createPartnerOrderAPI, payPartnerAPI } from '@/apis/member'

const config = ref<any>(null)
const paid = ref(false)

const loadConfig = async () => {
  try { const r = await getPartnerConfigAPI(); config.value = r.data } catch (e) {}
}

const handleBuy = async () => {
  const bizId = 'PARTNER_' + Date.now() + '_' + Math.floor(Math.random() * 1000)
  try {
    await createPartnerOrderAPI({ bizId })
    await payPartnerAPI({ bizId })
    paid.value = true
    uni.showToast({ title: '已模拟升级为合伙人', icon: 'success' })
  } catch (e) {}
}

onMounted(loadConfig)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 32rpx; font-weight: 700; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.btn-primary { background: #285b40; color: #fff; border-radius: 12rpx; }
.paid { color: #285b40; font-size: 28rpx; font-weight: 600; text-align: center; padding: 20rpx; }
</style>
