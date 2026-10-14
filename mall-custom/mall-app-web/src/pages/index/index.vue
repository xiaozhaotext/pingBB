<template>
  <view class="container zone-home">
    <view class="zone-home-header"><view class="zone-home-width"><view class="travel-brand" @click="goTab('/pages/index/index')">智美游</view><view class="travel-search" @click="handleSearch">⌕ 搜索商品名称</view><text class="home-cart" @click="goPage('/pages/cart/cart')">购物车 →</text></view></view>
    <view class="zone-home-width">
      <view class="home-banner" v-if="advertiseList.length">
        <swiper class="banner-swiper" :current="swiperCurrent" circular autoplay :interval="4000" :duration="500" @change="handleSwiperChange">
          <swiper-item v-for="(item, index) in advertiseList" :key="item.id || index">
            <image class="banner-image" :src="item.pic" mode="aspectFill" @click="handleNavToAdvertisePage(item)" />
          </swiper-item>
        </swiper>
        <view class="banner-dots" v-if="advertiseList.length > 1">
          <text v-for="(item, index) in advertiseList" :key="index" :class="{ active: index === swiperCurrent }"></text>
        </view>
      </view>
      <view class="travel-hero"><text class="travel-hero-title">山水有约，好物相伴</text><text class="travel-hero-sub">品质好物 · 悦享生活</text></view><view class="hero-pagination"><text></text></view>
      <view class="home-announcement"><text class="announcement-label">公告</text><text class="announcement-copy">{{ announcement }}</text></view>
      <view class="home-quick-links"><view @click="goPage('/pages/category/category')"><text>商品分类</text><small>按品类找好物 →</small></view><view @click="goPage('/pages/cart/cart')"><text>购物车</text><small>查看已选商品 →</small></view></view>
      <view class="home-zones">
        <view class="home-zone brand-zone" @click="goTab('/pages/zone/pick')"><text class="zone-eyebrow">精选品牌 · 品质之选</text><text class="home-zone-title">自选区</text><text class="home-zone-caption">自选好物，发现心仪之选</text><text class="zone-arrow">→</text></view>
        <view class="home-zone rebuy-zone" @click="goTab('/pages/zone/rebuy')"><text class="home-zone-title">复购区</text><text class="home-zone-caption">品质好物 · 再次心选</text><text class="zone-arrow">→</text></view>
        <view class="home-zone consume-zone" @click="goPage('/pages/zone/consume')"><text class="home-zone-title">消费区</text><text class="home-zone-caption">预留专区 · 暂未开放</text><text class="reserve-badge">预留</text></view>
      </view>
      <view class="home-product-heading"><view><text>自选区推荐商品</text><small>精选好物</small></view><text @click="goTab('/pages/zone/pick')">更多 →</text></view>
      <view class="home-zone-status" v-if="pickLoading">正在加载自选区商品…</view>
      <view class="home-zone-status" v-else-if="pickError">商品加载失败<text @click="loadPick">，点击重试</text></view>
      <view class="home-zone-status" v-else-if="!pickProducts.length">自选区暂无已上架商品，请在后台专区管理中配置。</view>
      <view v-else class="home-rebuy-grid"><view class="home-rebuy-product" v-for="item in pickProducts" :key="item.id" @click="openPickProduct(item)"><image :src="item.pic" mode="aspectFill"/><view class="home-product-copy"><text class="home-product-name">{{item.name}}</text><text class="home-product-sub">{{item.subTitle}}</text><view><text class="home-product-price">¥{{item.price}}</text><text class="rebuy-label">自选区</text></view></view></view></view>
      <view class="home-product-heading"><view><text>复购区推荐商品</text><small>精选好物</small></view><text @click="goTab('/pages/zone/rebuy')">更多 →</text></view>
      <view class="home-zone-status" v-if="rebuyLoading">正在加载复购区商品…</view>
      <view class="home-zone-status" v-else-if="rebuyError">商品加载失败<text @click="loadRebuy">，点击重试</text></view>
      <view class="home-zone-status" v-else-if="!rebuyProducts.length">复购区暂无已上架商品，请在后台专区管理中配置。</view>
      <view v-else class="home-rebuy-grid"><view class="home-rebuy-product" v-for="item in rebuyProducts" :key="item.id" @click="openRebuyProduct(item)"><image :src="item.pic" mode="aspectFill"/><view class="home-product-copy"><text class="home-product-name">{{item.name}}</text><text class="home-product-sub">{{item.subTitle}}</text><view><text class="home-product-price">¥{{item.price}}</text><text class="rebuy-label">复购区</text></view></view></view></view>
    </view>
  </view>
  <MainNavigation active="home" />
</template>

<script setup lang="ts">
import MainNavigation from '@/components/MainNavigation.vue'
const goTab = (url: string) => url.startsWith('/pages/zone/') ? uni.navigateTo({ url }) : uni.switchTab({ url })
const goPage = (url: string) => url === '/pages/cart/cart' ? uni.switchTab({ url }) : uni.navigateTo({ url })

import { ref, computed } from 'vue'
import { getZoneProductsAPI, type ZoneProduct } from '@/apis/zone'
import {
  onLoad,
  onPullDownRefresh,
  onReachBottom,
  onPageScroll,
  onNavigationBarButtonTap,
  onNavigationBarSearchInputClicked,
} from '@dcloudio/uni-app'
import { getHomeContentAPI, getRecommendProductListAPI } from '@/apis/home'
import type { SmsHomeAdvertise, HomeFlashPromotion } from '@/types/home'
import type { PmsProduct } from '@/types/product'
import type { PmsBrand } from '@/types/brand'
import type { PageParam } from '@/types/common'

