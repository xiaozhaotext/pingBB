<template>
  <view class="products-page">
    <view class="products-wrap">
      <view class="products-head">
        <text class="back-link" @click="goBack">‹ 目的地</text>
        <text class="products-title">{{ destName }}</text>
        <text class="products-desc">点开一条路线，查看行程介绍</text>
      </view>
      <view v-if="loading" class="state">路线加载中…</view>
      <view v-else-if="error" class="state" @click="load">加载失败，点击重试</view>
      <view v-else-if="!routes.length" class="state">该目的地暂无路线</view>
      <view v-else class="card-list">
        <view v-for="item in routes" :key="item.id" class="product-card" @click="openDetail(item)">
          <image class="product-cover" :src="productImageUrl(item.imageUrl || item.productPic)" mode="aspectFill" />
          <view class="product-info">
            <text class="product-name">{{ item.name || item.productName }}</text>
            <text v-if="item.description || item.productSubTitle" class="product-desc">{{ item.description || item.productSubTitle }}</text>
            <text class="product-more">查看详情 ›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getConsumeRoutesAPI, type ConsumeRoute } from '@/apis/consume'
import { productImageUrl } from '@/utils/productImage'

const destinationId = ref(0)
const destName = ref('目的地')
const routes = ref<ConsumeRoute[]>([])
const loading = ref(false)
const error = ref(false)
let requestId = 0
async function load() {
  if (!destinationId.value) return
  const current = ++requestId
  loading.value = true
  error.value = false
  try {
    const result = await getConsumeRoutesAPI(destinationId.value)
    if (current === requestId) routes.value = result.data || []
  } catch { if (current === requestId) error.value = true }
  finally { if (current === requestId) loading.value = false }
}
function openDetail(item: ConsumeRoute) {
  uni.navigateTo({ url: `/pages/product/product?id=${item.productId}&zone=CONSUME` })
}
function goBack() { uni.navigateBack() }
onLoad((options) => {
  destinationId.value = Number(options?.destinationId || 0)
  if (options?.name) {
    try { destName.value = decodeURIComponent(options.name) } catch { destName.value = options.name }
  }
  load()
})
</script>
<style scoped>
.products-page{min-height:100vh;background:#fff8f2;color:#3d2921}
.products-wrap{max-width:560px;margin:0 auto;padding:24px 16px 60px}
.products-head{padding:12px 4px 20px;display:flex;flex-direction:column;gap:6px}
.back-link{font-size:14px;color:#c0562e;margin-bottom:4px}
.products-title{font-size:26px;font-weight:700}
.products-desc{font-size:13px;color:#8a6a58}
.card-list{display:flex;flex-direction:column;gap:10px}
.product-card{display:flex;align-items:center;gap:12px;padding:10px;border-radius:12px;background:#fff;box-shadow:0 3px 12px rgba(90,55,35,.06)}
.product-cover{display:block;width:160rpx;height:160rpx;flex-shrink:0;border-radius:8px}
.product-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.product-name{font-size:16px;font-weight:700}
.product-desc{font-size:12px;color:#8a6a58;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.product-more{font-size:12px;color:#c0562e;margin-top:2px}
.state{padding:64px 20px;text-align:center;color:#8a6a58;background:#fff;border-radius:16px}
</style>
