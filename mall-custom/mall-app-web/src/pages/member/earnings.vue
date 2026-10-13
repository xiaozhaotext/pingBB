<template>
  <view class="app">
    <view class="mock-banner">收益按后台配置实时记账（可提现 / 冻结 / 已提现）；提现申请经后台人工审核，当前仅记账流转，暂未接入真实代付打款。</view>

    <view class="card" v-if="account">
      <text class="title">收益账户</text>
      <view class="row"><text>累计收益</text><text class="v">{{ account.totalEarnings }}</text></view>
      <view class="row"><text>可提现</text><text class="v">{{ account.availableEarnings }}</text></view>
      <view class="row"><text>冻结中</text><text class="v">{{ account.frozenEarnings }}</text></view>
      <view class="row"><text>已提现</text><text class="v">{{ account.withdrawn }}</text></view>
    </view>

    <view class="card">
      <text class="title">收益明细</text>
      <view class="list" v-if="ledger.length">
        <view class="item" v-for="(l, i) in ledger" :key="i">
          <text>{{ typeText(l.changeType) }}　{{ Number(l.amount) >= 0 ? '+' : '' }}{{ l.amount }}</text>
          <text class="sub">余额：{{ l.balanceAfter }}{{ l.remark ? '　' + l.remark : '' }}</text>
          <text class="sub">{{ l.createTime }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无收益明细</text>
    </view>

    <view class="card">收益来源：直属会员购买权益的直推奖励、每日分红、分享下单奖励；规则与开关均可在后台「平台设置 - 分销与分红」中调整。</view>
    <view v-if="loadError" class="card error">{{ loadError }}</view>

    <view class="card">
      <text class="title">申请提现</text>
      <input class="input" v-model="withdrawAmount" placeholder="输入提现金额" type="digit" />
      <button class="btn-primary" @click="handleWithdraw">申请提现</button>
    </view>

    <view class="card">
      <text class="title">提现申请列表</text>
      <view class="list" v-if="withdraws.length">
        <view class="item" v-for="(w, i) in withdraws" :key="i">
          <text>金额：{{ w.amount }} 手续费：{{ w.feeAmount }} 实到：{{ w.actualAmount }}</text>
          <text class="sub">状态：{{ statusText(w.status) }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无提现申请</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getEarningsAccountAPI, applyWithdrawAPI, getWithdrawListAPI, getEarningsLedgerAPI } from '@/apis/member'

const account = ref<any>(null)
const withdraws = ref<any[]>([])
const ledger = ref<any[]>([])
const withdrawAmount = ref('')
const loadError = ref('')

const loadData = async () => {
  loadError.value = ''
  try { const r = await getEarningsAccountAPI(); account.value = r.data }
  catch (e) { account.value = null; loadError.value = '收益账户加载失败，请登录后重试。' }
  try { const r = await getWithdrawListAPI(); withdraws.value = r.data || [] }
  catch (e) { withdraws.value = []; loadError.value = '提现记录加载失败，请重试。' }
  try { const r = await getEarningsLedgerAPI(); ledger.value = r.data || [] }
  catch (e) { ledger.value = [] }
}

const handleWithdraw = async () => {
  if (!withdrawAmount.value) return
  try {
    await applyWithdrawAPI({ amount: withdrawAmount.value, idempotencyKey: 'W' + Date.now() + '_' + Math.floor(Math.random() * 1000) })
    uni.showToast({ title: '已提交提现申请', icon: 'success' })
    loadData()
  } catch (e) {}
}

const statusText = (s: number) => {
  return ['待审核', '审核通过', '已完成', '已完成', '已拒绝'][s] || '未知'
}

const typeText = (t: string) => {
  const map: Record<string, string> = {
    referral_reward: '直推奖励',
    dividend: '每日分红',
    share_bonus: '分享下单奖励',
    simulate_reward: '后台发放',
    withdraw_reject: '提现退回',
    withdraw_done: '提现完成',
  }
  return map[t] || t || '收益'
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 14rpx 0; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.input { margin: 16rpx 0; height: 80rpx; border: 1rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; }
.btn { background: #f4f4f5; color: #606266; border-radius: 12rpx; }
.btn-primary { margin-top: 10rpx; background: #285b40; color: #fff; border-radius: 12rpx; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 26rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
.tip { font-size: 24rpx; color: #909399; }
.error { color: #b42318; }
</style>
