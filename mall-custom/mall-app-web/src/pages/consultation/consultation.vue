<template>
  <view class="consult-page">
    <view class="form">
      <input v-model="subject" placeholder="请输入咨询标题" maxlength="128" class="input" />
      <textarea v-model="content" placeholder="请描述您的问题（5000字以内）" maxlength="5000" class="textarea" />
      <input v-model="contact" placeholder="联系方式（手机/微信，可选）" maxlength="128" class="input" />
      <button @click="submit" class="btn" :disabled="submitting">提交咨询</button>
    </view>
    <view class="history">
      <view class="section-title">我的咨询</view>
      <view v-if="list.length === 0" class="empty"><text>暂无咨询记录</text></view>
      <view v-for="item in list" :key="item.id" class="item">
        <view class="item-header">
          <text class="item-subject">{{ item.subject }}</text>
          <text class="status" :class="'s' + item.status">{{ statusText(item.status) }}</text>
        </view>
        <text class="item-content">{{ item.content }}</text>
        <text class="item-time">{{ formatTime(item.create_time) }}</text>
        <view v-if="item.reply" class="reply">
          <text class="reply-label">客服回复：</text>
          <text class="reply-content">{{ item.reply }}</text>
          <text class="reply-time">{{ formatTime(item.reply_time) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { http } from '@/utils/http'

const subject = ref('')
const content = ref('')
const contact = ref('')
const submitting = ref(false)
const list = ref<any[]>([])

const statusText = (s: number) => s === 0 ? '待回复' : s === 1 ? '已回复' : '已关闭'
const formatTime = (t: string) => t ? t.substring(0, 16).replace('T', ' ') : ''

const loadList = async () => {
  try {
    const res = await http<any[]>({ method: 'GET', url: '/member/consultation/my' })
    list.value = res.data || []
  } catch (e) { console.error(e) }
}

const submit = async () => {
  if (!subject.value.trim() || !content.value.trim()) {
    uni.showToast({ title: '请填写标题和内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await http({
      method: 'POST',
      url: '/member/consultation/submit',
      data: { subject: subject.value.trim(), content: content.value.trim(), contact: contact.value.trim() }
    })
    uni.showToast({ title: '提交成功' })
    subject.value = ''; content.value = ''; contact.value = ''
    loadList()
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onMounted(() => loadList())
</script>

<style scoped>
.consult-page { padding: 20rpx; }
.form { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.input { border: 1rpx solid #eee; border-radius: 8rpx; padding: 16rpx; margin-bottom: 16rpx; font-size: 28rpx; }
.textarea { border: 1rpx solid #eee; border-radius: 8rpx; padding: 16rpx; margin-bottom: 16rpx; font-size: 28rpx; height: 200rpx; }
.btn { background: #e64340; color: #fff; border-radius: 8rpx; padding: 20rpx; font-size: 30rpx; }
.section-title { font-size: 32rpx; font-weight: bold; margin: 20rpx 0; }
.empty { text-align: center; color: #999; padding: 60rpx 0; }
.item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.item-header { display: flex; justify-content: space-between; align-items: center; }
.item-subject { font-size: 30rpx; font-weight: bold; }
.status { font-size: 24rpx; padding: 4rpx 12rpx; border-radius: 20rpx; }
.s0 { background: #fef0e0; color: #e6a23c; }
.s1 { background: #e0f5e9; color: #67c23a; }
.s2 { background: #f0f0f0; color: #999; }
.item-content { font-size: 28rpx; color: #333; margin: 12rpx 0; }
.item-time { font-size: 24rpx; color: #999; }
.reply { background: #f8f8f8; border-radius: 8rpx; padding: 16rpx; margin-top: 12rpx; }
.reply-label { font-size: 26rpx; font-weight: bold; color: #e64340; }
.reply-content { font-size: 28rpx; color: #333; display: block; margin: 8rpx 0; }
.reply-time { font-size: 24rpx; color: #999; }
</style>
