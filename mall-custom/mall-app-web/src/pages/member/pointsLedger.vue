<template>
  <view class="app">
    <view class="card">
      <text class="title">积分账户</text>
      <view class="row"><text>商品积分</text><text class="v">{{ productBalance }}</text></view>
      <view class="row"><text>复购积分</text><text class="v">{{ rebuyBalance }}</text></view>
    </view>

    <view class="card">
      <text class="title">积分明细</text>
      <view class="tabs">
        <text :class="['tab', tab === 0 ? 'active' : '']" @click="switchTab(0)">全部</text>
        <text :class="['tab', tab === 1 ? 'active' : '']" @click="switchTab(1)">商品积分</text>
        <text :class="['tab', tab === 2 ? 'active' : '']" @click="switchTab(2)">复购积分</text>
      </view>
      <view class="list" v-if="ledger.length">
        <view class="item" v-for="(l, i) in ledger" :key="i">
          <text>{{ typeText(l.changeType) }}　{{ Number(l.amount) >= 0 ? '+' : '' }}{{ l.amount }}（{{ pointsTypeText(l.pointsType) }}）</text>
          <text class="sub">余额：{{ l.balanceAfter }}{{ l.remark ? '　' + l.remark : '' }}</text>
          <text class="sub">{{ l.createTime }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无积分明细</text>
    </view>

    <view class="card">积分来源：购买权益、复购区消费返还、直推奖励、每日分红；规则与开关均可在后台「平台设置 - 分销与分红」中调整。</view>
    <view v-if="loadError" class="card error">{{ loadError }}</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getPointsAccountAPI, getPointsLedgerAPI } from '@/apis/member'

const productBalance = ref<string | number>('—')
const rebuyBalance = ref<string | number>('—')
const ledger = ref<any[]>([])
const loadError = ref('')
const tab = ref(0)

const loadData = async () => {
  loadError.value = ''
  try { const r = await getPointsAccountAPI(1); productBalance.value = r.data?.balance ?? '未开户' }
  catch (e) { productBalance.value = '—' }
  try { const r = await getPointsAccountAPI(2); rebuyBalance.value = r.data?.balance ?? '未开户' }
  catch (e) { rebuyBalance.value = '—' }
  await loadLedger()
}

const loadLedger = async () => {
  try {
    const r = await getPointsLedgerAPI(tab.value === 0 ? undefined : tab.value)
    ledger.value = r.data || []
  } catch (e) {
    ledger.value = []
    loadError.value = '积分明细加载失败，请登录后重试。'
  }
}

const switchTab = (t: number) => {
  tab.value = t
  loadLedger()
}

const pointsTypeText = (t: number) => {
  return t === 1 ? '商品积分' : t === 2 ? '复购积分' : '积分'
}

const typeText = (t: string) => {
  const map: Record<string, string> = {
    earn: '获得',
    consume: '消费',
    freeze: '冻结',
    unfreeze: '解冻',
    adjust: '调整',
    dividend: '分红',
  }
  return map[t] || t || '积分变动'
}

onLoad((options: any) => {
  if (options?.type === '1' || options?.type === '2') {
    tab.value = Number(options.type)
  }
})

onShow(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; padding: 14rpx 0; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.tabs { display: flex; gap: 16rpx; margin-bottom: 16rpx; }
.tab { padding: 10rpx 24rpx; border-radius: 999rpx; background: #f4f4f5; color: #606266; font-size: 24rpx; }
.tab.active { background: #285b40; color: #fff; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 26rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
.tip { font-size: 24rpx; color: #909399; }
.error { color: #b42318; }
</style>
