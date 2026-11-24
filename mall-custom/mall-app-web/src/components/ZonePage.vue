<template>
  <view class="zone-page">
    <DesktopHeader />
    <view class="zone-wrap">
      <view class="zone-head" :class="{ 'zone-head-image': props.zone !== 'PICK' }">
        <image v-if="props.zone !== 'PICK'" class="zone-column-photo" :src="`/static/festive-home/${props.zone === 'REBUY' ? 'rebuy' : 'consume'}.jpg`" mode="widthFix" />
        <view><text class="zone-kicker">智游美 · 商品专区</text><text class="zone-title">{{ title }}</text><text class="zone-description">{{ description }}</text></view>
        <view class="zone-actions"><text @click="goPage('/pages/cart/cart')">购物车</text><text @click="goPage('/pages/order/order?state=0')">我的订单</text></view>
      </view>
      <view class="zone-tip">专区仅展示已分配且上架的商品。{{ zoneTip }}</view>
      <view class="zone-filters">
        <input v-model="keyword" placeholder="搜索专区商品" confirm-type="search" @confirm="load" />
        <text class="zone-search-button" @click="load">搜索</text>
        <!-- #ifdef H5 -->
        <select class="zone-category" aria-label="专区商品分类" :value="categoryIndex" @change="selectWebCategory"><option v-for="(name,index) in categoryNames" :key="index" :value="index">{{ name }}</option></select>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <picker :range="categoryNames" :value="categoryIndex" @change="selectCategory"><view class="zone-category">{{ categoryNames[categoryIndex] }} ⌄</view></picker>
        <!-- #endif -->
        <text :class="{ active: sort === 'latest' }" @click="changeSort('latest')">综合</text>
        <text :class="{ active: sort === 'sale' }" @click="changeSort('sale')">销量</text>
        <text v-if="props.zone !== 'PICK'" :class="{ active: sort === 'price_asc' }" @click="changeSort('price_asc')">价格↑</text>
        <text v-if="props.zone !== 'PICK'" :class="{ active: sort === 'price_desc' }" @click="changeSort('price_desc')">价格↓</text>
      </view>
      <view v-if="loading" class="zone-empty">正在加载商品…</view>
      <view v-else-if="error" class="zone-empty">商品加载失败，请稍后重试。<text @click="load">重试</text></view>
      <view v-else-if="products.length === 0" class="zone-empty">{{ keyword || categoryIndex ? '没有符合筛选条件的商品' : '当前专区暂无已上架商品' }}</view>
      <view v-else class="zone-grid">
        <view v-for="item in products" :key="item.id" class="zone-card" @click="goPage('/pages/product/product?id=' + item.id + '&zone=' + props.zone)">
          <image :src="productImageUrl(item.pic)" mode="aspectFill" />
          <view class="zone-card-content">
            <text class="zone-card-title">{{ item.name }}</text>
            <text class="zone-card-sub">{{ item.subTitle }}</text>
            <view v-if="props.zone === 'PICK'" class="zone-card-tags"><text class="zone-product-tag">自选区</text></view>
            <view v-else class="zone-card-foot"><view class="zone-card-price"><text>{{ priceText(item) }}</text><text v-if="props.zone === 'CONSUME' && item.requiredProductPoints != null" class="zone-card-points">{{ item.requiredProductPoints }} 智豆</text></view><text>已售 {{ item.sale || 0 }}</text><text class="zone-add-cart" @click.stop="addCart(item)">加入购物车</text></view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const goPage = (url: string) => uni.navigateTo({ url })

