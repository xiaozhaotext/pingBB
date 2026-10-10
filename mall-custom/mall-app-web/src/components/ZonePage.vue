<template>
  <view class="zone-page">
    <DesktopHeader />
    <view class="zone-wrap">
      <view class="zone-head">
        <view><text class="zone-kicker">优选商城 · 商品专区</text><text class="zone-title">{{ title }}</text><text class="zone-description">{{ description }}</text></view>
        <view class="zone-actions"><text @click="uni.navigateTo({ url: '/pages/cart/cart' })">购物车</text><text @click="uni.navigateTo({ url: '/pages/order/order?state=-1' })">我的订单</text></view>
      </view>
      <view class="zone-tip">本地测试环境：专区仅展示已分配商品。积分与现金组合规则尚未确认，当前不提供专区积分结算。</view>
      <view class="zone-filters">
        <input v-model="keyword" placeholder="搜索专区商品" confirm-type="search" @confirm="load" />
        <text class="zone-search-button" @click="load">搜索</text>
        <picker :range="categoryNames" :value="categoryIndex" @change="selectCategory"><view class="zone-category">{{ categoryNames[categoryIndex] }} ⌄</view></picker>
        <text :class="{ active: sort === 'latest' }" @click="changeSort('latest')">综合</text>
        <text :class="{ active: sort === 'sale' }" @click="changeSort('sale')">销量</text>
        <text :class="{ active: sort === 'price_asc' }" @click="changeSort('price_asc')">价格↑</text>
        <text :class="{ active: sort === 'price_desc' }" @click="changeSort('price_desc')">价格↓</text>
      </view>
      <view v-if="loading" class="zone-empty">正在加载商品…</view>
      <view v-else-if="error" class="zone-empty">商品加载失败，请稍后重试。<text @click="load">重试</text></view>
      <view v-else-if="products.length === 0" class="zone-empty">{{ keyword || categoryIndex ? '没有符合筛选条件的商品' : '当前专区暂无已上架商品' }}</view>
      <view v-else class="zone-grid">
        <view v-for="item in products" :key="item.id" class="zone-card" @click="uni.navigateTo({ url: `/pages/product/product?id=${item.id}` })">
          <image :src="item.pic" mode="aspectFill" />
          <view class="zone-card-content"><text class="zone-card-title">{{ item.name }}</text><text class="zone-card-sub">{{ item.subTitle }}</text><view class="zone-card-foot"><text>¥{{ item.price }}</text><text>已售 {{ item.sale || 0 }}</text></view></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import DesktopHeader from '@/components/DesktopHeader.vue'
import { getZoneProductsAPI, type ZoneProduct } from '@/apis/zone'
import { getCategoryTreeAPI } from '@/apis/product'
import type { CategoryTreeNode } from '@/types/product'
const props = defineProps<{ zone: 'PICK' | 'REBUY'; title: string; description: string }>()
const products = ref<ZoneProduct[]>([])
const loading = ref(false)
const error = ref(false)
const keyword = ref('')
const sort = ref<'latest' | 'sale' | 'price_asc' | 'price_desc'>('latest')
const categoryIndex = ref(0)
const categories = ref<{ id?: number; name: string }[]>([{ name: '全部分类' }])
const categoryNames = ref<string[]>(['全部分类'])
const loadCategories = async () => {
  try {
    const result = await getCategoryTreeAPI()
    const flatten = (nodes: CategoryTreeNode[]) => nodes.forEach(node => {
      if (node.children?.length) flatten(node.children)
      else categories.value.push({ id: node.id, name: node.name })
    })
    categories.value = [{ name: '全部分类' }]
    flatten(result.data || [])
    categoryNames.value = categories.value.map(item => item.name)
  } catch { /* 商品列表仍可按关键字浏览 */ }
}
const selectCategory = (event: any) => { categoryIndex.value = Number(event.detail.value); load() }
const changeSort = (value: typeof sort.value) => { sort.value = value; load() }
const load = async () => {
  loading.value = true
  error.value = false
  try { products.value = (await getZoneProductsAPI(props.zone, { keyword: keyword.value.trim(), categoryId: categories.value[categoryIndex.value]?.id, sort: sort.value })).data || [] }
  catch { error.value = true }
  finally { loading.value = false }
}
onShow(() => { loadCategories(); load() })
</script>

<style scoped>
.zone-page { min-height: 100vh; background: #f5f7f3; color: #213d30; }
.zone-wrap { max-width: 1240px; margin: auto; padding: 24px 20px 110px; }
.zone-head { display: flex; justify-content: space-between; gap: 20px; align-items: end; padding: 30px; border-radius: 20px; background: #e4eee5; }
.zone-kicker,.zone-title,.zone-description { display: block; }
.zone-kicker { color: #5b8066; font-size: 13px; }
.zone-title { margin: 8px 0; font-size: 34px; font-weight: 800; }
.zone-description { color: #607568; font-size: 14px; }
.zone-actions { display: flex; gap: 12px; white-space: nowrap; }
.zone-actions text { padding: 9px 15px; border-radius: 9px; background: white; cursor: pointer; }
.zone-tip { margin: 18px 0; padding: 12px 16px; border-radius: 10px; background: #fff7e7; color: #866124; font-size: 13px; }
.zone-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; padding: 12px; border-radius: 12px; background: #fff; }
.zone-filters input { flex: 1; min-width: 150px; padding: 8px 10px; border: 1px solid #dce6df; border-radius: 8px; }
.zone-filters text,.zone-category { padding: 7px 9px; cursor: pointer; font-size: 13px; }
.zone-filters .active { color: #226842; font-weight: 800; }
.zone-search-button { background: #285b40; color: #fff; border-radius: 8px; }
.zone-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 18px; }
.zone-card { overflow: hidden; background: #fff; border-radius: 14px; cursor: pointer; box-shadow: 0 2px 12px #14351a0d; }
.zone-card image { width: 100%; height: 210px; }
.zone-card-content { padding: 14px; }
.zone-card-title,.zone-card-sub { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.zone-card-title { font-weight: 700; }
.zone-card-sub { margin-top: 6px; font-size: 12px; color: #809087; }
.zone-card-foot { margin-top: 15px; display: flex; justify-content: space-between; align-items: center; }
.zone-card-foot text:first-child { color: #bb6540; font-weight: 800; font-size: 20px; }
.zone-card-foot text:last-child { color: #87958c; font-size: 12px; }
.zone-empty { padding: 70px 15px; background: #fff; border-radius: 14px; text-align: center; color: #718277; }
.zone-empty text { display: block; margin-top: 10px; color: #287749; cursor: pointer; }
@media(max-width:800px) { .zone-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; } .zone-card image { height: 155px; } .zone-head { padding: 22px; align-items: start; flex-direction: column; } .zone-title { font-size: 26px; } .zone-wrap { padding: 12px 12px 85px; } }
</style>
