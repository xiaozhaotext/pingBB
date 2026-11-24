<template>
  <view class="category-page">
  <DesktopHeader />
  <view v-if="error" class="category-status" @click="loadData">分类加载失败，点击重试</view>
  <view v-else-if="!topCateList.length" class="category-status">{{ loading ? '正在加载分类…' : '暂无可展示的消费区、复购区分类' }}</view>
  <view class="content">
    <scroll-view scroll-y class="left-aside">
      <view
        v-for="item in topCateList"
        :key="item.id"
        class="f-item b-b"
        :class="{ active: item.id === currentCateId }"
        @click="handleTabTap(item)"
      >
        {{ item.name }}
      </view>
    </scroll-view>
    <scroll-view scroll-with-animation scroll-y class="right-aside">
      <view v-if="currentCateId" class="all-category" @click="handleNavToList(currentCateId)">查看当前分类全部商品 ›</view>
      <view class="s-list">
        <view
          @click="handleNavToList(item.id)"
          class="s-item"
          v-for="item in subCateList"
          :key="item.id"
        >
          <image v-if="item.icon && !failedIcons.has(item.id)" :src="productImageUrl(item.icon)" @error="failedIcons.add(item.id)" />
          <view v-else class="category-symbol">{{ item.name.slice(0,1) }}</view>
          <text>{{ item.name }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
  </view>
</template>

<script setup lang="ts">
import DesktopHeader from '@/components/DesktopHeader.vue'
import { productImageUrl } from '@/utils/productImage'
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMallCategoryTreeAPI } from '@/apis/product'
import { flattenCategoryTree } from '@/utils/categoryTree'
import type { CategoryTreeNode } from '@/types/product'

// ===== 页面数据 =====
// 当前分类id
const currentCateId = ref(0)
const failedIcons=ref(new Set<number>())
// 一级分类列表
const topCateList = ref<CategoryTreeNode[]>([])
// 二级分类列表
const subCateList = computed(() => {
  const selected=topCateList.value.find(item=>item.id===currentCateId.value)
  return selected ? selected.children?.length ? flattenCategoryTree(selected.children) : [selected] : []
})
const loading=ref(false),error=ref(false)

// ===== 数据加载 =====
// 加载分类数据
const loadData = async () => {
  loading.value=true;error.value=false
  try {
    const res = await getMallCategoryTreeAPI()
    topCateList.value = res.data || []
    if(!topCateList.value.some(item=>item.id===currentCateId.value))currentCateId.value=topCateList.value[0]?.id || 0
  } catch (e) {
    topCateList.value=[];currentCateId.value=0;error.value=true
  } finally {loading.value=false}
}

// ===== 生命周期 =====
// 页面加载时执行
onShow(loadData)

// ===== 事件处理方法 =====
// 一级分类点击
const handleTabTap = (item: CategoryTreeNode) => {
  currentCateId.value = item.id
}

// 导航到商品列表
const handleNavToList = (sid: number) => {
  uni.navigateTo({
    url: `/pages/product/list?sid=${sid}`,
  })
}
</script>

<style lang="scss">
page {
  height: 100%;
  background-color: #f8f8f8;
}
</style>

<style lang="scss" scoped>
.category-page{height:calc(100vh - var(--window-top,0px));display:flex;flex-direction:column}
.category-status{padding:28px;text-align:center;color:#8d6554}.all-category{padding:16px;color:#e85b36;font-size:14px;cursor:pointer}
.content {
  flex:1;min-height:0;width:100%;max-width:1280px;margin:0 auto;
  background-color: #f8f8f8;
  display: flex;
}

.left-aside {
  flex-shrink: 0;
  width: 28%;
  max-width: 240px;
  height: 100%;
  background-color: #fff;
}

.f-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height:100rpx;height:auto;padding:20rpx 24rpx;box-sizing:border-box;line-height:1.5;word-break:break-word;cursor:pointer;
  font-size: 28rpx;
  color: #79665c;
  position: relative;

  &.active {
    color: #e85b36;
    background: #f8f8f8;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      height: 36rpx;
      width: 8rpx;
      background-color: #e85b36;
      border-radius: 0 4px 4px 0;
      opacity: 0.8;
    }
  }
}

.right-aside {
  flex: 1;
  overflow: hidden;
  padding-left: 20rpx;
}

.s-list {
  margin-top: 20rpx;
  display: grid;
  grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:14px;
  width: 100%;
  background: transparent;
  padding-top: 12rpx;

  &::after {
    display:none;content: '';
    flex: 99;
    height: 0;
  }
}

.s-item {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
  width:auto;min-height:150px;padding:18px 14px;box-sizing:border-box;background:white;border:1px solid #ece3df;border-radius:14px;gap:12px;cursor:pointer;text-align:center;word-break:break-word;
  font-size: 26rpx;
  color: #666;
  padding-bottom: 20rpx;

  image {
    width: 140rpx;
    height: 140rpx;
  }
}
.category-symbol{width:64px;height:64px;line-height:64px;background:#f4efed;color:#e85b36;border-radius:16px;font-size:28px}
</style>