import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import DesktopHeader from '@/components/DesktopHeader.vue'
import { getZoneProductsAPI, type ZoneProduct } from '@/apis/zone'
import { getMallCategoryTreeAPI } from '@/apis/product'
import { flattenCategoryTree } from '@/utils/categoryTree'
import { addFirstAvailableSkuToCart } from '@/utils/cart'
import { productImageUrl } from '@/utils/productImage'
const props = defineProps<{ zone: 'PICK' | 'REBUY' | 'CONSUME'; title: string; description: string }>()
const products = ref<ZoneProduct[]>([])
const loading = ref(false)
const error = ref(false)
const keyword = ref('')
const sort = ref<'latest' | 'sale' | 'price_asc' | 'price_desc'>('latest')
const categoryIndex = ref(0)
const categories = ref<{ id?: number; name: string }[]>([{ name: '全部分类' }])
const categoryNames = ref<string[]>(['全部分类'])
let categoryGeneration = 0
const loadCategories = async () => {
  const run=++categoryGeneration
  try {
    const result = await getMallCategoryTreeAPI(props.zone)
    if(run!==categoryGeneration)return
    const selected=categories.value[categoryIndex.value]?.id
    categories.value = [{ name: '全部分类' },...flattenCategoryTree(result.data || []).map(node=>({id:node.id,name:'　'.repeat(node.depth)+node.name}))]
    categoryIndex.value=Math.max(0,categories.value.findIndex(item=>item.id===selected))
    categoryNames.value = categories.value.map(item => item.name)
  } catch {
    if(run!==categoryGeneration)return
    categories.value=[{name:'全部分类'}];categoryNames.value=['全部分类'];categoryIndex.value=0
    uni.showToast({title:'当前专区分类加载失败，请刷新重试',icon:'none'})
  }
}
const selectCategory = (event: any) => { categoryIndex.value = Number(event.detail.value); load() }
const selectWebCategory = (event:Event) => { categoryIndex.value=Number((event.target as HTMLSelectElement).value);load() }
const changeSort = (value: typeof sort.value) => { sort.value = value; load() }
let productGeneration=0
const load = async () => {
  const run=++productGeneration
  loading.value = true
  error.value = false
  try { const response=await getZoneProductsAPI(props.zone, { keyword: keyword.value.trim(), categoryId: categories.value[categoryIndex.value]?.id, sort: sort.value });if(run===productGeneration)products.value=response.data || [] }
  catch { if(run===productGeneration)error.value = true }
  finally { if(run===productGeneration)loading.value = false }
}
const addCart = (item: ZoneProduct) => { addFirstAvailableSkuToCart(item.id, props.zone) }
const zoneTip = computed(() => {
  if (props.zone === 'REBUY') return '复购区先用复购积分抵扣配置比例（默认 50%），余款优先使用账户余额，余额不足再通过外部支付。'
  if (props.zone === 'CONSUME') return '消费区可用智豆或账户余额全额支付。'
  return '点击商品查看详情、价格与会员权益。'
})
const priceText = (item: ZoneProduct) => `¥${item.price}`
onShow(async () => { await loadCategories(); await load() })
</script>

<style scoped>
.zone-page { min-height: 100vh; background: #fff8f2; color: #3d2921; }
.zone-wrap { max-width: 1240px; margin: auto; padding: 24px 20px 110px; }
.zone-head { display: flex; justify-content: space-between; gap: 20px; align-items: end; padding: 30px; border-radius: 20px; background: #eee7e4; }
.zone-kicker,.zone-title,.zone-description { display: block; }
.zone-kicker { color: #896352; font-size: 13px; }
.zone-title { margin: 8px 0; font-size: 34px; font-weight: 800; }
.zone-description { color: #856050; font-size: 14px; }
.zone-actions { display: flex; gap: 12px; white-space: nowrap; }
.zone-actions text { padding: 9px 15px; border-radius: 9px; background: white; cursor: pointer; }
.zone-tip { margin: 18px 0; padding: 12px 16px; border-radius: 10px; background: #fff7e7; color: #866124; font-size: 13px; }
.zone-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; padding: 12px; border-radius: 12px; background: #fff; }
.zone-filters input { flex: 1; min-width: 150px; padding: 8px 10px; border: 1px solid #e8deda; border-radius: 8px; }
.zone-filters text,.zone-category { padding: 7px 9px; cursor: pointer; font-size: 13px; }
.zone-filters .active { color: #683722; font-weight: 800; }
.zone-filters select.zone-category{max-width:260px;min-height:34px;border:1px solid #e8deda;border-radius:8px;background:#fff;color:#e85b36}
.zone-search-button { background: #e85b36; color: #fff; border-radius: 8px; }
.zone-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 18px; }
.zone-card { overflow: hidden; background: #fff; border-radius: 14px; cursor: pointer; box-shadow: 0 2px 12px #351e140d; }
.zone-card image { width: 100%; height: 210px; }
.zone-card-content { padding: 14px; }
.zone-card-title,.zone-card-sub { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.zone-card-title { font-weight: 700; }
.zone-card-sub { margin-top: 6px; font-size: 12px; color: #a67c6a; }
.zone-card-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 15px; }
.zone-product-tag { padding: 4px 10px; border: 1px solid #ecd7bc; border-radius: 14px; background: #fff7e7; color: #946b3e; font-size: 12px; }
.zone-card-foot { margin-top: 15px; display: flex; justify-content: space-between; align-items: center; }
.zone-card-foot text:first-child { color: #bb6540; font-weight: 800; font-size: 20px; }
.zone-card-foot text:last-child { color: #87958c; font-size: 12px; }
.zone-add-cart { padding: 4px 10px; border-radius: 14px; background: #e85b36; color: #fff !important; font-size: 12px !important; }
.zone-empty { padding: 70px 15px; background: #fff; border-radius: 14px; text-align: center; color: #986d5b; }
.zone-empty text { display: block; margin-top: 10px; color: #774028; cursor: pointer; }
.zone-head-image { display:block!important; padding:0!important; background:#fff; min-height:0; overflow:hidden; }
.zone-head-image > view:not(.zone-actions) { display:none; }
.zone-column-photo { display:block; width:100%; height:auto; }
.zone-head-image .zone-actions { padding:12px 20px; }
@media(max-width:800px) { .zone-grid { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; } .zone-card image { height: 155px; } .zone-head { padding: 22px; align-items: start; flex-direction: column; } .zone-title { font-size: 26px; } .zone-wrap { padding: 12px 12px 85px; } }
</style>
