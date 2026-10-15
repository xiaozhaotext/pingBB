<template>
  <view class="dividend-page">
    <view class="summary">
      <text class="title">我的分红额度</text>
      <view class="amounts"><view><strong>{{ money(quota?.capAmount) }}</strong><text>个人累计分红上限</text></view><view><strong>{{ money(quota?.remainingAmount) }}</strong><text>尚可领取额度</text></view></view>
      <text class="note">当前身份：{{ quota?.identityName || '游客' }} · 有效直邀会员：{{ quota?.referralCount || 0 }}</text>
      <view v-if="quota?.partnerShares" class="pool-limit"><view><strong>合伙人池</strong><text>{{ quota.partnerShares }} 份</text></view><view><text>个人累计上限</text><strong>{{ money(quota.partnerCap) }}</strong></view><view><text>已领取 {{ money(quota.partnerReceived) }}</text><text>剩余 {{ money(quota.partnerRemaining) }}</text></view></view>
      <view v-if="quota?.cofounderShares" class="pool-limit"><view><strong>联创池</strong><text>{{ quota.cofounderShares }} 份</text></view><view><text>个人累计上限</text><strong>{{ money(quota.cofounderCap) }}</strong></view><view><text>已领取 {{ money(quota.cofounderReceived) }}</text><text>剩余 {{ money(quota.cofounderRemaining) }}</text></view></view>
      <text class="note">以上是个人累计领取上限，不限制池内总余额；实际分红按池内总份数计算。{{ quota?.payoutEnabled ? '发放结果以账本为准。' : '分红发放暂未启用。' }}</text>
    </view>
    <view class="ledger-card">
      <view class="heading"><text>已发放分红明细</text><text class="refresh" @click="load">刷新</text></view>
      <view v-if="error" class="error" @click="load">{{ error }}，点击重试</view>
      <view v-else-if="!ledger.length" class="empty">暂无已发放分红</view>
      <view v-for="item in ledger" :key="item.id" class="entry">
        <view><text>{{ item.create_time || item.stat_date }}</text><strong>+{{ money(item.dividend_amount) }}</strong></view>
        <text class="meta">{{ item.pool_code === 'OPERATIONS' ? '运营池' : item.pool_code === 'COFOUNDER' ? '联创池' : '合伙人池' }} · 分红日期 {{ item.stat_date }}<template v-if="item.cumulative_received != null"> · 发放后累计 {{ money(item.cumulative_received) }}</template></text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getMyDividendQuotaAPI, getMyDividendLedgerAPI, type DividendQuota, type DividendPayment } from '@/apis/member'

const quota = ref<DividendQuota | null>(null)
const ledger = ref<DividendPayment[]>([])
const error = ref('')
const money = (value: number | undefined) => value == null ? '—' : `¥${Number(value).toFixed(2)}`
let timer: ReturnType<typeof setInterval> | null = null
async function load() {
  const results = await Promise.allSettled([getMyDividendQuotaAPI(), getMyDividendLedgerAPI()])
  if (results[0].status === 'fulfilled') quota.value = results[0].value.data
  if (results[1].status === 'fulfilled') ledger.value = results[1].value.data || []
  error.value = results.some(result => result.status === 'rejected') ? '部分分红数据加载失败' : ''
}
onShow(() => {
  load()
  if (timer) clearInterval(timer)
  timer = setInterval(load, 15000)
})
onHide(() => { if (timer) clearInterval(timer); timer = null })
</script>

<style lang="scss" scoped>
.dividend-page{min-height:100vh;padding:24rpx;background:#f2f6f5;box-sizing:border-box}.summary,.ledger-card{padding:32rpx;margin-bottom:24rpx;border:1rpx solid #e5eeea;border-radius:24rpx;background:#fff}.title{display:block;color:#28565a;font-size:34rpx;font-weight:700}.amounts{display:flex;gap:20rpx;margin:32rpx 0}.amounts view{flex:1;min-width:0}.amounts strong{display:block;color:#167a80;font-size:40rpx}.amounts text,.note{display:block;color:#829599;font-size:24rpx;line-height:1.7}.pool-limit{margin:22rpx 0;padding:22rpx;border-radius:16rpx;background:#f2f8f7}.pool-limit view{display:flex;justify-content:space-between;align-items:center;margin:8rpx 0;color:#46676b;font-size:25rpx}.pool-limit strong{color:#167a80;font-size:29rpx}.heading{display:flex;justify-content:space-between;color:#28565a;font-size:30rpx;font-weight:600}.refresh{color:#167a80;font-size:24rpx}.entry{padding:24rpx 0;border-bottom:1rpx solid #edf2f1}.entry>view{display:flex;justify-content:space-between;font-size:26rpx;color:#405e62}.entry strong{color:#167a80}.meta,.empty,.error{display:block;margin-top:10rpx;color:#87999b;font-size:23rpx}.empty{padding:48rpx 0;text-align:center}.error{color:#bd4747}
</style>
