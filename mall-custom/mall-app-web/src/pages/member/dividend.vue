<template>
  <view class="app">
    <view v-if="loadError" class="card error">{{ loadError }}</view>

    <template v-if="tri?.enabled">
      <view class="card">
        <text class="title">我的身份：{{ tierNames[tri.tier || 'VISITOR'] }}</text>
        <view class="row"><text>有效直邀会员</text><text>{{ tri.directPaidCount || 0 }} 人</text></view>
        <text class="tip" v-if="tri.tier === 'VISITOR'">注册为游客；在“我的”单独购买3999元会员后开通会员权益。</text>
        <text class="tip" v-else>每笔会员支付成功后触发分配，按全池有效总份数计算。累计已领取金额在升级后继续保留。</text>
        <button v-if="tri.customTravelEligible" class="btn" @click="openTravel">定制旅游路线</button>
      </view>
      <view class="card" v-for="pool in tri.pools" :key="pool.code">
        <text class="title">{{ poolNames[pool.code] }}</text>
        <template v-if="pool.eligible">
          <view class="row"><text>当前份数</text><text>{{ pool.shares }} 份</text></view>
          <view class="row"><text>累计领取上限</text><text>¥{{ fmt(pool.cap) }}</text></view>
          <view class="row"><text>已领取</text><text>¥{{ fmt(pool.received) }}</text></view>
          <view class="row"><text>剩余额度</text><text>¥{{ fmt(pool.remaining) }}</text></view>
        </template>
        <text class="tip" v-else>{{ pool.code === 'COFOUNDER' ? '直邀9名付费会员晋升联创后，获得本池1份分红，独立累计上限1200元。' : '成为会员后享有本池分红。' }}</text>
      </view>
      <view class="card"><text class="title">分红记录</text><text class="tip">份数表示本次分红时，您持有的份数 / 全池有效总份数。</text><view v-for="r in triRecords" :key="r.id" class="item"><text>{{ poolNames[r.pool_code] }}　+¥{{ fmt(r.amount) }}</text><text class="sub">{{ formatTime(r.created_at) }} · {{ tierNames[r.identity_snapshot] }} · {{ r.shares }}/{{ r.total_shares }}份</text></view><text v-if="!triRecords.length" class="tip">暂无分红记录</text><button class="btn" v-if="hasMore" @click="loadMore">加载更多</button></view>
    </template>
    <view class="card" v-if="!tri?.enabled && info">
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

    <view class="card" v-if="!tri?.enabled">
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
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getDividendInfoAPI, getDividendRecordsAPI } from '@/apis/member'
import { getTriPoolsAPI, getTriPoolRecordsAPI, tierNames, poolNames, type TriSummary, type TriRecord } from '@/apis/triPools'
const tri = ref<TriSummary | null>(null)
const triRecords = ref<TriRecord[]>([])
const openTravel = () => uni.navigateTo({ url: '/pages/member/travel' })

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
const formatTime = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date)
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
  if (tri.value?.enabled) loadTriRecords()
  else loadRecords(false)
}

const loadTriRecords = async () => {
  try {
    const list = (await getTriPoolRecordsAPI(page.value)).data || []
    triRecords.value = page.value === 1 ? list : triRecords.value.concat(list)
    hasMore.value = list.length >= PAGE_SIZE
  } catch { page.value = Math.max(1, page.value - 1); loadError.value = '分红记录加载失败，请重试' }
}

onShow(async () => {
  loadError.value = ''
  page.value = 1
  try {
    tri.value = (await getTriPoolsAPI()).data
    if (tri.value.enabled) { await loadTriRecords(); return }
  } catch { loadError.value = '分红信息加载失败，请重试'; return }
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
