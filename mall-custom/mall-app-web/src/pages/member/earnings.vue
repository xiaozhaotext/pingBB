<template>
  <view class="app">
    <view class="mock-banner">收益与提现均为模拟：金额非真实资产，提现仅状态流转，无真实打款；手续费 0.6% 为待审批参数。</view>

    <view class="card" v-if="account">
      <text class="title">收益账户（模拟）</text>
      <view class="row"><text>累计收益</text><text class="v">{{ account.totalEarnings }}</text></view>
      <view class="row"><text>可提现</text><text class="v">{{ account.availableEarnings }}</text></view>
      <view class="row"><text>冻结中</text><text class="v">{{ account.frozenEarnings }}</text></view>
      <view class="row"><text>已提现</text><text class="v">{{ account.withdrawn }}</text></view>
    </view>

    <view class="card">
      <text class="title">模拟发放收益（演示/测试用）</text>
      <input class="input" v-model="rewardAmount" placeholder="输入金额" type="digit" />
      <button class="btn" @click="handleReward">发放</button>
    </view>

    <view class="card">
      <text class="title">申请提现（模拟）</text>
      <input class="input" v-model="withdrawAmount" placeholder="输入提现金额" type="digit" />
      <button class="btn-primary" @click="handleWithdraw">申请提现</button>
    </view>

    <view class="card">
      <text class="title">提现申请列表（模拟）</text>
      <view class="list" v-if="withdraws.length">
        <view class="item" v-for="(w, i) in withdraws" :key="i">
          <text>金额：{{ w.amount }} 手续费：{{ w.feeAmount }} 实到：{{ w.actualAmount }}</text>
          <text class="sub">状态：{{ statusText(w.status) }}（模拟）</text>
        </view>
      </view>
      <text class="tip" v-else>暂无提现申请</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getEarningsAccountAPI, getEarningsLedgerAPI, simulateRewardAPI, applyWithdrawAPI, getWithdrawListAPI } from '@/apis/member'

const account = ref<any>(null)
const withdraws = ref<any[]>([])
const rewardAmount = ref('')
const withdrawAmount = ref('')

const loadData = async () => {
  try { const r = await getEarningsAccountAPI(); account.value = r.data } catch (e) {}
  try { const r = await getWithdrawListAPI(); withdraws.value = r.data || [] } catch (e) {}
}

const handleReward = async () => {
  if (!rewardAmount.value) return
  try {
    await simulateRewardAPI({ amount: rewardAmount.value, bizId: 'R' + Date.now(), idempotencyKey: 'R' + Date.now() + '_' + Math.floor(Math.random() * 1000) })
    uni.showToast({ title: '已模拟发放', icon: 'success' })
    loadData()
  } catch (e) {}
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
  return ['申请', '审核通过', '模拟处理中', '完成', '拒绝'][s] || '未知'
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
</style>
