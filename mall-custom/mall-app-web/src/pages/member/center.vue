<template>
  <view class="app">
    <view class="mock-banner">会员中心（身份 / 双积分 / 收益概览）。合伙人、联创、收益、分红按后台配置实时记账；提现需后台人工审核，打款通道尚未接入。</view>
    <view v-if="loadError" class="error-banner">{{ loadError }} <text @click="loadData">重试</text></view>

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
        <text class="label">可提现收益</text>
        <text class="value">{{ earningsAvailable }}</text>
      </view>
    </view>

    <view class="grid">
      <view class="grid-item" @click="go('/pages/member/invitation')">
        <text class="t">邀请与推荐</text>
        <text class="s">我的邀请码 / 下级</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/partner')">
        <text class="t">合伙人权益</text>
        <text class="s">3999 权益方案</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/cofounder')">
        <text class="t">联创权益</text>
        <text class="s">10000 权益方案</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/earnings')">
        <text class="t">收益与提现</text>
        <text class="s">收益 / 申请提现</text>
      </view>
      <view class="grid-item" @click="go('/pages/member/dividend')">
        <text class="t">每日分红池</text>
        <text class="s">多劳多得分配</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyIdentityAPI, getPointsAccountAPI, getEarningsAccountAPI } from '@/apis/member'

const identityText = ref('加载中')
const productPoints = ref<string | number>('加载中')
const repurchasePoints = ref<string | number>('加载中')
const earningsAvailable = ref('加载中')
const loadError = ref('')

const IDENTITY_MAP: Record<number, string> = { 0: '普通会员', 1: '合伙人', 2: '联创' }

const loadData = async () => {
  loadError.value = ''
  try {
    const idRes = await getMyIdentityAPI()
    identityText.value = idRes.data?.identityType == null ? '普通会员' : (IDENTITY_MAP[idRes.data.identityType] || '未知身份')
  } catch (e) { identityText.value = '获取失败'; loadError.value = '会员信息加载失败，请确认已登录且后端可用。' }
  try {
    const p = await getPointsAccountAPI(1)
    productPoints.value = p.data?.balance ?? '未开户'
  } catch (e) { productPoints.value = '获取失败'; loadError.value = '部分会员数据加载失败，请重试。' }
  try {
    const r = await getPointsAccountAPI(2)
    repurchasePoints.value = r.data?.balance ?? '未开户'
  } catch (e) { repurchasePoints.value = '获取失败'; loadError.value = '部分会员数据加载失败，请重试。' }
  try {
    const e = await getEarningsAccountAPI()
    earningsAvailable.value = e.data?.availableEarnings == null ? '未开户' : String(e.data.availableEarnings)
  } catch (e) { earningsAvailable.value = '获取失败'; loadError.value = '部分会员数据加载失败，请重试。' }
}

const go = (url: string) => {
  uni.navigateTo({ url })
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.error-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff0f0; color: #b42318; font-size: 24rpx; }
.error-banner text { margin-left: 16rpx; text-decoration: underline; }
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
