<template>
  <view class="app">
    <view class="mock-banner">每日贡献奖励池仅展示当前账号的模拟计算结果。贡献核验与分配由授权后台负责；实际分红尚未启用。</view>
    <view v-if="loadError" class="card error">{{ loadError }}</view>
    <view class="card">当前日期：{{ displayDate }} <button class="btn" @click="loadSnapshot">刷新结果</button></view>

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
import { getMyDividendSnapshotAPI } from '@/apis/member'

const today = () => new Date().setHours(0, 0, 0, 0)
const statDate = today()

const snapshot = ref<any[]>([])
const loadError = ref('')
const displayDate = new Date(statDate).toLocaleDateString('zh-CN')

const loadSnapshot = async () => {
  loadError.value = ''
  try { const r = await getMyDividendSnapshotAPI(statDate); snapshot.value = r.data || [] }
  catch (e) { snapshot.value = []; loadError.value = '分配记录加载失败，请登录后重试。' }
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
