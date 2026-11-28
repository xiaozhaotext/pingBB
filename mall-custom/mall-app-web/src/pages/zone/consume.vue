<template>
  <view class="consume-page">
    <DesktopHeader />
    <view class="consume-wrap">
      <view class="consume-head">
        <text class="zone-kicker">智游美 · 消费专区</text>
        <text class="zone-title">旅游路线</text>
        <text class="zone-desc">选一个主题，开启你的旅程</text>
      </view>
      <view v-if="loading" class="state">主题加载中…</view>
      <view v-else-if="error" class="state" @click="load">加载失败，点击重试</view>
      <view v-else-if="!themes.length" class="state">暂无主题，请稍后再来</view>
      <view v-else class="card-list">
        <view v-for="item in themes" :key="item.id" class="theme-card" @click="openDestinations(item)">
          <image class="theme-cover" :src="productImageUrl(item.imageUrl)" mode="aspectFill" />
          <view class="theme-info">
            <text class="theme-name">{{ item.name }}</text>
            <text v-if="item.description" class="theme-desc">{{ item.description }}</text>
            <text class="theme-more">查看目的地 ›</text>
          </view>
        </view>
      </view>
    </view>
    <MainNavigation active="consume" />
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import DesktopHeader from '@/components/DesktopHeader.vue'
import MainNavigation from '@/components/MainNavigation.vue'
import { getConsumeThemesAPI, type ConsumeTheme } from '@/apis/consume'
import { productImageUrl } from '@/utils/productImage'

const themes = ref<ConsumeTheme[]>([])
const loading = ref(false)
const error = ref(false)
let requestId = 0
async function load() {
  const current = ++requestId
  loading.value = true
  error.value = false
  try {
    const result = await getConsumeThemesAPI()
    if (current === requestId) themes.value = result.data || []
  } catch { if (current === requestId) error.value = true }
  finally { if (current === requestId) loading.value = false }
}
function openDestinations(item: ConsumeTheme) {
  uni.navigateTo({ url: `/pages/zone/consumeRoutes?themeId=${item.id}&name=${encodeURIComponent(item.name)}` })
}
onShow(load)
</script>
<style scoped>
.consume-page{min-height:100vh;background:#fff8f2;color:#3d2921}
.consume-wrap{max-width:560px;margin:0 auto;padding:24px 16px 120px}
.consume-head{padding:8px 4px 20px;display:flex;flex-direction:column;gap:6px}
.zone-kicker{font-size:12px;color:#a06b4c;letter-spacing:2px}
.zone-title{font-size:28px;font-weight:700}
.zone-desc{font-size:13px;color:#8a6a58}
.card-list{display:flex;flex-direction:column;gap:10px}
.theme-card{display:flex;align-items:center;gap:12px;padding:10px;border-radius:12px;background:#fff;box-shadow:0 3px 12px rgba(90,55,35,.06)}
.theme-cover{display:block;width:160rpx;height:160rpx;flex-shrink:0;border-radius:8px}
.theme-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.theme-name{font-size:16px;font-weight:700}
.theme-desc{font-size:12px;color:#8a6a58;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.theme-more{font-size:12px;color:#c0562e;margin-top:2px}
.state{padding:64px 20px;text-align:center;color:#8a6a58;background:#fff;border-radius:16px}
</style>
