<template>
  <view class="app">
    <view class="mock-banner">每日分红池（多劳多得）：按"个人贡献 / 当日总贡献"比例分配，严禁平均/写死/随机。全部为模拟计算，无真实分红入账与打款。</view>

    <view class="card">
      <text class="title">1. 记录贡献事件（可核验真实业务）</text>
      <input class="input" v-model="bizId" placeholder="业务单号(订单ID)" />
      <input class="input" v-model="contribution" placeholder="贡献值(正=产生, 负=退款冲回)" type="digit" />
      <button class="btn" @click="handleRecord">记录完成订单贡献</button>
      <button class="btn-warn" @click="handleRefund">记录退款负贡献</button>
    </view>

    <view class="card">
      <text class="title">2. 模拟每日分配（基于贡献比例）</text>
      <input class="input" v-model="poolTotal" placeholder="模拟分红资金池(可空,留空=总贡献)" type="digit" />
      <button class="btn-primary" @click="handleAllocate">执行模拟分配</button>
      <text class="tip">参与会员数：{{ allocateCount }}</text>
    </view>

    <view class="card">
      <text class="title">3. 分配明细（模拟）</text>
      <view class="list" v-if="snapshot.length">
        <view class="item" v-for="(s, i) in snapshot" :key="i">
          <text>会员ID：{{ s.memberId }}</text>
          <text class="sub">贡献：{{ s.contributionValue }} 比例：{{ (s.ratio * 100).toFixed(4) }}% 模拟分配：{{ s.estimatedAmount }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无分配记录，请先执行分配</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { recordContributionAPI, allocateDividendAPI, getDividendSnapshotAPI } from '@/apis/member'

const today = () => new Date().setHours(0, 0, 0, 0)
const statDate = today()

const bizId = ref('')
const contribution = ref('')
const poolTotal = ref('')
const allocateCount = ref(0)
const snapshot = ref<any[]>([])

const loadSnapshot = async () => {
  try { const r = await getDividendSnapshotAPI(statDate); snapshot.value = r.data || [] } catch (e) {}
}

const handleRecord = async () => {
  if (!bizId.value || !contribution.value) { uni.showToast({ title: '请填写单号与贡献值', icon: 'none' }); return }
  try {
    await recordContributionAPI({ eventType: 'order_complete', bizId: bizId.value, contributionValue: contribution.value, statDate })
    uni.showToast({ title: '已记录贡献', icon: 'success' })
    bizId.value = ''; contribution.value = ''
  } catch (e) {}
}

const handleRefund = async () => {
  if (!bizId.value) { uni.showToast({ title: '请填写单号', icon: 'none' }); return }
  try {
    await recordContributionAPI({ eventType: 'order_refund', bizId: bizId.value, contributionValue: '-' + (contribution.value || '1'), statDate })
    uni.showToast({ title: '已记录退款冲回', icon: 'success' })
    bizId.value = ''
  } catch (e) {}
}

const handleAllocate = async () => {
  const calcBatch = 'BATCH_' + statDate + '_' + Math.floor(Math.random() * 100000)
  try {
    const r = await allocateDividendAPI({ statDate, poolTotal: poolTotal.value || undefined, calcBatch })
    allocateCount.value = r.data ?? 0
    uni.showToast({ title: '分配完成(模拟)', icon: 'success' })
    loadSnapshot()
  } catch (e) {}
}

onMounted(loadSnapshot)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.input { margin: 12rpx 0; height: 80rpx; border: 1rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; }
.btn { background: #f4f4f5; color: #606266; border-radius: 12rpx; margin-top: 10rpx; }
.btn-warn { background: #fdf0ed; color: #e65a3c; border-radius: 12rpx; margin-top: 10rpx; }
.btn-primary { background: #285b40; color: #fff; border-radius: 12rpx; margin-top: 10rpx; }
.tip { display: block; margin-top: 12rpx; font-size: 24rpx; color: #909399; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 26rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
</style>
