<template>
  <!-- #ifdef H5 -->
  <view class="desktop-site-header">
    <view class="desktop-header-inner">
      <text class="desktop-brand" @click="goTab('/pages/index/index')">智美<span>游</span></text>
      <view class="desktop-links">
        <text @click="goTab('/pages/index/index')">首页</text>
        <text @click="goTab('/pages/zone/pick')">自选区</text>
        <text @click="goTab('/pages/zone/rebuy')">复购区</text>
        <text @click="goPage('/pages/category/category')">商品分类</text>
        <text @click="goSearch">搜索商品</text>
        <text @click="goPage('/pages/cart/cart')">购物车</text>
        <text @click="goPage('/pages/member/promotion')">推广中心</text>
        <text @click="goTab('/pages/user/user')">我的账户</text>
      </view>
    </view>
  </view>
  <!-- #endif -->
</template>

<script setup lang="ts">
const goTab = (url: string) => {
  try {
    uni.switchTab({ url, fail: () => uni.navigateTo({ url }) })
  } catch {
    uni.navigateTo({ url })
  }
}
const goPage = (url: string) => uni.navigateTo({ url })
const goSearch = () => uni.navigateTo({ url: '/pages/product/search' })
</script>

<style scoped>
.desktop-site-header { display: none; }
@media (min-width: 900px) {
  .desktop-site-header { display: block; width: 100%; background: #fff; border-bottom: 1px solid #e1e9df; color: #254a34; }
  .desktop-header-inner { max-width: 1240px; height: 78px; margin: 0 auto; padding: 0 28px; display: flex; align-items: center; justify-content: space-between; }
  .desktop-brand { font-size: 27px; font-weight: 800; letter-spacing: 2px; cursor: pointer; }
  .desktop-brand span { color: #c17a3c; }
  .desktop-links { display: flex; gap: 22px; font-size: 15px; font-weight: 600; }
  .desktop-links text { cursor: pointer; }
  .desktop-links text:hover { color: #c17a3c; }
}
</style>
