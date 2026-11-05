<template>
  <view class="notice-detail">
    <view v-if="loading" class="state">正在加载公告…</view>
    <view v-else-if="error" class="state"><text>{{ error }}</text><button size="mini" @click="loadNotice">重新加载</button></view>
    <view v-else-if="notice">
      <text class="title">{{ notice.title }}</text>
      <text class="time">{{ chinaDateTime(notice.publish_time) }}</text>
      <text class="content">{{ notice.content }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getAnnouncementDetailAPI, readNoticeAPI, type Notice } from '@/apis/notice'
import { chinaDateTime } from '@/utils/chinaTime'
import { tokenStorageKey } from '@/utils/sessionKeys'

const notice = ref<Notice>()
const loading = ref(false)
const error = ref('')
let noticeId = 0
let initialized = false
const loadNotice = async () => {
  if (loading.value) return
  loading.value = true
  error.value = ''
  notice.value = undefined
  try {
    if (!Number.isSafeInteger(noticeId) || noticeId <= 0) throw Error('无效的公告链接')
    notice.value = (await getAnnouncementDetailAPI(noticeId)).data
    if (uni.getStorageSync(tokenStorageKey)) {
      try { await readNoticeAPI(noticeId) }
      catch { /* Content remains readable when the read marker cannot be saved. */ }
    }
  } catch {
    error.value = '该公告已下线、已删除或暂时无法读取，请返回消息页查看最新公告。'
  } finally { loading.value = false }
}
onLoad(async options => {
  noticeId = Number(options?.id)
  await loadNotice()
  initialized = true
})
onShow(() => { if (initialized) void loadNotice() })
</script>

<style scoped>
.notice-detail { margin: 24rpx; padding: 36rpx; background: white; border-radius: 16rpx; }
.title, .time, .content { display: block; }
.title { color: #285a62; font-size: 38rpx; font-weight: bold; line-height: 1.6; overflow-wrap: anywhere; }
.time { color: #888; font-size: 24rpx; margin: 20rpx 0 36rpx; }
.content { color: #333; font-size: 30rpx; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.state { padding: 50rpx 0; color: #777; font-size: 28rpx; line-height: 1.8; }
.state button { margin-top: 24rpx; }
</style>
