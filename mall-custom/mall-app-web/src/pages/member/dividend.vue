<template>
  <view class="app">
    <view class="mock-banner">每日贡献奖励池仅展示当前账号的模拟计算结果。贡献核验与分配由授权后台负责；实际分红尚未启用。</view>
    <view v-if="loadError" class="card error">{{ loadError }}</view>
    <view class="card">当前日期：{{ displayDate }} <button class="btn" @click="loadSnapshot">刷新结果</button></view>

    <view class="card">
      <text class="title">记录贡献事件（真实入库，幂等）</text>
      <input class="input" v-model="bizId" placeholder="业务单号(订单ID)" />
      <input class="input" v-model="contribution" placeholder="贡献值(正=产生, 负=退款冲回)" type="digit" />
      <button class="btn-primary" @click="handleContribution">记录完成订单贡献</button>
      <button class="btn-warn" @click="handleRefund">记录退款冲回</button>
      <view v-if="actionMsg" class="result">{{ actionMsg }}</view>
    </view>

    <view class="card">
      <text class="title">执行模拟分配（比例 = 个人贡献 / 当日总贡献）</text>
      <view class="row"><text>资金池总额(可留空)</text><input class="input" v-model="poolTotal" placeholder="留空则按总贡献" type="digit" /></view>
      <button class="btn-primary" @click="handleAllocate">执行模拟分配</button>
      <view v-if="allocateMsg" class="result">{{ allocateMsg }}</view>
    </view>

    <view class="card">
      <text class="title">我的模拟分配明细</text>
      <view class="list" v-if="snapshot.length">
        <view class="item" v-for="(s, i) in snapshot" :key="i">
          <text>统计日期：{{ displayDate }}</text>
          <text class="sub">贡献：{{ s.contributionValue }} 比例：{{ (s.ratio * 100).toFixed(4) }}% 模拟分配：{{ s.estimatedAmount }}</text>
        </view>
      </view>
      <text class="tip" v-else>当前日期暂无已核验的模拟分配记录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyDividendSnapshotAPI, recordContributionAPI, allocateDividendAPI } from '@/apis/member'

const today = () => new Date().setHours(0, 0, 0, 0)
const statDate = today()

const snapshot = ref<any[]>([])
const loadError = ref('')
const displayDate = new Date(statDate).toLocaleDateString('zh-CN')
const bizId = ref('')
const contribution = ref('')
const poolTotal = ref('')
const actionMsg = ref('')
const allocateMsg = ref('')

const loadSnapshot = async () => {
  loadError.value = ''
  try { const r = await getMyDividendSnapshotAPI(statDate); snapshot.value = r.data || [] }
  catch (e) { snapshot.value = []; loadError.value = '分配记录加载失败，请登录后重试。' }
}

const handleContribution = async () => {
  actionMsg.value = ''
  if (!bizId.value || !contribution.value) { actionMsg.value = '请填写单号与贡献值'; return }
  try {
    const r: any = await recordContributionAPI({
      eventType: 'order_complete', bizId: bizId.value, contributionValue: contribution.value, statDate,
    })
    actionMsg.value = (r && r.code && r.code !== 200) ? ('记录失败：' + r.message) : '已记录贡献（真实入库，幂等）。'
    loadSnapshot()
  } catch (e: any) { actionMsg.value = '记录异常：' + (e?.message || e) }
}

const handleRefund = async () => {
  actionMsg.value = ''
  if (!bizId.value) { actionMsg.value = '请填写单号'; return }
  try {
    const r: any = await recordContributionAPI({
      eventType: 'order_refund', bizId: bizId.value, contributionValue: '-' + (contribution.value || '1'), statDate,
    })
    actionMsg.value = (r && r.code && r.code !== 200) ? ('冲回失败：' + r.message) : '已记录退款冲回（负贡献）。'
    loadSnapshot()
  } catch (e: any) { actionMsg.value = '冲回异常：' + (e?.message || e) }
}

const handleAllocate = async () => {
  allocateMsg.value = ''
  const calcBatch = 'BATCH_' + statDate + '_' + Math.floor(Math.random() * 100000)
  try {
    const r: any = await allocateDividendAPI({ statDate, poolTotal: poolTotal.value || undefined, calcBatch })
    allocateMsg.value = (r && r.code && r.code !== 200) ? ('分配失败：' + r.message) : ('模拟分配完成，生成 ' + (r.data ?? 0) + ' 条记录。')
    loadSnapshot()
  } catch (e: any) { allocateMsg.value = '分配异常：' + (e?.message || e) }
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
.error { color: #b42318; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 26rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
</style>
