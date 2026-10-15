<template>
  <view class="dividend-page">
    <view class="summary">
      <text class="title">我的分红额度</text>
      <view class="amounts"><view><strong>{{ money(quota?.remainingAmount) }}</strong><text>剩余额度上限</text></view><view><strong>{{ money(quota?.receivedAmount) }}</strong><text>累计已分红</text></view></view>
      <text class="note">{{ quota?.identityType ? `当前身份：${identityName} · 推荐人数：${quota?.referralCount || 0}` : '普通会员暂无分红额度' }}</text>
      <text class="note">额度上限不是可提现余额。{{ quota?.payoutEnabled ? '发放结果以账本为准。' : '分红发放暂未启用。' }}</text>
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
import { computed, ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getMyDividendQuotaAPI, getMyDividendLedgerAPI, type DividendQuota, type DividendPayment } from '@/apis/member'

const quota = ref<DividendQuota | null>(null)
const ledger = ref<DividendPayment[]>([])
const error = ref('')
const identityName = computed(() => quota.value?.identityType === 2 ? '联创' : '合伙人')
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
.dividend-page{min-height:100vh;padding:24rpx;background:#f2f6f5;box-sizing:border-box}.summary,.ledger-card{padding:32rpx;margin-bottom:24rpx;border:1rpx solid #e5eeea;border-radius:24rpx;background:#fff}.title{display:block;color:#28565a;font-size:34rpx;font-weight:700}.amounts{display:flex;gap:20rpx;margin:32rpx 0}.amounts view{flex:1;min-width:0}.amounts strong{display:block;color:#167a80;font-size:40rpx}.amounts text,.note{display:block;color:#829599;font-size:24rpx;line-height:1.7}.heading{display:flex;justify-content:space-between;color:#28565a;font-size:30rpx;font-weight:600}.refresh{color:#167a80;font-size:24rpx}.entry{padding:24rpx 0;border-bottom:1rpx solid #edf2f1}.entry>view{display:flex;justify-content:space-between;font-size:26rpx;color:#405e62}.entry strong{color:#167a80}.meta,.empty,.error{display:block;margin-top:10rpx;color:#87999b;font-size:23rpx}.empty{padding:48rpx 0;text-align:center}.error{color:#bd4747}
</style>