const announcement=ref('暂无公告')
import { http } from '@/utils/http'
const loadAnnouncement=async()=>{try{const r=await http<Array<{title:string}>>({url:'/home/announcements',method:'GET'});announcement.value=r.data?.[0]?.title||'暂无公告'}catch{announcement.value='公告暂不可用'}}
const pickProducts=ref<ZoneProduct[]>([])
const pickLoading=ref(false),pickError=ref(false)
const loadPick=async()=>{pickLoading.value=true;pickError.value=false;try{pickProducts.value=((await getZoneProductsAPI('PICK')).data||[]).slice(0,4)}catch{pickProducts.value=[];pickError.value=true}finally{pickLoading.value=false}}
const openPickProduct=(item:ZoneProduct)=>uni.navigateTo({url:`/pages/product/product?id=${item.id}&zone=PICK`})
const rebuyProducts=ref<ZoneProduct[]>([])
const rebuyLoading=ref(false),rebuyError=ref(false)
const loadRebuy=async()=>{rebuyLoading.value=true;rebuyError.value=false;try{rebuyProducts.value=((await getZoneProductsAPI('REBUY')).data||[]).slice(0,4)}catch{rebuyProducts.value=[];rebuyError.value=true}finally{rebuyLoading.value=false}}
const openRebuyProduct=(item:ZoneProduct)=>uni.navigateTo({url:`/pages/product/product?id=${item.id}&zone=REBUY`})
// 后台 Banner（/home/content → advertiseList）：后台启用/停用后应用端即时生效
const normalizeBannerPic=(pic:string)=>pic.replace(/^https?:\/\/macro-oss\.oss-cn-shenzhen\.aliyuncs\.com\//i,'/oss/')
const loadBanner=async()=>{try{const r=await getHomeContentAPI();const list=(r.data?.advertiseList||[]).filter(i=>!!i.pic).map(i=>({...i,pic:normalizeBannerPic(i.pic)}));advertiseList.value=list;swiperLength.value=list.length;if(!brandList.value.length&&r.data?.brandList?.length){brandList.value=r.data.brandList}}catch{advertiseList.value=[];swiperLength.value=0}}

// ===== 页面数据 =====
// 标题栏背景色
const titleNViewBackground = ref('')
// 标题栏背景色列表
const titleNViewBackgroundList = ['#285b40', '#356c50']
// 轮播图当前索引
const swiperCurrent = ref(0)
// 轮播图长度
const swiperLength = ref(0)
// 加载状态
const loadingType = ref<'more' | 'loading' | 'noMore'>('more')
// 页面滚动状态
const isScrolled = ref(false)

// 轮播广告
const advertiseList = ref<SmsHomeAdvertise[]>([])
// 品牌制造商直供
const brandList = ref<PmsBrand[]>([])
// 秒杀专区
const homeFlashPromotion = ref<HomeFlashPromotion | null>(null)
// 新鲜好物
const newProductList = ref<PmsProduct[]>([])
// 人气推荐
const hotProductList = ref<PmsProduct[]>([])
// 猜你喜欢
const recommendProductList = ref<PmsProduct[]>([])
// 猜你喜欢分页参数
const recommendPageParam = ref<PageParam>({
  pageNum: 1,
  pageSize: 4,
})

// ===== 计算属性 =====
// 秒杀倒计时计算
const cutDownTime = computed(() => {
  if (!homeFlashPromotion.value?.endTime) {
    return { endHour: '00', endMinute: '00', endSecond: '00' }
  }
  const endTime = new Date(homeFlashPromotion.value.endTime)
  const now = new Date()
  const endDateTime = new Date()
  endDateTime.setHours(endTime.getHours())
  endDateTime.setMinutes(endTime.getMinutes())
  endDateTime.setSeconds(endTime.getSeconds())
  const offsetTime = endDateTime.getTime() - now.getTime()
  const endHour = String(Math.floor(offsetTime / (60 * 60 * 1000))).padStart(2, '0')
  const offsetMinute = offsetTime % (60 * 60 * 1000)
  const endMinute = String(Math.floor(offsetMinute / (60 * 1000))).padStart(2, '0')
  const offsetSecond = offsetTime % (60 * 1000)
  const endSecond = String(Math.floor(offsetSecond / 1000)).padStart(2, '0')
  return { endHour, endMinute, endSecond }
})

// ===== 数据加载 =====
// 加载首页数据
const loadData = async () => { void loadAnnouncement(); void loadBanner(); await Promise.all([loadPick(),loadRebuy()]); uni.stopPullDownRefresh() }

// ===== 生命周期 =====
// 页面加载时执行
onLoad(() => {
  loadData()
})

// 下拉刷新时执行
onPullDownRefresh(() => {
  recommendPageParam.value.pageNum = 1
  loadData()
})

// 上拉加载更多时执行
onReachBottom(() => {})

// 页面滚动监听 - 改变导航栏按钮颜色
// @ts-ignore
onPageScroll((e: { scrollTop: number }) => {
  isScrolled.value = e.scrollTop > 50
  // #ifdef APP-PLUS
  // 动态改变导航栏按钮颜色 (App端)
  const pages = getCurrentPages()
  const page = pages[pages.length - 1]
  // @ts-ignore
  const currentWebview = page.$getAppWebview()
  if (currentWebview) {
    // @ts-ignore
    currentWebview.setTitleNViewButtonStyle(0, {
      color: isScrolled.value ? '#303133' : '#ffffff',
    })
    // @ts-ignore
    currentWebview.setTitleNViewButtonStyle(1, {
      color: isScrolled.value ? '#303133' : '#ffffff',
    })
  }
  // #endif
  // #ifdef H5
  // 动态改变导航栏图标颜色 (H5端) - 直接操作 DOM，因 titleNView buttons 使用内联 style
  const btnIcons = document.querySelectorAll('.uni-page-head-btn .uni-btn-icon')
  btnIcons.forEach((el: any) => {
    el.style.color = isScrolled.value ? '#303133' : '#ffffff'
  })
  // 同步修改搜索框放大镜图标颜色
  const searchIconPath = document.querySelector('.uni-page-head-search-icon path')
  if (searchIconPath) {
    searchIconPath.setAttribute('fill', isScrolled.value ? '#606266' : '#fff')
  }
  // #endif
})

// ===== 事件处理方法 =====
// 轮播图切换
const handleSwiperChange = (e: any) => {
  const index = e.detail.current
  swiperCurrent.value = index
  const changeIndex = index % titleNViewBackgroundList.length
  titleNViewBackground.value = titleNViewBackgroundList[changeIndex]
}

// 跳转到商品详情页
const handleNavToDetailPage = (item: PmsProduct) => {
  const id = item.id
  uni.navigateTo({
    url: `/pages/product/product?id=${id}`,
  })
}

// 跳转到广告详情页（按后台配置的 url 跳转）
const handleNavToAdvertisePage = (item: SmsHomeAdvertise) => {
  const target = item.url ? String(item.url).trim() : ''
  if (!target) {
    return
  }
  if (target.startsWith('/pages/')) {
    uni.navigateTo({ url: target })
  } else if (target.startsWith('http')) {
    // #ifdef H5
    window.open(target, '_blank')
    // #endif
    // #ifndef H5
    uni.setClipboardData({
      data: target,
      success: () => uni.showToast({ title: '链接已复制', icon: 'none' }),
    })
    // #endif
  } else {
    uni.showToast({ title: '暂不支持的跳转地址', icon: 'none' })
  }
}

// 跳转到品牌制造商直供页
const handleNavToRecommendBrandPage = () => {
  uni.navigateTo({
    url: '/pages/brand/list',
  })
}

// 跳转到品牌详情页
const handleNavToBrandDetailPage = (item: PmsBrand) => {
  uni.navigateTo({
    url: `/pages/brand/brandDetail?id=${item.id}`,
  })
}

// 跳转到新品列表页
const handleNavToNewProductListPage = () => {
  uni.navigateTo({
    url: '/pages/product/newProductList',
  })
}

// 跳转到人气推荐列表页
const handleNavToHotProductListPage = () => {
  uni.navigateTo({
    url: '/pages/product/hotProductList',
  })
}

// 跳转到专题页
const handleNavToTopicPage = () => {
  uni.showToast({
    icon: 'none',
    title: '功能开发中',
  })
}

// 跳转到搜索页
const handleSearch = () => {
  uni.navigateTo({
    url: '/pages/product/search',
  })
}

// 扫描功能（小程序端导航栏左侧按钮）
const handleScan = () => {
  uni.showToast({
    icon: 'none',
    title: '扫描功能开发中',
  })
}

// 跳转到通知页（小程序端导航栏右侧按钮）
const handleNavToNotice = () => {
  uni.navigateTo({
    url: '/pages/notice/notice',
  })
}

// 导航栏搜索框点击（H5/App端）
onNavigationBarSearchInputClicked(() => {
  handleSearch()
})

// ===== 其他方法 =====
// 时间格式化
const formatTime = (time: string | undefined) => {
  if (!time) return 'N/A'
  const date = new Date(time)
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

// 导航栏按钮点击事件
onNavigationBarButtonTap((e: { index: number }) => {
  const index = e.index
  if (index === 0) {
    uni.showToast({
      icon: 'none',
      title: '扫描功能开发中',
    })
  } else if (index === 1) {
    // #ifdef APP-PLUS
    const pages = getCurrentPages()
    const page = pages[pages.length - 1]
    // @ts-ignore
    const currentWebview = page.$getAppWebview()
    currentWebview.hideTitleNViewButtonRedDot({
      index,
    })
    // #endif
    uni.navigateTo({
      url: '/pages/notice/notice',
    })
  }
})
</script>

<style lang="scss">
/* #ifdef H5 */
@media (min-width: 900px) {
  body:has(.desktop-home) .uni-tabbar,
  body:has(.desktop-home) .uni-page-head { display: none !important; }
  body:has(.desktop-home) .uni-page-wrapper { padding-bottom: 0 !important; }
}
/* #endif */
/* #ifdef MP */
page {
  .cate-section {
    position: relative;
    z-index: 5;
    border-radius: 16rpx 16rpx 0 0;
    margin-top: -20rpx;
  }

  .carousel-section {
    padding: 0;

    .titleNview-placing {
      padding-top: 0;
      height: 0;
    }

    .swiper-dots {
      left: 45rpx;
      bottom: 40rpx;
    }
  }
}
/* #endif */

page {
  background: #f5f5f5;
}
</style>

<style lang="scss" scoped>
/* #ifdef H5 */
.desktop-home { display: none; }
@media (max-width: 899px) {
  .container { background: #f5f7f3; }
  .demo-note { padding: 8px 12px; background: #fff3d8; color: #805b1d; font-size: 11px; line-height: 1.4; }
  .cate-section { margin: 14px 12px 0; border-radius: 16px; background: #fff; box-shadow: 0 5px 18px #234b3012; overflow: hidden; }
  .f-header { margin: 16px 12px 0; border-radius: 12px 12px 0 0; background: #fff; padding: 12px 14px; }
  .guess-section, .hot-section, .seckill-section { margin: 0 12px; border-radius: 0 0 12px 12px; overflow: hidden; background: #fff; }
  .f-header .tit { color: #285b40; font-weight: 700; }
  .f-header .tit2 { color: #829087; }
}
@media (min-width: 900px) {
  .container { background: #f6f7f4; min-height: 100vh; }
  .mobile-home { display: none; }
  .desktop-home { display: block; color: #20332b; font-family: "Microsoft YaHei", "PingFang SC", sans-serif; }
  .desktop-width { max-width: 1240px; margin: 0 auto; padding: 0 28px; }
  .desktop-topline { background: #203d31; color: #dbe8dd; font-size: 12px; line-height: 32px; }
  .desktop-header { height: 92px; display: flex; align-items: center; gap: 54px; background: #fff; }
  .desktop-home { background: #f6f7f4; }
  .desktop-logo { min-width: 154px; color: #23553c; font-size: 30px; font-weight: 800; letter-spacing: 2px; cursor: pointer; }
  .desktop-logo span { color: #d28240; }
  .desktop-search { height: 44px; border: 1px solid #d9e2d9; border-radius: 7px; color: #89958c; background: #f8faf7; display: flex; align-items: center; justify-content: space-between; padding-left: 18px; flex: 1; cursor: pointer; font-size: 14px; }
  .desktop-search text { height: 44px; line-height: 44px; padding: 0 28px; margin: -1px -1px -1px 0; background: #285b40; border-radius: 0 7px 7px 0; color: white; font-weight: 600; }
  .desktop-actions { display: flex; gap: 24px; color: #394b3e; font-size: 14px; }
  .desktop-actions text, .desktop-nav text, .desktop-more { cursor: pointer; }
  .desktop-nav { background: #fff; border-top: 1px solid #eff2ed; border-bottom: 1px solid #e5ebe5; }
  .desktop-nav-inner { height: 52px; display: flex; align-items: center; gap: 52px; font-size: 15px; font-weight: 600; }
  .desktop-nav text:hover, .desktop-actions text:hover, .desktop-more:hover { color: #b76b36; }
  .desktop-main { padding-top: 28px; padding-bottom: 64px; }
  .desktop-hero { display: flex; height: 310px; background: linear-gradient(110deg, #dce9d9, #f0ede1); border-radius: 14px; overflow: hidden; }
  .desktop-hero-copy { width: 52%; padding: 53px 65px; display: flex; flex-direction: column; align-items: flex-start; }
  .desktop-eyebrow { color: #4a7358; font-size: 14px; letter-spacing: 4px; }
  .desktop-hero-title { font-size: 48px; font-weight: 800; margin-top: 12px; letter-spacing: 3px; }
  .desktop-hero-subtitle { font-size: 16px; color: #65776b; margin-top: 12px; }
  .desktop-hero-copy button { margin: 25px 0 0; background: #285b40; color: #fff; border-radius: 6px; font-size: 14px; line-height: 42px; padding: 0 22px; }
  .desktop-hero-media { width: 48%; overflow: hidden; }
  .desktop-hero-media image { width: 100%; height: 100%; }
  .desktop-section { margin-top: 38px; }
  .desktop-section-title { display: flex; justify-content: space-between; align-items: end; padding-bottom: 18px; }
  .desktop-section-title view { display: flex; align-items: baseline; gap: 16px; }
  .desktop-section-title view > text { font-size: 25px; font-weight: 750; }
  .desktop-section-title small { font-size: 13px; color: #89958c; }
  .desktop-more { font-size: 14px; color: #477454; }
  .desktop-products { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; }
  .desktop-product { background: #fff; border: 1px solid #e8ece6; border-radius: 10px; padding: 14px; cursor: pointer; transition: transform .2s, box-shadow .2s; }
  .desktop-product:hover { transform: translateY(-3px); box-shadow: 0 12px 25px #203d3119; }
  .desktop-product image { width: 100%; height: 210px; border-radius: 7px; background: #f3f5f1; }
  .desktop-product-name { display: block; font-size: 15px; margin-top: 12px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  .desktop-product-price { display: block; margin-top: 7px; color: #ad5c36; font-size: 20px; font-weight: 700; }
  .desktop-empty { margin-top: 35px; padding: 50px; text-align: center; background: #fff; color: #75867a; border-radius: 10px; }
}
/* #endif */
/* #ifdef MP */
.mp-search-box {
  position: absolute;
  left: 0;
  top: 30rpx;
  z-index: 9999;
  width: 100%;
  display: flex;
  align-items: center;
  padding: 0 20rpx;

  .nav-icon {
    font-size: 44rpx;
    color: #fff;
    padding: 0 10rpx;
  }

  .ser-input {
    flex: 1;
    height: 56rpx;
    line-height: 56rpx;
    text-align: center;
    font-size: 28rpx;
    color: $font-color-base;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.6);
  }
}

/* #endif */

.m-t {
  margin-top: 16rpx;
}

/* 搜索头部 - 覆盖在轮播图上方 */
.search-header {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 9999;
  width: 100%;
  padding: 0 30rpx;
  padding-top: calc(var(--status-bar-height) + 16rpx);
  box-sizing: border-box;
  /* 粉色渐变背景 */
  background: linear-gradient(135deg, #fa436a 0%, #ff6b6b 100%);
}

.search-wrapper {
  width: 100%;
  display: flex;
  align-items: center;
  height: 64rpx;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16rpx;
  padding: 0 24rpx;
}

.search-wrapper .yticon {
  font-size: 32rpx;
  color: #999;
}

.search-wrapper .icon-sousuo {
  margin-left: 8rpx;
}

.search-input {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 26rpx;
  color: #333;
  background: transparent;
  margin: 0 16rpx;
}

/* 头部 轮播图 */
.carousel-section {
  position: relative;
  padding-top: 10px;

  .titleNview-placing {
    height: var(--status-bar-height);
    padding-top: 44px;
    box-sizing: content-box;
  }

  .titleNview-background {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 426rpx;
    transition: 0.4s;
  }
}

.carousel {
  width: 100%;
  height: 350rpx;
  padding: 0 28rpx;
  box-sizing: border-box;

  .carousel-item {
    width: 100%;
    height: 100%;
    padding: 0;
    overflow: hidden;
  }

  image {
    width: 100%;
    height: 100%;
    border-radius: 10rpx;
  }
}

.swiper-dots {
  display: flex;
  position: absolute;
  left: 60rpx;
  bottom: 15rpx;
  width: 72rpx;
  height: 36rpx;
  background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAABkCAYAAADDhn8LAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyZpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuNi1jMTMyIDc5LjE1OTI4NCwgMjAxNi8wNC8xOS0xMzoxMzo0MCAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iIHhtbG5zOnN0UmVmPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VSZWYjIiB4bWxuczp4bXA9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC8iIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6OTk4MzlBNjE0NjU1MTFFOUExNjRFQ0I3RTQ0NEExQjMiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6OTk4MzlBNjA0NjU1MTFFOUExNjRFQ0I3RTQ0NEExQjMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIENDIDIwMTcgKFdpbmRvd3MpIj4gPHhtcE1NOkRlcml2ZWRGcm9tIHN0UmVmOmluc3RhbmNlSUQ9InhtcC5paWQ6Q0E3RUNERkE0NjExMTFFOTg5NzI4MTM2Rjg0OUQwOEUiIHN0UmVmOmRvY3VtZW50SUQ9InhtcC5kaWQ6Q0E3RUNERkI0NjExMTFFOTg5NzI4MTM2Rjg0OUQwOEUiLz4gPC9yZGY6RGVzY3JpcHRpb24+IDwvcmRmOlJERj4gPC94OnhtcG1ldGE+IDw/eHBhY2tldCBlbmQ9InIiPz4Gh5BPAAACTUlEQVR42uzcQW7jQAwFUdN306l1uWwNww5kqdsmm6/2MwtVCp8CosQtP9vg/2+/gY+DRAMBgqnjIp2PaCxCLLldpPARRIiFj1yBbMV+cHZh9PURRLQNhY8kgWyL/WDtwujjI8hoE8rKLqb5CDJaRMJHokC6yKgSCR9JAukmokIknCQJpLOIrJFwMsBJELFcKHwM9BFkLBMKFxNcBCHlQ+FhoocgpVwwnv0Xn30QBJGMC0QcaBVJiAMiec/dcwKuL4j1QMsVCXFAJE4s4NQA3K/8Y6DzO4g40P7UcmIBJxbEesCKWBDg8wWxHrAiFgT4fEGsB/CwIhYE+AeBAAdPLOcV8HRmWRDAiQVcO7GcV8CLM8uCAE4sQCDAlHcQ7x+ABQEEAggEEAggEEAggEAAgQACASAQQCCAQACBAAIBBAIIBBAIIBBAIABe4e9iAe/xd7EAJxYgEGDeO4j3EODp/cOCAE4sYMyJ5cwCHs4rCwI4sYBxJ5YzC84rCwKcXxArAuthQYDzC2JF0H49LAhwYUGsCFqvx5EF2T07dMaJBetx4cRyaqFtHJ8EIhK0i8OJBQxcECuCVutxJhCRoE0cZwMRyRcFefa/ffZBVPogePihhyCnbBhcfMFFEFM+DD4m+ghSlgmDkwlOgpAl4+BkkJMgZdk4+EgaSCcpVX7bmY9kgXQQU+1TgE0c+QJZUUz1b2T4SBbIKmJW+3iMj2SBVBWz+leVfCQLpIqYbp8b85EskIxyfIOfK5Sf+wiCRJEsllQ+oqEkQfBxmD8BBgA5hVjXyrBNUQAAAABJRU5ErkJggg==);
  background-size: 100% 100%;

  .num {
    width: 36rpx;
    height: 36rpx;
    border-radius: 50px;
    font-size: 24rpx;
    color: #fff;
    text-align: center;
    line-height: 36rpx;
  }

  .sign {
    position: absolute;
    top: 0;
    left: 50%;
    line-height: 36rpx;
    font-size: 12rpx;
    color: #fff;
    transform: translateX(-50%);
  }
}

/* 分类 */
.cate-section {
  display: flex;
  justify-content: space-around;
  align-items: center;
  flex-wrap: wrap;
  padding: 30rpx 22rpx;
  background: #fff;

  .cate-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    font-size: $font-sm + 2rpx;
    color: $font-color-dark;
  }

  /* 原图标颜色太深,不想改图了,所以加了透明度 */
  image {
    width: 88rpx;
    height: 88rpx;
    margin-bottom: 14rpx;
    border-radius: 50%;
    opacity: 0.7;
    box-shadow: 4rpx 4rpx 20rpx rgba(250, 67, 106, 0.3);
  }
}

.ad-1 {
  width: 100%;
  height: 210rpx;
  padding: 10rpx 0;
  background: #fff;

  image {
    width: 100%;
    height: 100%;
  }
}

/* 秒杀专区 */
.seckill-section {
  padding: 4rpx 30rpx 24rpx;
  background: #fff;

  .s-header {
    display: flex;
    align-items: center;
    height: 92rpx;
    line-height: 1;

    .s-img {
      width: 140rpx;
      height: 30rpx;
    }

    .tip {
      font-size: $font-base;
      color: $font-color-light;
      margin: 0 20rpx 0 40rpx;
    }

    .timer {
      display: inline-block;
      width: 40rpx;
      height: 36rpx;
      text-align: center;
      line-height: 36rpx;
      margin-right: 14rpx;
      font-size: $font-sm + 2rpx;
      color: #fff;
      border-radius: 2px;
      background: rgba(0, 0, 0, 0.8);
    }

    .icon-you {
      font-size: $font-lg;
      color: $font-color-light;
      flex: 1;
      text-align: right;
    }
  }

  .floor-list {
    white-space: nowrap;
  }

  .scoll-wrapper {
    display: flex;
    align-items: flex-start;
  }

  .floor-item {
    display: flex;
    flex-direction: column;
    width: 300rpx;
    margin-right: 20rpx;
    font-size: $font-sm + 2rpx;
    color: $font-color-dark;
    line-height: 1.8;

    image {
      width: 300rpx;
      height: 300rpx;
      border-radius: 6rpx;
    }

    .title {
      font-size: $font-lg;
      color: $font-color-dark;
      line-height: 80rpx;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .title2 {
      font-size: $font-sm;
      color: $font-color-light;
      line-height: 40rpx;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .price {
      color: $uni-color-primary;
    }
  }
}

.f-header {
  display: flex;
  align-items: center;
  height: 140rpx;
  padding: 6rpx 30rpx 8rpx;
  background: #fff;

  image {
    flex-shrink: 0;
    width: 80rpx;
    height: 80rpx;
    margin-right: 20rpx;
  }

  .tit-box {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .tit {
    font-size: $font-lg + 2rpx;
    color: $font-color-dark;
    line-height: 1.3;
  }

  .tit2 {
    font-size: $font-sm;
    color: $font-color-light;
  }

  .icon-you {
    font-size: $font-lg + 2rpx;
    color: $font-color-light;
  }

  .timer {
    display: inline-block;
    width: 40rpx;
    height: 36rpx;
    text-align: center;
    line-height: 36rpx;
    margin-right: 14rpx;
    font-size: $font-sm + 2rpx;
    color: #fff;
    border-radius: 2px;
    background: rgba(0, 0, 0, 0.8);
  }
}

/* 猜你喜欢 */
.guess-section {
  display: flex;
  flex-wrap: wrap;
  padding: 0 30rpx;
  background: #fff;

  .guess-item {
    display: flex;
    flex-direction: column;
    width: 48%;
    padding-bottom: 40rpx;

    &:nth-child(2n + 1) {
      margin-right: 4%;
    }
  }

  .image-wrapper {
    width: 100%;
    height: 330rpx;
    border-radius: 3px;
    overflow: hidden;

    image {
      width: 100%;
      height: 100%;
      opacity: 1;
    }
  }

  .image-wrapper-brand {
    width: 100%;
    height: 150rpx;
    border-radius: 3px;
    overflow: hidden;

    image {
      width: 100%;
      height: 100%;
      opacity: 1;
    }
  }

  .title {
    font-size: $font-lg;
    color: $font-color-dark;
    line-height: 80rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .title2 {
    font-size: $font-sm;
    color: $font-color-light;
    line-height: 40rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .price {
    font-size: $font-lg;
    color: $uni-color-primary;
    line-height: 1;
  }
}

.hot-section {
  display: flex;
  flex-wrap: wrap;
  padding: 0 30rpx;
  background: #fff;

  .guess-item {
    display: flex;
    flex-direction: row;
    width: 100%;
    padding-bottom: 40rpx;
  }

  .image-wrapper {
    width: 30%;
    height: 250rpx;
    border-radius: 3px;
    overflow: hidden;

    image {
      width: 100%;
      height: 100%;
      opacity: 1;
    }
  }

  .title {
    font-size: $font-lg;
    color: $font-color-dark;
    line-height: 80uprpx;
  }

  .title2 {
    font-size: $font-sm;
    color: $font-color-light;
    line-height: 40rpx;
    height: 80rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    display: block;
  }

  .price {
    font-size: $font-lg;
    color: $uni-color-primary;
    line-height: 80rpx;
  }

  .txt {
    width: 70%;
    display: flex;
    flex-direction: column;
    padding-left: 40rpx;
  }
}
</style>

<style scoped>
.zone-home{background:#f7f3eb;min-height:100vh;padding-bottom:90px}.zone-home-width{max-width:1160px;margin:auto;padding:0 20px}.zone-home-header{background:linear-gradient(120deg,#fff1bf,#edd68e);padding:20px 0 24px;border-radius:0 0 24px 24px}.zone-home-header .zone-home-width{position:relative}.travel-brand{font-size:20px}.travel-search{margin-top:14px;padding:12px 18px;font-size:14px}.home-cart{position:absolute;right:20px;top:4px;color:#5a6953;font-size:13px}.travel-hero{margin:18px 0 0;padding:28px 24px;min-height:155px;border-radius:20px;background-position:center 58%;box-shadow:0 10px 24px #81734c12}.travel-hero-title{font-size:26px}.travel-hero-sub{font-size:13px;margin-top:8px}.home-announcement{display:flex;gap:12px;align-items:center;padding:16px;margin:20px 0;background:#fffdf8;border-radius:16px;font-size:12px;color:#787360}.announcement-label{background:#b78235;color:white;padding:4px 8px;border-radius:5px}.home-zones{padding:12px;background:white;border-radius:22px;display:grid;grid-template-columns:1fr 1fr;gap:12px;box-shadow:0 12px 28px #8a73470c}.home-zone{position:relative;padding:25px 18px;min-height:132px;border-radius:16px;overflow:hidden;cursor:pointer}.brand-zone{grid-column:1/-1;min-height:176px;background:#e3ebd7 url('/static/travel-landscape.svg') right center/cover}.home-zone-title{display:block;font-size:29px;font-weight:700;color:#345441;letter-spacing:3px}.zone-eyebrow{display:block;margin-bottom:14px;color:#7a825d;font-size:12px}.home-zone-caption{display:block;font-size:11px;color:#6a795f;margin-top:10px}.rebuy-zone{background:linear-gradient(130deg,#f8edd4,#e1e8d6)}.consume-zone{background:linear-gradient(130deg,#fff7e9,#eae3d1);border:1px dashed #dfcfac}.zone-arrow{position:absolute;right:18px;bottom:17px;color:#536c50;font-size:22px}.reserve-badge{position:absolute;top:12px;right:12px;background:#ffffffb0;color:#958571;padding:3px 8px;border-radius:20px;font-size:10px}.home-product-heading{display:flex;justify-content:space-between;align-items:center;margin:28px 0 16px}.home-product-heading view>text{display:block;font-size:22px;font-weight:700;color:#30483e}.home-product-heading small{display:block;font-size:12px;color:#998d78;margin-top:7px}.home-product-heading>text{font-size:12px;color:#a27630;cursor:pointer}.home-rebuy-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.home-rebuy-product{background:white;border-radius:18px;overflow:hidden;cursor:pointer;border:1px solid #f0e8db}.home-rebuy-product image{width:100%;height:180px}.home-product-copy{padding:14px}.home-product-name{display:block;font-size:14px;line-height:1.5;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}.home-product-sub{display:block;color:#988f7d;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin:8px 0}.home-product-copy>view{display:flex;align-items:center;justify-content:space-between}.home-product-price{color:#b17f31;font-size:17px;font-weight:700}.rebuy-label{font-size:10px;color:#809178;border:1px solid #e1eadc;border-radius:4px;padding:2px 4px}.home-zone-status{padding:35px 18px;text-align:center;background:#fffdf8;border-radius:18px;color:#998d78;font-size:13px}
@media(min-width:900px){.zone-home-header .zone-home-width{display:flex;align-items:center;gap:65px}.travel-search{flex:1;margin:0}.home-cart{position:static}.travel-brand{font-size:26px}.travel-hero{min-height:290px;padding:60px 45px}.travel-hero-title{font-size:44px}.travel-hero-sub{font-size:17px}.brand-zone{min-height:245px;padding:40px}.home-zone-title{font-size:38px}.home-zone-caption{font-size:15px}.home-zone{padding:30px}.home-rebuy-grid{grid-template-columns:repeat(4,1fr)}.home-rebuy-product image{height:250px}.home-product-name{font-size:16px}.zone-home{padding-bottom:60px}}
</style>

<style scoped>
.zone-home{background:#f5f2eb}.zone-home-header{background:linear-gradient(120deg,#f9ebbb,#e7d294);border-radius:0;padding:24px 0 20px}.travel-brand{font-size:18px;letter-spacing:1px;color:#2a4035}.travel-search{background:#fffdf7;border:1px solid #f4eaca}.home-cart{font-size:12px;color:#5f6d55}
.travel-hero{min-height:178px;padding:30px 24px;border-radius:20px;background:linear-gradient(90deg,#142a36b8,#142a360a),url('/static/travel-photo/alpine-lake.jpg') center 52%/cover;box-shadow:0 12px 24px #29433817;border:0;display:flex;flex-direction:column;justify-content:center}.travel-hero-title{color:#fff;font-size:25px;font-weight:500;letter-spacing:2px;text-shadow:0 2px 10px #071c2980}.travel-hero-sub{color:#f5e9c7;font-size:12px;letter-spacing:3px;margin-top:12px}
.home-announcement{margin:18px 0;border:1px solid #eee7d9;box-shadow:0 4px 14px #635b4405}.announcement-label{background:#a58c4b}
.home-zones{padding:10px;border-radius:20px;gap:10px;box-shadow:0 12px 32px #635b440a;border:1px solid #eee9df}.home-zone{border-radius:14px;padding:22px 18px;min-height:132px;display:flex;flex-direction:column;justify-content:center}.brand-zone{min-height:182px;background:linear-gradient(90deg,#183d31c9,#183d3110),url('/static/travel-photo/coastal-retreat.jpg') center 62%/cover}.rebuy-zone{background:linear-gradient(90deg,#1e2921a8,#1e29211a),url('/static/travel-photo/slow-morning.jpg') center/cover}.consume-zone{background:linear-gradient(90deg,#203a489c,#203a4830),url('/static/travel-photo/ocean-terrace.jpg') center/cover;border:0}.home-zone-title{font-family:"Songti SC","Noto Serif SC",serif;font-weight:600;font-size:32px;letter-spacing:4px;color:#fff;text-shadow:0 2px 12px #142e3040}.brand-zone .home-zone-title{font-size:38px}.zone-eyebrow{color:#f1e1b5;font-size:11px;letter-spacing:2px;margin-bottom:12px}.home-zone-caption{color:#f6f4e9;font-size:11px;line-height:1.6;margin-top:10px}.zone-arrow{color:#f5e3b6;font-size:22px}.reserve-badge{background:#fff9e8e0;color:#6f725a;font-size:10px}
.home-product-heading{margin-top:26px}.home-product-heading view>text{font-size:23px;letter-spacing:1px}.home-rebuy-product{border:1px solid #eae4d9;border-radius:16px;box-shadow:0 7px 20px #5a563208}.home-rebuy-product image{background:#f7f6f2;object-fit:contain}.home-product-price{color:#9a7b39}.rebuy-label{color:#6b7e60;border-color:#dfe6d9}
@media(min-width:900px){.zone-home-header{padding:25px 0}.travel-brand{font-size:25px}.zone-home-width{max-width:1200px}.travel-hero{min-height:330px;padding:50px;border-radius:24px;background-position:center 50%}.travel-hero-title{font-size:44px}.travel-hero-sub{font-size:16px}.home-zones{padding:18px;gap:18px}.brand-zone{min-height:250px;padding:40px}.home-zone{padding:35px}.home-zone-title{font-size:36px}.brand-zone .home-zone-title{font-size:48px}.home-zone-caption{font-size:14px}.zone-eyebrow{font-size:13px}.rebuy-zone,.consume-zone{min-height:195px}.home-product-heading{margin-top:38px}.home-product-heading view>text{font-size:28px}}
</style>

<style scoped>
/* 参照视频首屏的纵向比例：品牌、搜索、横幅、公告、三区、商品。 */
.zone-home{background:#f8f5f0}.zone-home-width{padding:0 12px}.zone-home-header{padding:12px 0 13px;background:linear-gradient(115deg,#fce99e,#fff2c4);position:sticky;top:0;z-index:20;box-shadow:0 2px 10px #b79b5010}.travel-brand{font-family:"Songti SC",serif;font-size:23px;font-weight:700;letter-spacing:2px}.travel-search{margin-top:10px;font-size:14px;height:39px;padding:10px 16px;border-radius:28px}.home-cart{top:7px;font-size:11px}.travel-hero{min-height:0;height:auto;aspect-ratio:2.34;padding:18px 20px;margin-top:12px;border-radius:18px;background-position:center 55%;box-shadow:none}.travel-hero-title{font-family:"Songti SC",serif;font-size:26px;letter-spacing:1px}.travel-hero-sub{font-size:11px;letter-spacing:2px;margin-top:9px}.hero-pagination{height:12px;display:flex;align-items:center;justify-content:center}.hero-pagination text{width:7px;height:7px;border:2px solid #b98d36;border-radius:50%}.home-announcement{margin:9px 0 12px;padding:12px 11px;min-height:45px;border:0;border-radius:16px;gap:9px;font-size:12px}.announcement-label{background:#b85f34;padding:5px 8px;font-weight:600}.announcement-copy{font-weight:600;color:#3c3933}.home-zones{padding:12px;gap:9px;border-radius:19px;border:0;box-shadow:0 7px 18px #76634306}.home-zone{min-height:0;aspect-ratio:1.84;padding:14px 13px;border-radius:13px}.brand-zone{min-height:0;aspect-ratio:2.56;padding:18px 20px}.home-zone-title{font-size:26px;letter-spacing:2px}.brand-zone .home-zone-title{font-size:36px}.zone-eyebrow{font-size:9px;margin-bottom:8px;letter-spacing:1px}.home-zone-caption{font-size:9px;margin-top:8px;letter-spacing:.2px}.zone-arrow{font-size:17px;right:12px;bottom:10px}.reserve-badge{top:6px;right:6px;font-size:8px;padding:2px 5px}.home-product-heading{margin:16px 0 12px}.home-product-heading view>text{font-size:21px;color:#292d28}.home-product-heading small{font-size:10px;margin-top:6px}.home-product-heading>text{color:#b85f34;font-weight:600;font-size:11px}.home-rebuy-grid{gap:9px}.home-rebuy-product{border-radius:12px}.home-rebuy-product image{height:auto;aspect-ratio:1/1}.home-product-copy{padding:9px}.home-product-name{font-size:12px}.home-product-sub{font-size:10px}.home-product-price{font-size:15px}
@media(min-width:900px){.zone-home-width{max-width:1200px;padding:0 20px}.zone-home-header{padding:20px 0}.travel-brand{font-size:28px}.travel-search{margin-top:0;height:44px}.travel-hero{aspect-ratio:3.5;padding:45px;margin-top:20px}.travel-hero-title{font-size:44px}.travel-hero-sub{font-size:16px}.hero-pagination{height:20px}.home-announcement{padding:15px;font-size:14px}.home-zones{padding:20px;gap:16px}.brand-zone{aspect-ratio:4.2;padding:35px}.home-zone{aspect-ratio:2.9;padding:25px}.brand-zone .home-zone-title{font-size:48px}.home-zone-title{font-size:36px}.home-zone-caption,.zone-eyebrow{font-size:13px}.home-product-heading{margin-top:28px}.home-product-heading view>text{font-size:28px}.home-product-name{font-size:15px}.home-product-copy{padding:15px}}
</style>

<style scoped>
.zone-home{background:#f2f6f5}.zone-home-header{background:linear-gradient(115deg,#e3f0ec,#edf5f7);box-shadow:0 2px 12px #385f5810;border-bottom:1px solid #dce8e5}.travel-brand{color:#285a62}.travel-search{background:#fff;border-color:#deebe8;color:#809497}.home-cart{color:#26777c}.hero-pagination text{border-color:#378d93}.home-announcement{background:#fff;border:1px solid #e4eeeb}.announcement-label{background:#277f80}.announcement-copy{color:#406364}.home-zones{border:1px solid #e5ede9;box-shadow:0 8px 25px #34584c07}.zone-eyebrow{color:#edf5ef}.zone-arrow{color:#e6f5f6}.reserve-badge{background:#edf8f4e0;color:#527c70}.home-product-heading view>text{color:#284c53}.home-product-heading small{color:#809a99}.home-product-heading>text{color:#248189}.home-rebuy-product{border-color:#e0eae7;background:#fff}.home-product-sub{color:#839793}.home-product-price{color:#167c81}.rebuy-label{color:#4a8670;border-color:#d3e8dc}.home-zone-status{background:#fff;color:#819697}
</style>
<style scoped>
.home-banner{position:relative;margin-top:12px;border-radius:18px;overflow:hidden}
.banner-swiper{width:100%;height:180px}
.banner-image{width:100%;height:180px;display:block}
.banner-dots{position:absolute;left:0;right:0;bottom:10px;display:flex;justify-content:center;gap:6px}
.banner-dots text{width:7px;height:7px;border-radius:50%;background:#ffffff99}
.banner-dots text.active{background:#fff}
@media(min-width:900px){.banner-swiper{height:300px}.banner-image{height:300px}}
.home-quick-links{display:flex;gap:12px;margin:14px 0}.home-quick-links>view{flex:1;min-width:0;padding:14px 18px;background:#fff;border:1px solid #dcebe6;border-radius:12px;box-shadow:0 3px 14px #2752490d;cursor:pointer}.home-quick-links text,.home-quick-links small{display:block}.home-quick-links text{font-size:16px;font-weight:700;color:#285a62}.home-quick-links small{margin-top:4px;font-size:11px;color:#7a9992}
</style>
