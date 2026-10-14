<template>
  <view class="app">
    <view v-if="loadError" class="card error">{{ loadError }}</view>

    <view class="card" v-if="info">
      <text class="title">我的分红</text>
      <view class="row"><text>我的身份</text><text class="v">{{ info.identityName }}</text></view>
      <view class="row"><text>分红份数</text><text class="v">{{ info.shares }} 份</text></view>
      <view class="row"><text>今日分红</text><text class="v highlight">¥{{ fmt(info.todayDividend) }}</text></view>
      <view class="row"><text>累计分红</text><text class="v">¥{{ fmt(info.totalDividend) }}</text></view>
      <view class="row"><text>我的推荐人数</text><text class="v">{{ info.referralCount }} 人</text></view>
      <view class="row"><text>分红封顶</text><text class="v">¥{{ fmt(info.capAmount) }}</text></view>
      <view class="tip">封顶规则：基础 ¥{{ fmt(info.capBase) }} + ¥{{ fmt(info.capPerReferral) }}×推荐人数，上限 ¥{{ fmt(info.capMax) }}。</view>
      <view class="tip" v-if="!info.distributionEnabled">每日分红发放当前未启用，数据为历史记录。</view>
    </view>

    <view class="card">
      <text class="title">分红记录</text>
      <view class="list" v-if="records.length">
        <view class="item" v-for="(r, i) in records" :key="i">
          <text>{{ r.stat_date }}　{{ r.shares }}份　+¥{{ fmt(r.dividend_amount) }}</text>
          <text class="sub">累计：¥{{ fmt(r.cumulative_received) }}　封顶：¥{{ fmt(r.cap_amount) }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无分红记录</text>
      <button class="btn" v-if="hasMore" @click="loadMore">加载更多</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDividendInfoAPI, getDividendRecordsAPI } from '@/apis/member'

const info = ref<any>(null)
const records = ref<any[]>([])
const loadError = ref('')
const page = ref(1)
const hasMore = ref(false)
const PAGE_SIZE = 20

const fmt = (v: any) => {
  const n = Number(v || 0)
  return n.toFixed(2)
}

const loadInfo = async () => {
  try {
    const r = await getDividendInfoAPI()
    info.value = r.data || null
  } catch (e) {
    loadError.value = '分红信息加载失败，请登录后重试。'
  }
}

const loadRecords = async (reset = false) => {
  if (reset) { page.value = 1; records.value = [] }
  try {
    const r = await getDividendRecordsAPI({ page: page.value, size: PAGE_SIZE })
    const list = r.data || []
    records.value = reset ? list : records.value.concat(list)
    hasMore.value = list.length >= PAGE_SIZE
  } catch (e) {
    if (reset) records.value = []
  }
}

const loadMore = () => {
  page.value += 1
  loadRecords(false)
}

onMounted(async () => {
  await loadInfo()
  await loadRecords(true)
})
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 14rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.row .v.highlight { color: #e65a3c; font-size: 32rpx; }
.tip { display: block; margin-top: 12rpx; font-size: 24rpx; color: #909399; line-height: 1.6; }
.error { color: #b42318; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 26rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
.btn { background: #f4f4f5; color: #606266; border-radius: 12rpx; margin-top: 12rpx; font-size: 26rpx; }
</style>
