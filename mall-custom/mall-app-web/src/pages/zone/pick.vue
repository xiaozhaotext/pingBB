<template>
  <view class="activities-page">
    <DesktopHeader />
    <view class="activities-wrap">
      <view class="activities-heading"><text class="title">自选区 · 旅游活动</text><text>查看活动规则，选择心仪的旅游正品</text></view>
      <view v-if="loading" class="empty">活动加载中…</view>
      <view v-else-if="error" class="empty" @click="load">活动加载失败，点击重试</view>
      <view v-else-if="!activities.length" class="empty">暂无已发布的旅游活动</view>
      <view v-else class="activity-grid">
        <view v-for="activity in activities" :key="activity.id" class="activity-card" @click="open(activity.id)">
          <image :src="productImageUrl(activity.imageUrl)" mode="aspectFill" />
          <view class="activity-name"><text>{{ activity.title }}</text><text>查看活动 ›</text></view>
        </view>
      </view>
    </view>
    <MainNavigation active="pick" />
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import DesktopHeader from '@/components/DesktopHeader.vue'
import MainNavigation from '@/components/MainNavigation.vue'
import { getPickActivitiesAPI, type PickActivity } from '@/apis/pickActivity'
import { productImageUrl } from '@/utils/productImage'
const activities = ref<PickActivity[]>([])
const loading = ref(false)
const error = ref(false)
let requestId = 0
async function load() {
  const current = ++requestId
  loading.value = true
  error.value = false
  try {
    const result = await getPickActivitiesAPI()
    if (current === requestId) activities.value = result.data || []
  } catch { if (current === requestId) error.value = true }
  finally { if (current === requestId) loading.value = false }
}
function open(id: number) { uni.navigateTo({ url: `/pages/product/product?zone=PICK&activityId=${id}` }) }
onShow(load)
</script>
<style scoped>
.activities-page{min-height:100vh;background:#f5f7f3;color:#213d30}.activities-wrap{max-width:1240px;margin:auto;padding:24px 20px 110px}.activities-heading{padding:28px;background:#e4eee5;border-radius:20px;margin-bottom:24px}.activities-heading text{display:block}.title{font-size:28px;font-weight:700;margin-bottom:12px}.activity-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:20px}.activity-card{border-radius:16px;overflow:hidden;background:#fff;cursor:pointer}.activity-card image{width:100%;height:230px}.activity-name{padding:18px;display:flex;gap:12px;justify-content:space-between;align-items:center}.activity-name text:first-child{font-weight:700;overflow-wrap:anywhere}.activity-name text:last-child{font-size:12px;color:#54745e;white-space:nowrap}.empty{padding:64px 20px;text-align:center;background:white;border-radius:16px}@media(max-width:800px){.activity-grid{grid-template-columns:1fr}.activities-wrap{padding:16px 12px 100px}.activity-card image{height:200px}.title{font-size:24px}}
</style>
