<template>
  <view class="notice-page">
    <view v-if="notices.length === 0" class="empty">
      <text>暂无通知</text>
    </view>
    <view v-for="notice in notices" :key="notice.id" class="notice-item" @click="markRead(notice)">
      <view class="header">
        <text class="title">{{ notice.title }}</text>
        <text v-if="notice.is_read === 0" class="unread-dot"></text>
      </view>
      <text class="time">{{ formatTime(notice.publish_time) }}</text>
      <text class="content">{{ notice.content }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { http } from '@/utils/http'
import { chinaDateTime } from '@/utils/chinaTime'

interface Notice {
  id: number
  title: string
  content: string
  type: string
  publish_time: string
  is_read: number
}

const notices = ref<Notice[]>([])

const loadNotices = async () => {
  try {
    const res = await http<Notice[]>({ method: 'GET', url: '/member/notice/list' })
    notices.value = res.data || []
  } catch (e) {
    console.error('加载通知失败', e)
  }
}

const markRead = async (notice: Notice) => {
  if (notice.is_read === 1) return
  try {
    await http({ method: 'POST', url: '/member/notice/read', data: { noticeId: notice.id } })
    notice.is_read = 1
  } catch (e) {
    console.error('标记已读失败', e)
  }
}

const formatTime = chinaDateTime

onMounted(() => {
  loadNotices()
})
</script>

<style scoped>
.notice-page { padding: 20rpx; }
.empty { text-align: center; padding: 100rpx 0; color: #999; }
.notice-item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.header { display: flex; align-items: center; }
.title { font-size: 32rpx; font-weight: bold; flex: 1; }
.unread-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #e64340; }
.time { font-size: 24rpx; color: #999; margin: 8rpx 0; }
.content { font-size: 28rpx; color: #333; line-height: 1.6; }
</style>
