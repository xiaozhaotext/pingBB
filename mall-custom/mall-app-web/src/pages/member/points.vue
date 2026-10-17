<template>
  <view class="app">
    <view class="mock-banner">积分账户与明细实时读取后台账本：商品积分用于商品抵扣，复购积分用于复购区抵扣，发放规则均可在后台配置。</view>

    <view class="tabs">
      <view class="tab" :class="{ active: pointsType === 1 }" @click="switchType(1)">商品积分</view>
      <view class="tab" :class="{ active: pointsType === 2 }" @click="switchType(2)">复购积分</view>
    </view>

    <view class="card">
      <text class="title">账户概览</text>
      <view class="row"><text>当前余额</text><text class="v">{{ account ? account.balance : '—' }}</text></view>
      <view class="row"><text>可用</text><text class="v">{{ account ? account.available : '—' }}</text></view>
      <view class="row"><text>冻结中</text><text class="v">{{ account ? account.frozen : '—' }}</text></view>
    </view>

    <view class="card">
      <text class="title">积分明细</text>
      <view class="list" v-if="ledger.length">
        <view class="item" v-for="(l, i) in ledger" :key="i">
          <text>{{ typeText(l.changeType) }}　{{ Number(l.amount) >= 0 ? '+' : '' }}{{ l.amount }}</text>
          <text class="sub">余额：{{ l.balanceAfter }}{{ l.remark ? '　' + l.remark : '' }}</text>
          <text class="sub">{{ l.createTime }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无积分明细</text>
    </view>

    <view v-if="loadError" class="card error">{{ loadError }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getPointsAccountAPI, getPointsLedgerAPI } from '@/apis/member'

const pointsType = ref(1)
const account = ref<any>(null)
const ledger = ref<any[]>([])
const loadError = ref('')

const loadData = async () => {
  loadError.value = ''
  account.value = null
  ledger.value = []
  try {
    const r = await getPointsAccountAPI(pointsType.value)
    account.value = r.data || null
  } catch (e) {
    loadError.value = '积分账户加载失败，请登录后重试。'
  }
  try {
    const r = await getPointsLedgerAPI(pointsType.value)
    ledger.value = r.data || []
  } catch (e) {
    ledger.value = []
    loadError.value = '积分明细加载失败，请重试。'
  }
}

const switchType = (t: number) => {
  if (pointsType.value === t) return
  pointsType.value = t
  loadData()
}

const typeText = (t: string) => {
  const map: Record<string, string> = {
    earn: '获得积分',
    consume: '消费积分',
    adjust: '后台调整',
    freeze: '冻结积分',
    unfreeze: '解冻积分',
    dividend: '每日分红',
    cofounder_grant: '联创赠送',
    partner_grant: '合伙人赠送',
  }
  return map[t] || t || '积分变动'
}

onLoad(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.tabs { display: flex; margin-bottom: 20rpx; background: #fff; border-radius: 16rpx; overflow: hidden; }
.tabs .tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #606266; }
.tabs .tab.active { color: #285b40; font-weight: 600; box-shadow: inset 0 -4rpx 0 #285b40; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 14rpx 0; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 26rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
.tip { font-size: 24rpx; color: #909399; }
.error { color: #b42318; }
</style>
