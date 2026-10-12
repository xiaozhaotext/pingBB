<template>
  <view class="container">
    <!-- #ifdef H5 -->
    <view class="desktop-home">
      <view class="desktop-topline"><view class="desktop-width">本地演示环境 · 当前商品及广告来自官方示例测试库，请勿作为真实经营数据</view></view>
      <view class="desktop-header desktop-width">
        <view class="desktop-logo" @click="uni.switchTab({ url: '/pages/index/index' })">优选<span>商城</span></view>
        <view class="desktop-search" @click="handleSearch">搜索商品、品牌与分类 <text>搜索</text></view>
        <view class="desktop-actions">
          <text @click="uni.navigateTo({ url: '/pages/cart/cart' })">购物车</text>
          <text @click="uni.switchTab({ url: '/pages/user/user' })">我的账户</text>
        </view>
      </view>
      <view class="desktop-nav"><view class="desktop-width desktop-nav-inner">
        <text @click="uni.switchTab({ url: '/pages/index/index' })">首页</text>
        <text @click="uni.switchTab({ url: '/pages/zone/pick' })">自选区</text>
        <text @click="uni.switchTab({ url: '/pages/zone/rebuy' })">复购区</text>
        <text @click="uni.navigateTo({ url: '/pages/category/category' })">全部分类</text>
        <text @click="handleNavToNewProductListPage">新品上架</text>
        <text @click="handleNavToHotProductListPage">热销商品</text>
      </view></view>
      <view class="desktop-main desktop-width">
        <view class="desktop-hero">
          <view class="desktop-hero-copy">
            <text class="desktop-eyebrow">山水之间 · 发现好物</text>
            <text class="desktop-hero-title">把美好带入旅途</text>
            <text class="desktop-hero-subtitle">在山水与日常之间，挑选心仪好物</text>
            <button @click="uni.navigateTo({ url: '/pages/category/category' })">浏览全部商品 →</button>
          </view>
          <view class="desktop-hero-media" v-if="advertiseList.length && advertiseList[0]?.pic">
            <image :src="advertiseList[0].pic" mode="aspectFill"></image>
          </view>
        </view>
        <view class="desktop-section" v-if="newProductList.length">
          <view class="desktop-section-title"><view><text>新品上架</text><small>最近更新的商品</small></view><text class="desktop-more" @click="handleNavToNewProductListPage">查看更多 →</text></view>
          <view class="desktop-products"><view class="desktop-product" v-for="item in newProductList.slice(0, 8)" :key="item.id" @click="handleNavToDetailPage(item)"><image :src="item.pic" mode="aspectFill"></image><text class="desktop-product-name">{{ item.name }}</text><text class="desktop-product-price">¥{{ item.price }}</text></view></view>
        </view>
        <view class="desktop-section" v-if="hotProductList.length">
          <view class="desktop-section-title"><view><text>人气推荐</text><small>本地示例数据</small></view><text class="desktop-more" @click="handleNavToHotProductListPage">查看更多 →</text></view>
          <view class="desktop-products"><view class="desktop-product" v-for="item in hotProductList.slice(0, 8)" :key="item.id" @click="handleNavToDetailPage(item)"><image :src="item.pic" mode="aspectFill"></image><text class="desktop-product-name">{{ item.name }}</text><text class="desktop-product-price">¥{{ item.price }}</text></view></view>
        </view>
        <view class="desktop-section" v-if="recommendProductList.length">
          <view class="desktop-section-title"><view><text>更多好物</text><small>继续探索</small></view></view>
          <view class="desktop-products"><view class="desktop-product" v-for="item in recommendProductList" :key="item.id" @click="handleNavToDetailPage(item)"><image :src="item.pic" mode="aspectFill"></image><text class="desktop-product-name">{{ item.name }}</text><text class="desktop-product-price">¥{{ item.price }}</text></view></view>
        </view>
        <view class="desktop-empty" v-if="!newProductList.length && !hotProductList.length && !recommendProductList.length">当前没有可展示的商品，请检查本地商城接口与商品数据。</view>
      </view>
    </view>
    <!-- #endif -->
    <view class="mobile-home">
    <view class="travel-intro"><text class="travel-brand">优选商城 · 旅途与生活</text><view class="travel-search" @click="handleSearch">⌕ 搜索心仪商品</view></view>
    <view class="travel-hero"><text class="travel-hero-title">山水有约，好物相伴</text><text class="travel-hero-sub">发现日常之美，开启轻松旅途</text></view>
    <view class="travel-entry-grid">
      <view class="travel-entry" @click="uni.navigateTo({ url: '/pages/category/category' })"><text>探索好物 →</text><small>按分类浏览真实在售商品</small></view>
      <view class="travel-entry" @click="uni.switchTab({ url: '/pages/zone/pick' })"><text>自选区</text><small>精选心仪好物</small></view>
      <view class="travel-entry" @click="uni.switchTab({ url: '/pages/zone/rebuy' })"><text>复购区</text><small>再次遇见所爱</small></view>
    </view>
    <view class="demo-note">本地演示环境：当前商品及广告为示例测试数据</view>
    <!-- 小程序头部兼容 -->
    <!-- #ifdef MP -->
    <view class="mp-search-box">
      <text class="yticon icon-saomiao nav-icon" @click="handleScan"></text>
      <input class="ser-input" type="text" value="输入关键字搜索" disabled @click="handleSearch" />
      <text class="yticon icon-icon-- nav-icon" @click="handleNavToNotice"></text>
    </view>
    <!-- #endif -->

    <!-- 头部轮播 -->
    <view class="carousel-section">
      <!-- 标题栏和状态栏占位符 -->
      <view class="titleNview-placing"></view>
      <!-- 背景色区域 -->
      <view class="titleNview-background" :style="{ backgroundColor: titleNViewBackground }"></view>
      <swiper class="carousel" circular @change="handleSwiperChange">
        <swiper-item
          v-for="(item, index) in advertiseList"
          :key="index"
          class="carousel-item"
          @click="handleNavToAdvertisePage(item)"
        >
          <image :src="item.pic" mode="aspectFill" />
        </swiper-item>
      </swiper>
      <!-- 自定义swiper指示器 -->
      <view class="swiper-dots">
        <text class="num">{{ swiperCurrent + 1 }}</text>
        <text class="sign">/</text>
        <text class="num">{{ swiperLength }}</text>
      </view>
    </view>

    <!-- 头部功能区 -->
    <view class="cate-section">
      <view class="cate-item" @click="handleNavToTopicPage">
        <image src="/static/temp/c3.png"></image>
        <text>专题</text>
      </view>
      <view class="cate-item" @click="handleNavToTopicPage">
        <image src="/static/temp/c5.png"></image>
        <text>话题</text>
      </view>
      <view class="cate-item" @click="handleNavToRecommendBrandPage">
        <image src="/static/temp/c6.png"></image>
        <text>优选</text>
      </view>
      <view class="cate-item" @click="handleNavToHotProductListPage">
        <image src="/static/temp/c7.png"></image>
        <text>特惠</text>
      </view>
    </view>

    <!-- 品牌制造商直供 -->
    <view class="f-header m-t" @click="handleNavToRecommendBrandPage">
      <image src="/static/icon_home_brand.png"></image>
      <view class="tit-box">
        <text class="tit">精选品牌</text>
        <text class="tit2">按品牌浏览在售商品</text>
      </view>
      <text class="yticon icon-you"></text>
    </view>
    <view class="guess-section">
      <view
        v-for="(item, index) in brandList"
        :key="index"
        class="guess-item"
        @click="handleNavToBrandDetailPage(item)"
      >
        <view class="image-wrapper-brand">
          <image :src="item.logo" mode="aspectFit"></image>
        </view>
        <text class="title clamp">{{ item.name }}</text>
        <text class="title2">商品数量：{{ item.productCount }}</text>
      </view>
    </view>

    <!-- 秒杀专区 -->
    <view class="f-header m-t" v-if="homeFlashPromotion">
      <image src="/static/icon_flash_promotion.png"></image>
      <view class="tit-box">
        <text class="tit">秒杀专区</text>
        <text class="tit2">下一场 {{ formatTime(homeFlashPromotion?.nextStartTime) }} 开始</text>
      </view>
      <view class="tit-box">
        <text class="tit2" style="text-align: right">本场结束剩余：</text>
        <view style="text-align: right">
          <text class="hour timer">{{ cutDownTime.endHour }}</text>
          <text>:</text>
          <text class="minute timer">{{ cutDownTime.endMinute }}</text>
          <text>:</text>
          <text class="second timer">{{ cutDownTime.endSecond }}</text>
        </view>
      </view>
    </view>
    <view class="guess-section" v-if="homeFlashPromotion">
      <view
        v-for="(item, index) in homeFlashPromotion.productList"
        :key="index"
        class="guess-item"
        @click="handleNavToDetailPage(item)"
      >
        <view class="image-wrapper">
          <image :src="item.pic" mode="aspectFill"></image>
        </view>
        <text class="title clamp">{{ item.name }}</text>
        <text class="title2 clamp">{{ item.subTitle }}</text>
        <text class="price">￥{{ item.flashPromotionPrice || item.price }}</text>
      </view>
    </view>

    <!-- 新鲜好物 -->
    <view class="f-header m-t" @click="handleNavToNewProductListPage">
      <image src="/static/icon_new_product.png"></image>
      <view class="tit-box">
        <text class="tit">新鲜好物</text>
        <text class="tit2">为你寻觅世间好物</text>
      </view>
      <text class="yticon icon-you"></text>
    </view>
    <view class="seckill-section">
      <scroll-view class="floor-list" scroll-x>
        <view class="scoll-wrapper">
          <view
            v-for="(item, index) in newProductList"
            :key="index"
            class="floor-item"
            @click="handleNavToDetailPage(item)"
          >
            <image :src="item.pic" mode="aspectFill"></image>
            <text class="title clamp">{{ item.name }}</text>
            <text class="title2 clamp">{{ item.subTitle }}</text>
            <text class="price">￥{{ item.price }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 人气推荐楼层 -->
    <view class="f-header m-t" @click="handleNavToHotProductListPage">
      <image src="/static/icon_hot_product.png"></image>
      <view class="tit-box">
        <text class="tit">人气推荐</text>
        <text class="tit2">大家都赞不绝口的</text>
      </view>
      <text class="yticon icon-you"></text>
    </view>

    <view class="hot-section">
      <view
        v-for="(item, index) in hotProductList"
        :key="index"
        class="guess-item"
        @click="handleNavToDetailPage(item)"
      >
        <view class="image-wrapper">
          <image :src="item.pic" mode="aspectFill"></image>
        </view>
        <view class="txt">
          <text class="title clamp">{{ item.name }}</text>
          <text class="title2">{{ item.subTitle }}</text>
          <text class="price">￥{{ item.price }}</text>
        </view>
      </view>
    </view>

    <!-- 猜你喜欢 -->
    <view class="f-header m-t">
      <image src="/static/icon_recommend_product.png"></image>
      <view class="tit-box">
        <text class="tit">猜你喜欢</text>
        <text class="tit2">你喜欢的都在这里了</text>
      </view>
      <text class="yticon icon-you" v-show="false"></text>
    </view>

    <view class="guess-section">
      <view
        v-for="(item, index) in recommendProductList"
        :key="index"
        class="guess-item"
        @click="handleNavToDetailPage(item)"
      >
        <view class="image-wrapper">
          <image :src="item.pic" mode="aspectFill"></image>
        </view>
        <text class="title clamp">{{ item.name }}</text>
        <text class="title2 clamp">{{ item.subTitle }}</text>
        <text class="price">￥{{ item.price }}</text>
      </view>
    </view>
    <uni-load-more :status="loadingType"></uni-load-more>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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
const loadData = async () => {
  try {
    const res = await getHomeContentAPI()
    const data = res.data
    advertiseList.value = data?.advertiseList || []
    brandList.value = data?.brandList || []
    homeFlashPromotion.value = data?.homeFlashPromotion || null
    newProductList.value = data?.newProductList || []
    hotProductList.value = data?.hotProductList || []
    swiperLength.value = advertiseList.value.length
    titleNViewBackground.value = titleNViewBackgroundList[0]
    // 加载推荐商品
    const recommendRes = await getRecommendProductListAPI(recommendPageParam.value)
    recommendProductList.value = recommendRes.data || []
    uni.stopPullDownRefresh()
  } catch (err) {
    console.error('加载首页数据失败', err)
    uni.stopPullDownRefresh()
  }
}

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
onReachBottom(async () => {
  recommendPageParam.value.pageNum++
  loadingType.value = 'loading'
  try {
    const res = await getRecommendProductListAPI(recommendPageParam.value)
    const list = res.data as PmsProduct[]
    if (!list || list.length === 0) {
      recommendPageParam.value.pageNum--
      loadingType.value = 'noMore'
    } else {
      recommendProductList.value = recommendProductList.value.concat(list)
      loadingType.value = 'more'
    }
  } catch (err) {
    loadingType.value = 'more'
  }
})

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

// 跳转到广告详情页
const handleNavToAdvertisePage = (item: SmsHomeAdvertise) => {
  // TODO: 实现广告详情页跳转逻辑
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
