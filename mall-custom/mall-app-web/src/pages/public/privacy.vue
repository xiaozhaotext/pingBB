<template>
  <view class="privacy">
    <view class="title">{{ title }}</view>
    <view v-if="companyName" class="meta">{{ companyName }}</view>
    <view class="body">
      <text class="text">{{ bodyText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getSiteInfoAPI } from '@/apis/home'

const FALLBACK = '平台尚未在后台配置隐私政策内容。可在管理后台「会员运营 - 平台设置 - 服务内容」中填写后刷新本页。'

const title = ref('隐私政策')
const companyName = ref('')
const bodyText = ref('内容加载中…')

onLoad(async (options?: Record<string, string>) => {
  const contentKey = options?.type === 'agreement' ? 'user_agreement' : 'privacy_policy'
  title.value = contentKey === 'user_agreement' ? '用户协议' : '隐私政策'
  uni.setNavigationBarTitle({ title: title.value })
  try {
    const response = await getSiteInfoAPI()
    companyName.value = response.data?.company?.name ?? ''
    const content = response.data?.contents?.[contentKey]
    bodyText.value = content && String(content).trim() ? String(content) : FALLBACK
  } catch {
    // http.ts 已给出网络/后端提示，这里退回到说明文案，避免空白页
    bodyText.value = FALLBACK
  }
})
</script>

<style lang="scss" scoped>
.privacy {
  min-height: 100vh;
  padding: 32rpx 28rpx 60rpx;
  background: #fff;
}
.title {
  font-size: 40rpx;
  font-weight: 600;
  color: #285a62;
}
.meta {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9aa8a8;
}
.body {
  margin-top: 28rpx;
}
.text {
  font-size: 28rpx;
  line-height: 1.8;
  color: #40484a;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
