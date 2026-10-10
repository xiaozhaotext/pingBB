<template>
  <view class="app">
    <view class="mock-banner">会员中心（身份 / 双积分 / 收益概览）。合伙人、联创、收益、分红均为模拟能力，不涉及真实资金。</view>

    <view class="card">
      <view class="row">
        <text class="label">当前身份</text>
        <text class="value">{{ identityText }}</text>
      </view>
      <view class="row">
        <text class="label">商品积分</text>
        <text class="value">{{ productPoints }} 分</text>
      </view>
      <view class="row">
        <text class="label">复购积分</text>
        <text class="value">{{ repurchasePoints }} 分</text>
      </view>
      <view class="row">
        <text class="label">可提现收益(模拟)</text>
        <text class="value">{{ earningsAvailable }}</text>
      </view>
    </view>

    <view class="grid">
      <view class="grid-item" @click="go('/pages/member/invitation')">
        <text class="t">邀请与推荐</text>
        <text class="s">我的邀请码 / 下级</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/partner')">
        <text class="t">合伙人(模拟)</text>
        <text class="s">3999 权益方案</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/cofounder')">
        <text class="t">联创(模拟)</text>
        <text class="s">10000 权益方案</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/earnings')">
        <text class="t">收益与提现(模拟)</text>
        <text class="s">收益 / 申请提现</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/dividend')">
        <text class="t">每日分红池(模拟)</text>
        <text class="s">多劳多得分配</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyIdentityAPI, getPointsAccountAPI, getEarningsAccountAPI } from '@/apis/member'

const identityText = ref('普通会员')
const productPoints = ref(0)
const repurchasePoints = ref(0)
const earningsAvailable = ref('0.00')

const IDENTITY_MAP: Record<number, string> = { 0: '普通会员', 1: '合伙人', 2: '联创' }

const loadData = async () => {
  try {
    const idRes = await getMyIdentityAPI()
    if (idRes.data && idRes.data.identityType != null) {
      identityText.value = IDENTITY_MAP[idRes.data.identityType] || '普通会员'
    }
  } catch (e) {}
  try {
    const p = await getPointsAccountAPI(1)
    productPoints.value = p.data?.balance ?? 0
  } catch (e) {}
  try {
    const r = await getPointsAccountAPI(2)
    repurchasePoints.value = r.data?.balance ?? 0
  } catch (e) {}
  try {
    const e = await getEarningsAccountAPI()
    earningsAvailable.value = (e.data?.availableEarnings ?? '0.00').toString()
  } catch (e) {}
}

const go = (url: string) => {
  uni.navigateTo({ url })
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 10rpx 24rpx; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f2f2f2; }
.row:last-child { border-bottom: none; }
.label { color: #606266; font-size: 28rpx; }
.value { color: #303133; font-size: 30rpx; font-weight: 600; }
.grid { margin-top: 24rpx; display: flex; flex-wrap: wrap; gap: 20rpx; }
.grid-item { width: calc(50% - 10rpx); box-sizing: border-box; background: #fff; border-radius: 16rpx; padding: 30rpx 24rpx; }
.grid-item .t { display: block; font-size: 30rpx; font-weight: 600; color: #303133; }
.grid-item .s { display: block; margin-top: 8rpx; font-size: 24rpx; color: #909399; }
</style>
