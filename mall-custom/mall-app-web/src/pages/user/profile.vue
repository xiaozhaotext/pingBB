<template>
  <view class="profile-page">
    <view class="card">
      <image class="avatar" :src="memberStore.memberInfo?.icon || '/static/missing-face.png'" mode="aspectFill" />
      <view class="account">账号：{{ memberStore.memberInfo?.username || '—' }}</view>
      <view class="field-label">昵称</view>
      <input v-model="nickname" class="nickname-input" :maxlength="24" placeholder="输入 2 至 24 个字符" />
      <button class="save-button" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存昵称' }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMemberInfoAPI, updateNicknameAPI } from '@/apis/member'
import { useMemberStore } from '@/stores/member'

const memberStore = useMemberStore()
const nickname = ref('')
const saving = ref(false)

onShow(async () => {
  if (!memberStore.hasLogin) {
    uni.redirectTo({ url: '/pages/public/login' })
    return
  }
  try {
    const response = await getMemberInfoAPI()
    if (response.data) {
      memberStore.setMemberInfo(response.data)
      nickname.value = response.data.nickname || ''
    }
  } catch {
    nickname.value = memberStore.memberInfo?.nickname || ''
  }
})

async function save() {
  const value = nickname.value.trim()
  if (value.length < 2 || value.length > 24) {
    uni.showToast({ title: '昵称须为 2 至 24 个字符', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await updateNicknameAPI(value)
    const response = await getMemberInfoAPI()
    if (response.data) memberStore.setMemberInfo(response.data)
    nickname.value = response.data?.nickname || value
    uni.showToast({ title: '昵称已更新', icon: 'success' })
  } catch {
    uni.showToast({ title: '保存失败，请重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-page { min-height: 100vh; padding: 24px 16px; background: #f3f7f6; box-sizing: border-box; }
.card { padding: 28px 20px; border-radius: 18px; background: #fff; text-align: center; }
.avatar { width: 82px; height: 82px; border-radius: 24px; background: #edf1ef; }
.account { margin: 12px 0 28px; color: #687773; font-size: 14px; }
.field-label { margin-bottom: 10px; text-align: left; color: #284c48; font-weight: 600; }
.nickname-input { height: 46px; padding: 0 14px; border: 1px solid #d7e1de; border-radius: 10px; text-align: left; }
.save-button { margin-top: 28px; color: #fff; background: #286251; border-radius: 10px; }
</style>
