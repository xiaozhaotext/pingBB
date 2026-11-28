<template>
  <view class="dest-page">
    <view class="dest-wrap">
      <view class="dest-head">
        <text class="back-link" @click="goBack">‹ 旅游路线</text>
        <text class="dest-title">{{ themeName }}</text>
        <text class="dest-desc">选一个目的地，看看那里的路线</text>
      </view>
      <view v-if="loading" class="state">目的地加载中…</view>
      <view v-else-if="error" class="state" @click="load">加载失败，点击重试</view>
      <view v-else-if="!destinations.length" class="state">该主题暂无目的地</view>
      <view v-else class="card-list">
        <view v-for="item in destinations" :key="item.id" class="dest-card" @click="openRoutes(item)">
          <image class="dest-cover" :src="productImageUrl(item.imageUrl)" mode="aspectFill" />
          <view class="dest-info">
            <text class="dest-name">{{ item.name }}</text>
            <text v-if="item.description" class="dest-desc-text">{{ item.description }}</text>
            <text class="dest-more">查看详情 ›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getConsumeDestinationsAPI, getConsumeRoutesAPI, type ConsumeDestination } from '@/apis/consume'
import { productImageUrl } from '@/utils/productImage'

const themeId = ref(0)
const themeName = ref('主题')
const destinations = ref<ConsumeDestination[]>([])
const loading = ref(false)
const error = ref(false)
let requestId = 0
let opening = false
async function load() {
  if (!themeId.value) return
  const current = ++requestId
  loading.value = true
  error.value = false
  try {
    const result = await getConsumeDestinationsAPI(themeId.value)
    if (current === requestId) destinations.value = result.data || []
  } catch { if (current === requestId) error.value = true }
  finally { if (current === requestId) loading.value = false }
}
async function openRoutes(item: ConsumeDestination) {
  if (opening) return
  opening = true
  uni.showLoading({ title: '加载中', mask: true })
  try {
    const result = await getConsumeRoutesAPI(item.id)
    const routes = result.data || []
    if (routes.length === 1 && routes[0].productId) {
      // 只有一个目的地产品时，直接进入产品介绍页，不再多一层线路列表
      uni.navigateTo({ url: `/pages/product/product?id=${routes[0].productId}&zone=CONSUME` })
    } else if (routes.length > 1) {
      uni.navigateTo({ url: `/pages/zone/consumeProducts?destinationId=${item.id}&name=${encodeURIComponent(item.name)}` })
    } else {
      uni.showToast({ title: '该目的地暂无线路', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '加载失败，请重试', icon: 'none' })
  } finally {
    uni.hideLoading()
    opening = false
  }
}
function goBack() { uni.navigateBack() }
onLoad((options) => {
  themeId.value = Number(options?.themeId || 0)
  if (options?.name) {
    try { themeName.value = decodeURIComponent(options.name) } catch { themeName.value = options.name }
  }
  load()
})
</script>
<style scoped>
.dest-page{min-height:100vh;background:#fff8f2;color:#3d2921}
.dest-wrap{max-width:560px;margin:0 auto;padding:24px 16px 60px}
.dest-head{padding:12px 4px 20px;display:flex;flex-direction:column;gap:6px}
.back-link{font-size:14px;color:#c0562e;margin-bottom:4px}
.dest-title{font-size:26px;font-weight:700}
.dest-desc{font-size:13px;color:#8a6a58}
.card-list{display:flex;flex-direction:column;gap:10px}
.dest-card{display:flex;align-items:center;gap:12px;padding:10px;border-radius:12px;background:#fff;box-shadow:0 3px 12px rgba(90,55,35,.06)}
.dest-cover{display:block;width:160rpx;height:160rpx;flex-shrink:0;border-radius:8px}
.dest-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.dest-name{font-size:16px;font-weight:700}
.dest-desc-text{font-size:12px;color:#8a6a58;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.dest-more{font-size:12px;color:#c0562e;margin-top:2px}
.state{padding:64px 20px;text-align:center;color:#8a6a58;background:#fff;border-radius:16px}
</style>
