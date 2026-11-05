<template>
  <view class="notice-page">
    <view v-if="loading" class="empty">正在加载通知…</view>
    <view v-else-if="error" class="empty" @click="loadNotices">{{ error }}，点击重试</view>
    <view v-else-if="notices.length === 0" class="empty">
      <text>暂无通知</text>
    </view>
    <view v-for="notice in notices" :key="notice.id" class="notice-item" @click="openNotice(notice)">
      <view class="header">
        <text class="title">{{ notice.title }}</text>
        <text v-if="notice.is_read === 0" class="unread-dot"></text>
      </view>
      <text class="time">{{ formatTime(notice.publish_time) }}</text>
      <text class="content">{{ notice.content }}</text>
      <text class="detail-link">查看详情 ›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getNoticesAPI, type Notice } from '@/apis/notice'
import { chinaDateTime } from '@/utils/chinaTime'

const notices = ref<Notice[]>([])
const loading = ref(false)
const error = ref('')

const loadNotices = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await getNoticesAPI()
    notices.value = res.data || []
  } catch (e) {
    notices.value = []
    error.value = '通知加载失败'
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

const openNotice = (notice: Notice) => uni.navigateTo({ url: `/pages/notice/detail?id=${notice.id}` })

const formatTime = chinaDateTime

onShow(loadNotices)
onPullDownRefresh(loadNotices)
</script>

<style scoped>
.notice-page { padding: 20rpx; }
.empty { text-align: center; padding: 100rpx 0; color: #999; }
.notice-item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.header { display: flex; align-items: center; }
.title { font-size: 32rpx; font-weight: bold; flex: 1; }
.unread-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #e64340; }
.time { font-size: 24rpx; color: #999; margin: 8rpx 0; }
.time, .detail-link { display: block; }
.content { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; white-space: pre-wrap; font-size: 28rpx; color: #333; line-height: 1.6; }
.detail-link { margin-top: 16rpx; color: #277f80; font-size: 24rpx; }
</style>
