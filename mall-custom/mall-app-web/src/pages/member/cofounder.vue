<template>
  <view class="app">
    <view class="mock-banner">联创权益为模拟方案：下单与支付均为模拟，不会产生真实扣款，也不会自动发放真实分红。三份分红、分享奖励上限仅为规则草案。</view>

    <view class="card" v-if="config">
      <text class="title">联创 10000 权益方案（模拟）</text>
      <view class="row"><text>权益价格</text><text class="v">{{ config.price }} 元(模拟)</text></view>
      <view class="row"><text>赠送联创积分</text><text class="v">{{ config.cofounderPoints }}</text></view>
      <view class="row"><text>分享奖励上限</text><text class="v">{{ config.shareRewardCap }} 元(待确认)</text></view>
      <view class="row"><text>分红份数</text><text class="v">{{ config.dividendCount }} 份(规则草案)</text></view>
    </view>

    <view class="card">模拟购买入口暂未对公网开放。权益方案来自后端配置；身份只能由授权后台调整。</view>
    <view v-if="loadError" class="card error">{{ loadError }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCofounderConfigAPI } from '@/apis/member'

const config = ref<any>(null)
const loadError = ref('')

const loadConfig = async () => {
  try { const r = await getCofounderConfigAPI(); config.value = r.data; if (!r.data) loadError.value = '暂无已启用的权益方案。' }
  catch (e) { loadError.value = '权益方案加载失败，请登录后重试。' }
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
.error { color: #b42318; }
</style>
