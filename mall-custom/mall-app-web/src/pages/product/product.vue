<template>
  <DesktopHeader />
  <view class="container" :class="'theme-' + theme" @touchstart="edgeStart" @touchmove="edgeMove" @touchend="edgeEnd">
    <view class="demo-product-note">本地演示数据：商品与促销内容来自官方示例库，请勿作为真实经营信息。</view>
    <!-- 自定义导航栏 -->
    <view class="custom-navbar" :style="{ background: navBgColor }">
      <view class="status-bar" :style="{ height: statusBarHeight + 'px' }"></view>
      <view class="navbar-content">
        <view class="nav-back" @click="handleGoBack">
          <view class="back-arrow" :style="{ borderColor: navIconColor }"></view>
        </view>
        <view class="detail-search" @click="openProductSearch"><text>⌕</text><text class="search-placeholder">搜索你喜欢的好物</text><text class="search-action">搜索</text></view>
        <view class="header-action" aria-label="分享商品" @click="handleShare"><text class="yticon icon-fenxiang2"></text></view>
        <navigator class="header-action" url="/pages/cart/cart"><text class="yticon icon-gouwuche"></text></navigator>
      </view>
      <view class="detail-tabs"><text @click="scrollSection('.introduce-section')">商品</text><text @click="scrollSection('.eva-section')">评价</text><text @click="scrollSection('.detail-desc')">详情</text><text @click="scrollSection('.recommend-section')">推荐</text></view>
    </view>

    <!-- 占位区域，防止内容被导航栏遮挡 -->
    <!-- 轮播图本身需要顶到最上面，导航栏覆盖其上 -->
    <!-- 不需要占位，因为轮播图需要从顶部开始显示 -->

    <view class="product-travel-note"><text>智美游 · 沿途好物</text><text>把喜欢的生活，带在身边</text></view>
    <!-- 轮播图 -->
    <view class="carousel" @touchstart.stop @touchmove.stop @touchend.stop>
      <swiper circular :duration="400">
        <swiper-item class="swiper-item" v-for="(item, index) in imgList" :key="index">
          <view class="image-wrapper">
            <image :src="item" class="loaded" mode="aspectFill"></image>
            <WatermarkOverlay v-bind="watermark" />
          </view>
        </swiper-item>
      </swiper>
    </view>

    <!-- 商品基本信息 -->
    <view class="introduce-section">
      <text class="title">{{ product.name }}</text
      ><br />
      <text class="title2">{{ product.subTitle }}</text>
      <view class="shop-row">
        <text class="shop-label">商家</text>
        <text class="shop-name">{{ shopName }}</text>
      </view>
      <view class="price-box">
        <text class="price-tip">¥</text>
        <text class="price">{{ product.price }}</text>
        <text class="m-price">¥{{ product.originalPrice }}</text>
      </view>
      <view class="bot-row">
        <text>销量: {{ product.sale }}</text>
        <text>库存: {{ product.stock }}</text>
      </view>
    </view>

    <!-- 分享不承诺尚未核实的红包或推广收益 -->
    <view class="share-section" @click="openConsultation">
      <text class="tit">在线咨询</text><text class="share-btn">咨询商品或订单 ›</text>
    </view>
    <!-- 商品规格、参数、优惠券等 -->
    <view class="c-list">
      <view class="c-row b-b" @click="handleToggleSpec">
        <text class="tit">购买类型</text>
        <view class="con">
          <text class="selected-text" v-for="(sItem, sIndex) in specSelected" :key="sIndex">
            {{ sItem.name }}
          </text>
        </view>
        <text class="yticon icon-you"></text>
      </view>
      <view class="c-row b-b" @click="handleToggleAttr">
        <text class="tit">商品参数</text>
        <view class="con">
          <text class="con t-r">查看</text>
        </view>
        <text class="yticon icon-you"></text>
      </view>
      <view class="c-row b-b" @click="handleToggleCoupon('show')">
        <text class="tit">优惠券</text>
        <text class="con t-r red">领取优惠券</text>
        <text class="yticon icon-you"></text>
      </view>
      <view class="c-row b-b">
        <text class="tit">促销活动</text>
        <view class="con-list">
          <text v-for="item in promotionTipList" :key="item">{{ item }}</text>
        </view>
      </view>
      <view class="c-row b-b">
        <text class="tit">服务</text>
        <view class="bz-list con">
          <text v-for="item in serviceList" :key="item">{{ item }} ·</text>
        </view>
      </view>
    </view>

    <!-- 商品评价 -->
    <view class="eva-section">
      <view class="e-header">
        <text class="tit">商品评价</text>
        <text class="e-count">{{ commentTotal }} 条<template v-if="commentAvgStar">· 好评 {{ commentAvgStar }} 分</template></text>
        <text class="e-more" @click="handleWriteComment">写评价 ›</text>
      </view>
      <view v-if="commentLoading" class="eva-empty">评价加载中…</view>
      <view v-else-if="commentList.length === 0" class="eva-empty">暂无评价，购买并收货后可发表第一条评价</view>
      <view v-else class="eva-list">
        <view class="eva-item" v-for="c in commentList" :key="c.id">
          <view class="eva-item-head">
            <image class="eva-avatar" :src="c.memberIcon || '/static/missing-face.png'" mode="aspectFill"></image>
            <view class="eva-item-meta">
              <text class="eva-nick">{{ c.memberNickName || '匿名用户' }}</text>
              <text class="eva-stars">{{ formatStars(c.star) }}</text>
            </view>
            <text class="eva-time">{{ formatDateTime(c.createTime) }}</text>
          </view>
          <text class="eva-content">{{ c.content }}</text>
          <text v-if="c.productAttribute" class="eva-attr">{{ c.productAttribute }}</text>
          <view v-if="c.replays && c.replays.length" class="eva-replay-box">
            <view class="eva-replay" v-for="r in c.replays" :key="r.id">商家回复：{{ r.content }}</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 品牌信息 -->
    <view class="brand-info">
      <view class="d-header">
        <text>品牌信息</text>
      </view>
      <view class="brand-box" @click="handleNavToBrandDetail">
        <view class="image-wrapper">
          <image :src="brand.logo" class="loaded" mode="aspectFit"></image>
        </view>
        <view class="title">
          <text>{{ brand.name }}</text>
          <text>品牌首字母：{{ brand.firstLetter }}</text>
        </view>
      </view>
    </view>

    <!-- 图文详情 -->
    <view class="detail-desc">
      <view class="d-header">
        <text>图文详情</text>
      </view>
      <rich-text :nodes="desc"></rich-text>
    </view>

    <view class="recommend-section"><view class="section-heading"><text>发现更多好物</text><text>{{ recommendationLabel }}</text></view>
      <view class="recommend-empty" v-if="recommendLoading">正在加载商品…</view>
      <view class="recommend-empty" v-else-if="recommendError" @click="loadRecommendations">加载失败，点击重试</view>
      <view class="recommend-grid" v-else-if="recommendProducts.length"><view class="recommend-card" v-for="item in recommendProducts" :key="item.id" @click="openRecommended(item.id)"><image :src="item.pic" mode="aspectFill"/><text class="recommend-name">{{ item.name }}</text><text class="recommend-price">¥{{ item.price }}</text></view></view>
      <view class="recommend-empty" v-else>暂无其他同类商品</view>
    </view>
    <view class="to-top" v-if="scrollY > 600" @click="scrollSection('.introduce-section')">↑<text>顶部</text></view>
    <!-- 底部操作菜单 -->
    <view class="page-bottom">
      <view class="p-b-btn" @click="handleNavToBrandDetail">
        <svg class="line-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 10.5 4.5 4h15l1.5 6.5M4.5 10.5v9h15v-9M3 10.5a3 3 0 0 0 6 0 3 3 0 0 0 6 0 3 3 0 0 0 6 0M9 19.5v-5h6v5" /></svg>
        <text>店铺</text>
      </view>
      <navigator url="/pages/consultation/consultation" class="p-b-btn">
        <svg class="line-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3.5a8.5 8.5 0 0 0-7.2 13l-.8 4 4-1.2A8.5 8.5 0 1 0 12 3.5Z" /><path d="M8.5 11h.1M12 11h.1M15.5 11h.1" /></svg>
        <text>客服</text>
      </navigator>
      <view class="p-b-btn" :class="{ active: favorite }" @click="handleToggleFavorite">
        <text class="yticon icon-shoucang"></text>
        <text>收藏</text>
      </view>

      <view class="action-btn-group">
        <button type="primary" class="action-btn no-border add-cart-btn" @click="handleAddToCart">加入购物车</button>
        <button type="primary" class="action-btn no-border buy-now-btn" @click="handleBuy">立即购买</button>
      </view>
    </view>

    <!-- 规格-模态层弹窗 -->
    <view class="popup spec" :class="specClass" @touchmove.stop.prevent @click="handleToggleSpec">
      <view class="mask"></view>
      <view class="layer attr-content" @click.stop>
        <view class="a-t">
          <image :src="product.pic"></image>
          <view class="right">
            <text class="price">¥{{ product.price }}</text>
            <text class="stock">库存：{{ product.stock }}件</text>
            <view class="selected">
              已选：
              <text class="selected-text" v-for="(sItem, sIndex) in specSelected" :key="sIndex">
                {{ sItem.name }}
              </text>
            </view>
          </view>
        </view>
        <view v-for="(item, index) in specList" :key="index" class="attr-list">
          <text>{{ item.name }}</text>
          <view class="item-list">
            <template v-for="(childItem, childIndex) in specChildList" :key="childIndex">
              <text
                v-if="childItem.pid === item.id"
                class="tit"
                :class="{ selected: childItem.selected }"
                @click="handleSelectSpec(childIndex, childItem.pid)"
              >
                {{ childItem.name }}
              </text>
            </template>
          </view>
        </view>
        <button class="btn" @click="handleToggleSpec">完成</button>
      </view>
    </view>

    <!-- 属性-模态层弹窗 -->
    <view class="popup spec" :class="attrClass" @touchmove.stop.prevent @click="handleToggleAttr">
      <view class="mask"></view>
      <view class="layer attr-content no-padding" @click.stop>
        <view class="c-list">
          <view v-for="item in attrList" class="c-row b-b" :key="item.key">
            <text class="tit">{{ item.key }}</text>
            <view class="con">
              <text class="con t-r">{{ item.value }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 优惠券面板 -->
    <view
      class="mask"
      :class="couponState === 0 ? 'none' : couponState === 1 ? 'show' : ''"
      @click="handleToggleCoupon"
    >
      <view class="mask-content" @click.stop.prevent>
        <view
          class="coupon-item"
          v-for="(item, index) in couponList"
          :key="index"
          @click="handleAddCoupon(item)"
        >
          <view class="con">
            <view class="left">
              <text class="title">{{ item.name }}</text>
              <text class="time">有效期至{{ formatDateTime(item.endTime) }}</text>
            </view>
            <view class="right">
              <text class="price">{{ item.amount }}</text>
              <text>满{{ item.minPoint }}可用</text>
            </view>
            <view class="circle l"></view>
            <view class="circle r"></view>
          </view>
          <text class="tips">{{ formatCouponUseType(item.useType) }}</text>
        </view>
      </view>
    </view>
    <!-- 发表评价弹窗 -->
    <view class="popup" :class="commentPopup" @touchmove.stop.prevent @click="closeCommentPopup">
      <view class="mask"></view>
      <view class="layer comment-layer" @click.stop>
        <view class="comment-title">发表评价</view>
        <view v-if="pendingLoading" class="eva-empty">加载可评价订单…</view>
        <view v-else-if="pendingList.length === 0" class="eva-empty">暂无可评价的订单商品（需订单已完成且未评价）</view>
        <template v-else>
          <view class="pending-list">
            <view
              class="pending-item"
              :class="{ active: commentForm.orderId === Number(p.orderId) && commentForm.productId === Number(p.productId) }"
              v-for="p in pendingList"
              :key="p.orderId + '-' + p.productId"
              @click="selectPending(p)"
            >
              <image class="pending-pic" :src="p.productPic" mode="aspectFill"></image>
              <view class="pending-info">
                <text class="pending-name">{{ p.productName }}</text>
                <text class="pending-attr">{{ p.productAttr }}</text>
              </view>
            </view>
          </view>
          <view class="star-row">
            <text
              class="star"
              :class="{ active: st <= commentForm.star }"
              v-for="st in 5"
              :key="st"
              @click="commentForm.star = st"
            >★</text>
          </view>
          <textarea class="comment-input" v-model="commentForm.content" :maxlength="500" placeholder="说说这件商品怎么样（1-500字）" />
          <button class="btn" :disabled="commentSubmitting" @click="submitComment">提交评价</button>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const fallbackPage=ref('/pages/index/index')
// 来源专区：从专区页进入商品详情时带 zone 参数，加购/立即购买时按该专区归集
const zoneCode = ref<ZoneCode | undefined>()
const goFallbackPage=()=>{
  const url=fallbackPage.value
  if(url.startsWith('/pages/zone/')) uni.redirectTo({url})
  else uni.switchTab({url})
}
let removeEntryGuard:(()=>void)|undefined
function closeDetailOverlay(){
 if(attrClass.value==='show'){handleToggleAttr();return true}
 if(specClass.value==='show'){handleToggleSpec();return true}
 if(couponState.value===1){handleToggleCoupon();return true}
 return false
}
function guardDirectEntry(){
 // #ifdef H5
 if(getCurrentPages().length<=1){
  const state={...window.history.state}
  window.history.replaceState({...state,mallDetailEntryBase:true},'',window.location.href)
  window.history.pushState({...state,mallDetailEntryBase:false},'',window.location.href)
 }
 const detailHash=window.location.hash
 const back=(event:PopStateEvent)=>{
  if(!event.state?.mallDetailEntryBase){
   if(window.location.hash===detailHash){event.stopImmediatePropagation();closeDetailOverlay()}
   return
  }
  event.stopImmediatePropagation()
  if(closeDetailOverlay()){window.history.pushState({...event.state,mallDetailEntryBase:false},'',window.location.href);return}
  window.removeEventListener('popstate',back,true)
  window.history.replaceState({...event.state,mallDetailEntryBase:false},'',window.location.href)
  goFallbackPage()
 }
 window.addEventListener('popstate',back,true)
 removeEntryGuard=()=>window.removeEventListener('popstate',back,true)
 // #endif
}
onUnload(()=>removeEntryGuard?.())
onBackPress(()=>{if(closeDetailOverlay())return true;if(getCurrentPages().length<=1){handleGoBack();return true}return false})
let gesture:{x:number;y:number;valid:boolean}|null=null
function edgeStart(e:any){
 const t=e.touches?.[0];if(!t)return
 let blocked=e.target?.closest?.('uni-swiper,.carousel,.popup,.mask,input,textarea')
 // #ifdef H5
 blocked=document.elementFromPoint(t.clientX,t.clientY)?.closest('uni-swiper,.carousel,.popup,.mask,input,textarea') || blocked
 // #endif
 gesture={x:t.clientX,y:t.clientY,valid:t.clientX<=24&&!blocked}
}
function edgeMove(e:any){
 const t=e.touches?.[0];if(!t||!gesture?.valid)return
 if(t.clientX-gesture.x>24 && Math.abs(t.clientY-gesture.y)<Math.abs(t.clientX-gesture.x)/2 && e.cancelable)e.preventDefault()
}
function edgeEnd(e:any){
 const t=e.changedTouches?.[0];if(t&&gesture?.valid&&t.clientX-gesture.x>80&&Math.abs(t.clientY-gesture.y)<50)handleGoBack()
 gesture=null
}
function scrollSection(selector:string){
 // #ifdef H5
 try{
   const section=document.querySelector(selector) as HTMLElement|null
   if(section){
     // 优先用 uni.pageScrollTo（H5 下更可靠），失败时回退到 scrollIntoView
     const rect=section.getBoundingClientRect()
     const top=Math.max(0,window.scrollY+rect.top-112)
     if(typeof uni!=='undefined'&&uni.pageScrollTo){
       uni.pageScrollTo({scrollTop:top,duration:300})
     }else{
       section.style.scrollMarginTop='112px'
       section.scrollIntoView({behavior:'smooth',block:'start'})
     }
     return
   }
 }catch(e){/* 忽略，回退到 uni API */}
 // #endif
 uni.createSelectorQuery().select(selector).boundingClientRect((rect:any)=>{if(rect)uni.pageScrollTo({scrollTop:Math.max(0,scrollY.value+rect.top-112),duration:200})}).exec()
}

const recommendationLabel=ref('同类商品')
const recommendProducts=ref<PmsProduct[]>([])
const recommendLoading=ref(false)
const recommendError=ref(false)
async function loadRecommendations(){
 recommendLoading.value=true;recommendError.value=false
 try{const response=await searchProductListAPI({pageNum:1,pageSize:9,sort:0,productCategoryId:product.value.productCategoryId});recommendProducts.value=(response.data?.list||[]).filter(item=>item.id!==product.value.id&&item.publishStatus===1).slice(0,8);recommendationLabel.value='同类商品';if(!recommendProducts.value.length){const other=await searchProductListAPI({pageNum:1,pageSize:9,sort:0});recommendProducts.value=(other.data?.list||[]).filter(item=>item.id!==product.value.id&&item.publishStatus===1).slice(0,8);recommendationLabel.value='商城好物'}}
 catch{recommendError.value=true;recommendProducts.value=[]}
 finally{recommendLoading.value=false}
}
const openProductSearch=()=>uni.navigateTo({url:'/pages/product/search'})
const openRecommended=(id:number)=>uni.navigateTo({url:'/pages/product/product?id='+id})

const openConsultation = () => uni.navigateTo({ url: '/pages/consultation/consultation?productId=' + product.value.id })

import DesktopHeader from '@/components/DesktopHeader.vue'
import WatermarkOverlay from '@/components/WatermarkOverlay.vue'
import { ref, computed, watch } from 'vue'
import { onLoad, onShow, onPageScroll, onUnload, onBackPress } from '@dcloudio/uni-app'
import { getTheme, type AppTheme } from '@/utils/theme'
import { getProductDetailAPI, searchProductListAPI, getProductCommentListAPI, createProductCommentAPI, getProductCommentPendingAPI } from '@/apis/product'
import { getSiteInfoAPI } from '@/apis/home'
import { addCartAPI, getCartListAPI } from '@/apis/cart'
import type { ZoneCode } from '@/apis/zone'
import { getProductCouponListAPI, addMemberCouponAPI } from '@/apis/coupon'
import { createReadHistoryAPI } from '@/apis/memberReadHistory'
import {
  createProductCollectionAPI,
  deleteProductCollectionAPI,
  getProductCollectionDetailAPI,
} from '@/apis/memberProductCollection'
import { useMemberStore } from '@/stores/member'
import type {
  PmsProduct,
  PmsProductAttribute,
  PmsProductAttributeValue,
  PmsSkuStock,
  PmsProductFullReduction,
  PmsProductLadder,
  SpecOption,
  ServiceItem,
  ShareItem,
} from '@/types/product'
import type { PmsBrand } from '@/types/brand'
import type { SmsCoupon } from '@/types/coupon'
import { formatDate } from '@/utils/date'

// ===== 导航栏相关 =====
const statusBarHeight = ref(0)
const scrollY = ref(0)

// 导航栏背景色：初始透明，滚动超过一定距离后渐变为白色
const navBgColor = computed(() => {
  const opacity = Math.min(scrollY.value / 200, 1)
  return `rgba(255, 255, 255, ${opacity})`
})

// 导航栏标题透明度：滚动超过一定距离后显示
const navTitleOpacity = computed(() => {
  return Math.min(scrollY.value / 200, 1)
})

// 导航栏图标颜色：初始白色（在轮播图上），滚动后变深色
const navIconColor = computed(() => {
  const progress = Math.min(scrollY.value / 200, 1)
  return progress > 0.5 ? '#333' : '#fff'
})

// ===== 常量 =====

// 默认服务列表
const defaultServiceList: ServiceItem[] = [
  { id: 1, name: '无忧退货' },
  { id: 2, name: '快速退款' },
  { id: 3, name: '免费包邮' },
]

// 默认分享列表
const defaultShareList: ShareItem[] = [
  { type: 1, icon: '/static/temp/share_wechat.png', text: '微信好友' },
  { type: 2, icon: '/static/temp/share_moment.png', text: '朋友圈' },
  { type: 3, icon: '/static/temp/share_qq.png', text: 'QQ好友' },
  { type: 4, icon: '/static/temp/share_qqzone.png', text: 'QQ空间' },
]

// ===== 状态变量 =====

// 换肤主题（从 uni.storage 读取，设置页修改后返回本页时 onShow 刷新）
const theme = ref<AppTheme>(getTheme())

// 弹窗状态
const specClass = ref<'none' | 'show' | 'hide'>('none')
const attrClass = ref<'none' | 'show' | 'hide'>('none')
const couponState = ref(0)
// Each open overlay gets a same-page history entry; browser back closes it first.
watch([specClass,attrClass,couponState],([spec,attr,coupon])=>{
 // #ifdef H5
 const open=spec==='show'||attr==='show'||coupon===1
 if(open&&!window.history.state?.mallDetailOverlay) window.history.pushState({...window.history.state,mallDetailOverlay:true},'',window.location.href)
 if(!open&&window.history.state?.mallDetailOverlay) window.history.back()
 // #endif
})


// 商品数据
const membershipOffer = ref<string | undefined>()
const product = ref<PmsProduct>({} as PmsProduct)
const brand = ref<PmsBrand>({} as PmsBrand)
const imgList = ref<string[]>([])
const desc = ref('')

// 规格相关
const specList = ref<PmsProductAttribute[]>([])
const specChildList = ref<SpecOption[]>([])
const specSelected = ref<SpecOption[]>([])
const skuStockList = ref<PmsSkuStock[]>([])

// 属性、促销、服务
const attrList = ref<{ key: string; value: string }[]>([])
const promotionTipList = ref<string[]>([])
const serviceList = ref<string[]>([])

// 优惠券
const couponList = ref<SmsCoupon[]>([])

// 商家与评价
const shopName = ref('平台自营')
const commentList = ref<any[]>([])
const commentTotal = ref(0)
const commentAvgStar = ref<number | null>(null)
const commentLoading = ref(false)

// 写评价弹窗
const commentPopup = ref<'none' | 'show' | 'hide'>('none')
const pendingList = ref<any[]>([])
const pendingLoading = ref(false)
const commentSubmitting = ref(false)
const commentForm = ref<{ orderId: number | null; productId: number | null; star: number; content: string }>({
  orderId: null,
  productId: null,
  star: 5,
  content: '',
})

// 格式化时间（保留到秒）
const formatDateTime = (time: string | null | undefined): string => {
  if (!time) return 'N/A'
  const date = new Date(time)
  if (isNaN(date.getTime())) return 'N/A'
  return formatDate(date, 'yyyy-MM-dd hh:mm:ss')
}

// 格式化优惠券使用类型
const formatCouponUseType = (useType: number): string => {
  if (useType === 0) return '全场通用'
  if (useType === 1) return '指定分类商品可用'
  if (useType === 2) return '指定商品可用'
  return ''
}

// 收藏状态
const favorite = ref(false)

// 分享
const shareList = ref<ShareItem[]>([])

// 会员状态
const memberStore = useMemberStore()

// ===== 数据加载 =====

// 加载商品详情数据
const loadData = async (id: number) => {
  if (!id) return

  try {
    const res = await getProductDetailAPI(id)
    if (res.data) {
      const data = res.data
      membershipOffer.value = data.membershipOffer
      product.value = data.product
      skuStockList.value = data.skuStockList
      brand.value = data.brand
      shopName.value = data.shopName || '平台自营'
      commentTotal.value = data.commentCount ?? 0

      // 初始化原价（如果不存在）
      if (!product.value.originalPrice) {
        product.value.originalPrice = product.value.price
      }

      initImgList()
      initServiceList()
      initSpecList(data)
      initAttrList(data)
      initPromotionTipList(data)
      initProductDesc()
      void loadRecommendations()
      saveReadHistory()
      initProductCollection()
      loadComments(id)
    }
  } catch (error) {
    console.error('加载商品详情失败:', error)
  }
}

// ===== onLoad / onShow =====

// 页面显示时刷新主题（从设置页切换主题后返回本页即生效）
onShow(() => {
  theme.value = getTheme()
})

// 页面加载
onLoad((options) => {
  const requestedZone = options?.zone ? String(options.zone).toUpperCase() : ''
  zoneCode.value = requestedZone === 'PICK' || requestedZone === 'REBUY' || requestedZone === 'CONSUME'
    ? (requestedZone as ZoneCode)
    : undefined
  fallbackPage.value=options?.zone==='REBUY'?'/pages/zone/rebuy':options?.zone==='CONSUME'?'/pages/zone/consume':options?.zone==='PICK'?'/pages/zone/pick':'/pages/index/index'
  guardDirectEntry()
  // 获取状态栏高度
  const systemInfo = uni.getSystemInfoSync()
  statusBarHeight.value = systemInfo.statusBarHeight || 0

  const id = options?.id ? Number(options.id) : 0
  shareList.value = defaultShareList
  loadWatermark()
  loadData(id)
})

// 商品图水印设置（后台可开关，失败时保持关闭，不影响详情展示）
const watermark = ref({ enabled: false, text: '', position: 'bottomRight', opacity: '0.35', size: 18 })
const loadWatermark = async () => {
  try {
    const response = await getSiteInfoAPI()
    const config = response.data?.watermark ?? {}
    watermark.value = {
      enabled: config.enabled === '1',
      text: config.text ?? '',
      position: config.position ?? 'bottomRight',
      opacity: config.opacity ?? '0.35',
      size: Number(config.size) || 18,
    }
  } catch {
    watermark.value = { enabled: false, text: '', position: 'bottomRight', opacity: '0.35', size: 18 }
  }
}

// 监听页面滚动
onPageScroll((e: { scrollTop: number }) => {
  scrollY.value = e.scrollTop
})

// ===== 初始化方法 =====

// 初始化轮播图列表
const initImgList = () => {
  const tempPics = product.value.albumPics?.split(',') || []
  tempPics.unshift(product.value.pic)
  for (const item of tempPics) {
    if (item != null && item !== '') {
      imgList.value.push(item)
    }
  }
}

// 初始化服务列表
const initServiceList = () => {
  const serviceIds = product.value.serviceIds || ''
  for (const item of defaultServiceList) {
    if (serviceIds.indexOf(String(item.id)) !== -1) {
      serviceList.value.push(item.name)
    }
  }
}

// 初始化商品规格
const initSpecList = (data: any) => {
  const { productAttributeList, productAttributeValueList } = data

  for (const item of productAttributeList) {
    if (item.type === 0) {
      // 规格属性
      specList.value.push({
        id: item.id,
        name: item.name,
        type: item.type,
        handAddStatus: item.handAddStatus,
        inputList: item.inputList,
        productAttributeCategoryId: item.productAttributeCategoryId,
        searchType: item.searchType,
        selectType: item.selectType,
        relatedStatus: item.relatedStatus,
        sort: item.sort,
      })

      if (item.handAddStatus === 1) {
        // 支持手动新增
        const filterValueList = productAttributeValueList.filter(
          (v: PmsProductAttributeValue) => v.productAttributeId === item.id,
        )
        const inputList = filterValueList[0]?.value?.split(',') || []
        for (const val of inputList) {
          specChildList.value.push({ pid: item.id, pname: item.name, name: val })
        }
      } else if (item.handAddStatus === 0) {
        // 不支持手动新增
        const inputList = item.inputList?.split(',') || []
        for (const val of inputList) {
          specChildList.value.push({ pid: item.id, pname: item.name, name: val })
        }
      }
    }
  }

  // 根据SKU筛选可用规格
  const availAbleSpecSet = new Set<string>()
  for (const sku of skuStockList.value) {
    try {
      const spDataArr = JSON.parse(sku.spData)
      for (const sp of spDataArr) {
        availAbleSpecSet.add(sp.value)
      }
    } catch (e) {
      console.error('解析spData失败:', e, sku.spData)
    }
  }

  specChildList.value = specChildList.value.filter((item) => availAbleSpecSet.has(item.name))

  // 规格默认选中第一条
  specList.value.forEach((item) => {
    for (const cItem of specChildList.value) {
      if (cItem.pid === item.id) {
        cItem.selected = true
        specSelected.value.push(cItem)
        changeSpecInfo()
        break
      }
    }
  })
}

// 初始化商品参数
const initAttrList = (data: any) => {
  const { productAttributeList, productAttributeValueList } = data

  for (const item of productAttributeList) {
    if (item.type === 1) {
      // 参数属性
      const filterValueList = productAttributeValueList.filter(
        (v: PmsProductAttributeValue) => v.productAttributeId === item.id,
      )
      const value = filterValueList[0]?.value || ''
      attrList.value.push({ key: item.name, value })
    }
  }
}

// 初始化促销活动信息
const initPromotionTipList = (data: any) => {
  const promotionType = product.value.promotionType

  if (promotionType === 0) {
    promotionTipList.value.push('暂无优惠')
  } else if (promotionType === 1) {
    promotionTipList.value.push('单品优惠')
  } else if (promotionType === 2) {
    promotionTipList.value.push('会员优惠')
  } else if (promotionType === 3) {
    promotionTipList.value.push('多买优惠')
    for (const item of data.productLadderList || []) {
      promotionTipList.value.push(`满${item.count}件打${item.discount * 10}折`)
    }
  } else if (promotionType === 4) {
    promotionTipList.value.push('满减优惠')
    for (const item of data.productFullReductionList || []) {
      promotionTipList.value.push(`满${item.fullPrice}元减${item.reducePrice}元`)
    }
  } else if (promotionType === 5) {
    promotionTipList.value.push('限时优惠')
  }
}

// 初始化商品详情HTML
const initProductDesc = () => {
  let rawhtml = product.value.detailMobileHtml || ''

  // #ifdef H5
  const tempNode = document.createElement('div')
  tempNode.innerHTML = rawhtml
  const imgs = tempNode.getElementsByTagName('img')
  for (let i = 0; i < imgs.length; i++) {
    ;(imgs[i] as HTMLElement).style.width = '100%'
    ;(imgs[i] as HTMLElement).style.height = 'auto'
    ;(imgs[i] as HTMLElement).style.display = 'block'
  }
  desc.value = tempNode.innerHTML
  // #endif

  // #ifndef H5
  desc.value = rawhtml
  // #endif
}

// ===== 业务方法 =====

// 保存浏览记录
const saveReadHistory = () => {
  if (memberStore.hasLogin) {
    createReadHistoryAPI({
      productId: product.value.id,
      productName: product.value.name,
      productPic: product.value.pic,
      productPrice: String(product.value.price),
      productSubTitle: product.value.subTitle,
    })
  }
}

// 初始化收藏状态
const initProductCollection = async () => {
  if (memberStore.hasLogin) {
    try {
      const res = await getProductCollectionDetailAPI({ productId: product.value.id })
      favorite.value = res.data != null
    } catch (error) {
      console.error('查询收藏状态失败:', error)
    }
  }
}

// 规格改变时更新商品信息
const changeSpecInfo = () => {
  const skuStock = getSkuStock()
  if (skuStock) {
    // SKU 价格为空时回退到商品价，避免历史脏数据显示空价
    if (skuStock.price != null) {
      product.value.originalPrice = skuStock.price
    }
    if (product.value.promotionType === 1) {
      // 单品优惠使用促销价，SKU没有促销价时使用商品本身的促销价
      const skuPrice = skuStock.promotionPrice ?? skuStock.price
      if (skuPrice != null) {
        product.value.price = skuPrice
      }
    } else if (skuStock.price != null) {
      product.value.price = skuStock.price
    }
    if (skuStock.stock != null) {
      product.value.stock = skuStock.stock
    }
  }
}

// 获取当前选中的SKU
const getSkuStock = (): PmsSkuStock | null => {
  for (const sku of skuStockList.value) {
    const spDataArr = JSON.parse(sku.spData)
    const availAbleSpecSet = new Map<string, string>()
    for (const sp of spDataArr) {
      availAbleSpecSet.set(sp.key, sp.value)
    }

    let correctCount = 0
    for (const item of specSelected.value) {
      const value = availAbleSpecSet.get(item.pname)
      if (value != null && value === item.name) {
        correctCount++
      }
    }

    if (correctCount === specSelected.value.length) {
      return sku
    }
  }
  return null
}

// ===== 事件处理方法 =====

// 返回上一页
const handleGoBack = () => {
  if(closeDetailOverlay()) return
  if(getCurrentPages().length>1) uni.navigateBack({delta:1,fail:goFallbackPage})
  else goFallbackPage()
}

// 切换规格弹窗
const handleToggleSpec = () => {
  if (specClass.value === 'show') {
    specClass.value = 'hide'
    setTimeout(() => {
      specClass.value = 'none'
    }, 250)
  } else if (specClass.value === 'none') {
    specClass.value = 'show'
  }
}

// 切换属性弹窗
const handleToggleAttr = () => {
  if (attrClass.value === 'show') {
    attrClass.value = 'hide'
    setTimeout(() => {
      attrClass.value = 'none'
    }, 250)
  } else if (attrClass.value === 'none') {
    attrClass.value = 'show'
  }
}

// 切换优惠券弹窗
const handleToggleCoupon = async (type?: string) => {
  try {
    const res = await getProductCouponListAPI(String(product.value.id))
    couponList.value = res.data || []

    if (!couponList.value || couponList.value.length === 0) {
      uni.showToast({
        title: '暂无可领优惠券',
        icon: 'none',
      })
      return
    }

    const timer = type === 'show' ? 10 : 300
    const state = type === 'show' ? 1 : 0
    couponState.value = 2
    setTimeout(() => {
      couponState.value = state
    }, timer)
  } catch (error) {
    console.error('获取优惠券列表失败:', error)
  }
}

// 选择规格
const handleSelectSpec = (index: number, pid: number) => {
  const list = specChildList.value
  list.forEach((item) => {
    if (item.pid === pid) {
      item.selected = false
    }
  })

  list[index].selected = true

  // 存储已选择
  specSelected.value = []
  list.forEach((item) => {
    if (item.selected) {
      specSelected.value.push(item)
    }
  })

  changeSpecInfo()
}

// 领取优惠券
const handleAddCoupon = async (coupon: SmsCoupon) => {
  handleToggleCoupon()
  try {
    await addMemberCouponAPI(String(coupon.id))
    uni.showToast({
      title: '领取优惠券成功！',
      duration: 2000,
    })
  } catch (error) {
    console.error('领取优惠券失败:', error)
  }
}

// 分享
const handleShare = () => {
  // #ifdef H5
  uni.setClipboardData({ data: window.location.href })
  // #endif
  // #ifndef H5
  uni.showToast({ title: '请在网页端复制商品链接', icon: 'none' })
  // #endif
}

// 收藏/取消收藏
const handleToggleFavorite = async () => {
  if (!memberStore.hasLogin) {
    handleCheckLogin()
    return
  }

  try {
    if (favorite.value) {
      // 取消收藏
      await deleteProductCollectionAPI({ productId: product.value.id })
      uni.showToast({
        title: '取消收藏成功！',
        icon: 'none',
      })
    } else {
      // 收藏
      await createProductCollectionAPI({
        productId: product.value.id,
        productName: product.value.name,
        productPic: product.value.pic,
        productPrice: String(product.value.price),
        productSubTitle: product.value.subTitle,
      })
      uni.showToast({
        title: '收藏成功！',
        icon: 'none',
      })
    }
    favorite.value = !favorite.value
  } catch (error) {
    console.error('收藏操作失败:', error)
  }
}

// 立即购买：加购后直接进入确认订单页
const handleBuy = async () => {
  if (membershipOffer.value) {
    uni.navigateTo({ url: '/pages/member/' + (membershipOffer.value === 'COFOUNDER' ? 'cofounder' : 'partner') }); return
  }
  if (!memberStore.hasLogin) {
    handleCheckLogin()
    return
  }
  const skuStock = getSkuStock()
  if (!skuStock) {
    uni.showToast({ icon: 'none', title: '请先选择规格' })
    return
  }
  try {
    uni.showLoading({ title: '处理中', mask: true })
    await addCartAPI({ productId: product.value.id, productSkuId: skuStock.id, quantity: 1, zoneCode: zoneCode.value })
    const res = await getCartListAPI()
    const target = (res.data || []).find(
      (item) => Number(item.productId) === Number(product.value.id)
        && Number(item.productSkuId) === Number(skuStock.id)
        && (!zoneCode.value || String(item.zoneCode || '') === zoneCode.value),
    )
    uni.hideLoading()
    if (!target) {
      uni.showToast({ icon: 'none', title: '下单信息获取失败，请前往购物车结算' })
      return
    }
    uni.navigateTo({ url: '/pages/order/createOrder?cartIds=[' + target.id + ']' })
  } catch (error) {
    uni.hideLoading()
    console.error('立即购买失败:', error)
  }
}

// 商品评价：加载已核验评价
const loadComments = async (productId: number) => {
  commentLoading.value = true
  try {
    const res = await getProductCommentListAPI(productId, 1, 20)
    commentList.value = res.data?.list || []
    commentTotal.value = res.data?.total ?? 0
    commentAvgStar.value = res.data?.avgStar ?? null
  } catch (error) {
    console.error('加载商品评价失败:', error)
  } finally {
    commentLoading.value = false
  }
}

const formatStars = (star: number) => {
  const n = Math.max(0, Math.min(5, Number(star) || 0))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}

// 打开写评价弹窗
const handleWriteComment = async () => {
  if (!memberStore.hasLogin) {
    handleCheckLogin()
    return
  }
  commentPopup.value = 'show'
  pendingLoading.value = true
  try {
    const res = await getProductCommentPendingAPI()
    const all = res.data || []
    const current = Number(product.value.id)
    const mine = all.filter((p: any) => Number(p.productId) === current)
    pendingList.value = [...mine, ...all.filter((p: any) => Number(p.productId) !== current)]
    const pre = pendingList.value.find((p: any) => Number(p.productId) === current) || pendingList.value[0]
    commentForm.value = {
      orderId: pre ? Number(pre.orderId) : null,
      productId: pre ? Number(pre.productId) : null,
      star: 5,
      content: '',
    }
  } catch (error) {
    console.error('加载可评价订单失败:', error)
  } finally {
    pendingLoading.value = false
  }
}

const closeCommentPopup = () => {
  commentPopup.value = 'hide'
  setTimeout(() => {
    commentPopup.value = 'none'
  }, 250)
}

const selectPending = (p: any) => {
  commentForm.value.orderId = Number(p.orderId)
  commentForm.value.productId = Number(p.productId)
}

const submitComment = async () => {
  if (!commentForm.value.orderId || !commentForm.value.productId) {
    uni.showToast({ icon: 'none', title: '请选择要评价的订单商品' })
    return
  }
  if (!commentForm.value.content.trim()) {
    uni.showToast({ icon: 'none', title: '请填写评价内容' })
    return
  }
  commentSubmitting.value = true
  try {
    await createProductCommentAPI({
      productId: commentForm.value.productId,
      orderId: commentForm.value.orderId,
      star: commentForm.value.star,
      content: commentForm.value.content.trim(),
    })
    uni.showToast({ icon: 'success', title: '评价成功' })
    closeCommentPopup()
    loadComments(Number(product.value.id))
  } catch (error) {
    console.error('提交评价失败:', error)
  } finally {
    commentSubmitting.value = false
  }
}

// 加入购物车
const handleAddToCart = async () => {
  if (membershipOffer.value) { uni.showToast({ title: '旅游权益请点立即购买，查看双积分与会员权益', icon: 'none' }); return }
  if (!memberStore.hasLogin) {
    handleCheckLogin()
    return
  }

  const skuStock = getSkuStock()
  if (!skuStock) {
    uni.showToast({
      title: '请选择规格',
      icon: 'none',
    })
    return
  }

  try {
    const res = await addCartAPI({
      price: product.value.price,
      productAttr: skuStock.spData,
      productBrand: product.value.brandName,
      productCategoryId: product.value.productCategoryId,
      productId: product.value.id,
      productName: product.value.name,
      productPic: product.value.pic,
      productSkuCode: skuStock.skuCode,
      productSkuId: skuStock.id,
      productSn: product.value.productSn,
      productSubTitle: product.value.subTitle,
      quantity: 1,
      zoneCode: zoneCode.value,
    })
    uni.showToast({
      title: res.message,
      duration: 1500,
    })
  } catch (error) {
    console.error('加入购物车失败:', error)
  }
}

// 跳转到品牌详情页
const handleNavToBrandDetail = () => {
  const id = brand.value.id
  uni.navigateTo({
    url: `/pages/brand/brandDetail?id=${id}`,
  })
}

// 检查登录状态
const handleCheckLogin = () => {
  uni.showModal({
    title: '提示',
    content: '你还没登录，是否要登录？',
    confirmText: '去登录',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) {
        uni.navigateTo({
          url: '/pages/public/login',
        })
      }
    },
  })
}
</script>

<style lang="scss">
page {
  background: $page-color-base;
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}
</style>

<style lang="scss" scoped>
.demo-product-note { padding: 10px 14px; background: #fff3d8; color: #805b1d; font-size: 12px; line-height: 1.5; }
/* 换肤主题变量：theme-pink 为默认（与现有粉色一致），theme-green 为清新绿；子元素通过 var(--theme-*) 引用 */
.theme-pink { --theme-primary: #fa436a; --theme-primary-dark: #e43a55; --theme-primary-soft: #fbebee; --theme-action-gradient: linear-gradient(to right, #ffac30, #fa436a, #f56c6c); --theme-action-shadow: 1px 2px 5px rgba(219, 63, 96, 0.4); }
.theme-green { --theme-primary: #285b40; --theme-primary-dark: #1d4230; --theme-primary-soft: #e8f0eb; --theme-action-gradient: linear-gradient(to right, #3d7a5c, #285b40, #1d4230); --theme-action-shadow: 1px 2px 5px rgba(40, 91, 64, 0.4); }
/* 自定义导航栏 */
.custom-navbar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 999;

  .navbar-content {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 44px;
    position: relative;
  }

  .nav-back {
    position: absolute;
    left: 20rpx;
    width: 64rpx;
    height: 64rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.3);
    border-radius: 50%;
    z-index: 10;

    .back-arrow {
      width: 20rpx;
      height: 20rpx;
      border-left: 4rpx solid currentColor;
      border-bottom: 4rpx solid currentColor;
      transform: rotate(45deg);
      margin-left: 6rpx;
    }
  }

  .nav-title {
    font-size: 34rpx;
    font-weight: bold;
    color: #333;
  }
}

.icon-you {
  font-size: $font-base + 2rpx;
  color: #888;
}

/* 轮播图 */
.carousel {
  height: 722rpx;
  position: relative;

  swiper {
    height: 100%;
  }

  .image-wrapper {
    position: relative;
    width: 100%;
    height: 100%;
  }

  .swiper-item {
    display: flex;
    justify-content: center;
    align-content: center;
    height: 750rpx;
    overflow: hidden;

    image {
      width: 100%;
      height: 100%;
    }
  }
}

/* 标题简介 */
.introduce-section {
  background: #fff;
  padding: 20rpx 30rpx;

  .title {
    font-size: 32rpx;
    color: $font-color-dark;
    height: 50rpx;
    line-height: 50rpx;
  }

  .title2 {
    font-size: 28rpx;
    color: $font-color-light;
    height: 46rpx;
    line-height: 46rpx;
  }

  .price-box {
    display: flex;
    align-items: baseline;
    height: 64rpx;
    padding: 10rpx 0;
    font-size: 26rpx;
    color: $uni-color-primary;
  }

  .price {
    font-size: $font-lg + 2rpx;
  }

  .m-price {
    margin: 0 12rpx;
    color: $font-color-light;
    text-decoration: line-through;
  }

  .bot-row {
    display: flex;
    align-items: center;
    height: 50rpx;
    font-size: $font-sm;
    color: $font-color-light;

    text {
      flex: 1;
    }
  }
}

/* 分享 */
.share-section {
  display: flex;
  align-items: center;
  color: $font-color-base;
  background: linear-gradient(to right, #fdf5f6, #fbebf6);
  padding: 12rpx 30rpx;

  .share-icon {
    display: flex;
    align-items: center;
    width: 70rpx;
    height: 30rpx;
    line-height: 1;
    border: 1rpx solid $uni-color-primary;
    border-radius: 4rpx;
    position: relative;
    overflow: hidden;
    font-size: 22rpx;
    color: $uni-color-primary;

    &:after {
      content: '';
      width: 50rpx;
      height: 50rpx;
      border-radius: 50%;
      left: -20rpx;
      top: -12rpx;
      position: absolute;
      background: $uni-color-primary;
    }
  }

  .icon-xingxing {
    position: relative;
    z-index: 1;
    font-size: 24rpx;
    margin-left: 2rpx;
    margin-right: 10rpx;
    color: #fff;
    line-height: 1;
  }

  .tit {
    font-size: $font-base;
    margin-left: 10rpx;
  }

  .icon-bangzhu1 {
    padding: 10rpx;
    font-size: 30rpx;
    line-height: 1;
  }

  .share-btn {
    flex: 1;
    text-align: right;
    font-size: $font-sm;
    color: $uni-color-primary;
  }

  .icon-you {
    font-size: $font-sm;
    margin-left: 4rpx;
    color: $uni-color-primary;
  }
}

/* 商品规格列表 */
.c-list {
  font-size: $font-sm + 2rpx;
  color: $font-color-base;
  background: #fff;

  .c-row {
    display: flex;
    align-items: center;
    padding: 20rpx 30rpx;
    position: relative;
  }

  .tit {
    width: 140rpx;
  }

  .con {
    flex: 1;
    color: $font-color-dark;

    .selected-text {
      margin-right: 10rpx;
    }
  }

  .bz-list {
    height: 40rpx;
    font-size: $font-sm + 2rpx;
    color: $font-color-dark;

    text {
      display: inline-block;
      margin-right: 30rpx;
    }
  }

  .con-list {
    flex: 1;
    display: flex;
    flex-direction: column;
    color: $font-color-dark;
    line-height: 40rpx;
  }

  .red {
    color: $uni-color-primary;
  }
}

/* 评价 */
.eva-section {
  display: flex;
  flex-direction: column;
  padding: 20rpx 30rpx;
  background: #fff;
  margin-top: 16rpx;

  .e-header {
    display: flex;
    align-items: center;
    height: 70rpx;
    font-size: $font-sm + 2rpx;
    color: $font-color-light;

    .tit {
      font-size: $font-base + 2rpx;
      color: $font-color-dark;
      margin-right: 4rpx;
    }

    .tip {
      flex: 1;
      text-align: right;
    }

    .icon-you {
      margin-left: 10rpx;
    }
  }
}

.eva-box {
  display: flex;
  padding: 20rpx 0;

  .portrait {
    flex-shrink: 0;
    width: 80rpx;
    height: 80rpx;
    border-radius: 100px;
  }

  .right {
    flex: 1;
    display: flex;
    flex-direction: column;
    font-size: $font-base;
    color: $font-color-base;
    padding-left: 26rpx;

    .con {
      font-size: $font-base;
      color: $font-color-dark;
      padding: 20rpx 0;
    }

    .bot {
      display: flex;
      justify-content: space-between;
      font-size: $font-sm;
      color: $font-color-light;
    }
  }
}

/* 品牌信息 */
.brand-info {
  margin-top: 16rpx;
  background-color: #fff;
  display: flex;
  flex-direction: column;

  .brand-box {
    display: flex;
    align-items: center;
    padding: 30rpx 50rpx;

    .image-wrapper {
      width: 210rpx;
      height: 70rpx;

      image {
        width: 100%;
        height: 100%;
      }
    }

    .title {
      flex: 1;
      display: flex;
      flex-direction: column;
      font-size: $font-lg + 4rpx;
      margin-left: 30rpx;
      color: $font-color-dark;

      text:last-child {
        font-size: $font-sm;
        color: $font-color-light;
        margin-top: 8rpx;
      }
    }
  }

  .d-header {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 80rpx;
    font-size: $font-base + 2rpx;
    color: $font-color-dark;
    position: relative;

    text {
      padding: 0 20rpx;
      background: #fff;
      position: relative;
      z-index: 1;
    }

    &:after {
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translateX(-50%);
      width: 300rpx;
      height: 0;
      content: '';
      border-bottom: 1px solid #ccc;
    }
  }
}

/* 商家信息（与品牌信息同样式） */
.merchant-info {
  margin-top: 16rpx;
  background-color: #fff;
  display: flex;
  flex-direction: column;

  .merchant-box {
    display: flex;
    align-items: center;
    padding: 30rpx 50rpx;

    .title {
      flex: 1;
      display: flex;
      flex-direction: column;
      font-size: $font-lg + 4rpx;
      color: $font-color-dark;

      text:last-child {
        font-size: $font-sm;
        color: $font-color-light;
        margin-top: 8rpx;
      }
    }
  }

  .d-header {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 80rpx;
    font-size: $font-base + 2rpx;
    color: $font-color-dark;
    position: relative;

    text {
      padding: 0 20rpx;
      background: #fff;
      position: relative;
      z-index: 1;
    }

    &:after {
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translateX(-50%);
      width: 300rpx;
      height: 0;
      content: '';
      border-bottom: 1px solid #ccc;
    }
  }
}

/* 图文详情 */
.detail-desc {
  background: #fff;
  margin-top: 16rpx;

  .d-header {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 80rpx;
    font-size: $font-base + 2rpx;
    color: $font-color-dark;
    position: relative;

    text {
      padding: 0 20rpx;
      background: #fff;
      position: relative;
      z-index: 1;
    }

    &:after {
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translateX(-50%);
      width: 300rpx;
      height: 0;
      content: '';
      border-bottom: 1px solid #ccc;
    }
  }
}

.detail-desc:deep(img) {
  width: 100%;
  height: auto;
}

/* 弹出层 */
.popup {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 99;

  &.show {
    display: block;

    .mask {
      animation: showPopup 0.2s linear both;
    }

    .layer {
      animation: showLayer 0.2s linear both;
    }
  }

  &.hide {
    .mask {
      animation: hidePopup 0.2s linear both;
    }

    .layer {
      animation: hideLayer 0.2s linear both;
    }
  }

  &.none {
    display: none;
  }

  .mask {
    position: fixed;
    top: 0;
    width: 100%;
    height: 100%;
    z-index: 1;
    background-color: rgba(0, 0, 0, 0.4);
  }

  .layer {
    position: fixed;
    z-index: 99;
    bottom: 0;
    width: 100%;
    min-height: 40vh;
    border-radius: 10rpx 10rpx 0 0;
    background-color: #fff;

    .btn {
      height: 66rpx;
      line-height: 66rpx;
      border-radius: 100rpx;
      background: $uni-color-primary;
      font-size: $font-base + 2rpx;
      color: #fff;
      margin: 30rpx auto 20rpx;
    }
  }

  .no-padding {
    padding: 0rpx 0rpx;
  }
}

/* 规格选择弹窗内容 - 与源项目一致，放在顶层 */
.attr-content {
  padding: 10rpx 30rpx;

  .a-t {
    display: flex;

    image {
      width: 170rpx;
      height: 170rpx;
      flex-shrink: 0;
      margin-top: -40rpx;
      border-radius: 8rpx;
    }

    .right {
      display: flex;
      flex-direction: column;
      padding-left: 24rpx;
      font-size: $font-sm + 2rpx;
      color: $font-color-base;
      line-height: 42rpx;

      .price {
        font-size: $font-lg;
        color: $uni-color-primary;
        margin-bottom: 10rpx;
      }

      .selected-text {
        margin-right: 10rpx;
      }
    }
  }

  .attr-list {
    display: flex;
    flex-direction: column;
    font-size: $font-base + 2rpx;
    color: $font-color-base;
    padding-top: 30rpx;
    padding-left: 10rpx;
  }

  .item-list {
    padding: 20rpx 0 0;
    display: flex;
    flex-wrap: wrap;
  }
}

@keyframes showPopup {
  0% {
    opacity: 0;
  }

  100% {
    opacity: 1;
  }
}

@keyframes hidePopup {
  0% {
    opacity: 1;
  }

  100% {
    opacity: 0;
  }
}

@keyframes showLayer {
  0% {
    transform: translateY(120%);
  }

  100% {
    transform: translateY(0%);
  }
}

@keyframes hideLayer {
  0% {
    transform: translateY(0);
  }

  100% {
    transform: translateY(120%);
  }
}

/* 底部操作菜单 */
.page-bottom {
  position: fixed;
  left: 30rpx;
  bottom: calc(30rpx + env(safe-area-inset-bottom));
  z-index: 95;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 690rpx;
  height: 100rpx;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 20rpx 0 rgba(0, 0, 0, 0.5);
  border-radius: 16rpx;

  .p-b-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    font-size: $font-sm;
    color: $font-color-base;
    width: 96rpx;
    height: 80rpx;

    // navigator 组件渲染为 <a class="navigator-wrap">，需同步 flex 布局
    :deep(.navigator-wrap) {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
    }

    .yticon {
      font-size: 40rpx;
      line-height: 48rpx;
      color: $font-color-light;
    }

    &.active,
    &.active .yticon {
      color: $uni-color-primary;
    }

    .icon-fenxiang2 {
      font-size: 42rpx;
      transform: translateY(-2rpx);
    }

    .icon-shoucang {
      font-size: 46rpx;
    }
  }

  .action-btn-group {
    display: flex;
    height: 76rpx;
    border-radius: 100px;
    overflow: hidden;
    box-shadow: var(--theme-action-shadow, 1px 2px 5px rgba(219, 63, 96, 0.4));
    background: var(--theme-action-gradient, linear-gradient(to right, #ffac30, #fa436a, #f56c6c));
    margin-left: 20rpx;
    position: relative;

    &:after {
      content: '';
      position: absolute;
      top: 50%;
      right: 50%;
      transform: translateY(-50%);
      height: 28rpx;
      width: 0;
      border-right: 1px solid rgba(255, 255, 255, 0.5);
    }

    .action-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 180rpx;
      height: 100%;
      font-size: $font-base;
      padding: 0;
      border-radius: 0;
      background: transparent;
    }
  }
}

/* 优惠券面板 */
.mask {
  display: flex;
  align-items: flex-end;
  position: fixed;
  left: 0;
  top: var(--window-top);
  bottom: 0;
  width: 100%;
  background: rgba(0, 0, 0, 0);
  z-index: 9995;
  transition: 0.3s;

  .mask-content {
    width: 100%;
    min-height: 30vh;
    max-height: 70vh;
    background: #f3f3f3;
    transform: translateY(100%);
    transition: 0.3s;
    overflow-y: scroll;
  }

  &.none {
    display: none;
  }

  &.show {
    background: rgba(0, 0, 0, 0.4);

    .mask-content {
      transform: translateY(0);
    }
  }
}

/* 优惠券列表 */
.coupon-item {
  display: flex;
  flex-direction: column;
  margin: 20rpx 24rpx;
  background: #fff;

  .con {
    display: flex;
    align-items: center;
    position: relative;
    height: 120rpx;
    padding: 0 30rpx;

    &:after {
      position: absolute;
      left: 0;
      bottom: 0;
      content: '';
      width: 100%;
      height: 0;
      border-bottom: 1px dashed #f3f3f3;
      transform: scaleY(50%);
    }
  }

  .left {
    display: flex;
    flex-direction: column;
    justify-content: center;
    flex: 1;
    overflow: hidden;
    height: 100rpx;
  }

  .title {
    font-size: 32rpx;
    color: $font-color-dark;
    margin-bottom: 10rpx;
  }

  .time {
    font-size: 24rpx;
    color: $font-color-light;
  }

  .right {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    font-size: 26rpx;
    color: $font-color-base;
    height: 100rpx;
  }

  .price {
    font-size: 44rpx;
    color: $base-color;

    &:before {
      content: '￥';
      font-size: 34rpx;
    }
  }

  .tips {
    font-size: 24rpx;
    color: $font-color-light;
    line-height: 60rpx;
    padding-left: 30rpx;
  }

  .circle {
    position: absolute;
    left: -6rpx;
    bottom: -10rpx;
    z-index: 10;
    width: 20rpx;
    height: 20rpx;
    background: #f3f3f3;
    border-radius: 100px;

    &.r {
      left: auto;
      right: -6rpx;
    }
  }
}
.eva-empty { padding: 24rpx 30rpx; color: #777; font-size: 26rpx; background: #fff; }

.shop-row { display: flex; align-items: center; margin-top: 10rpx; font-size: 26rpx; }
.shop-row .shop-label { color: #999; margin-right: 12rpx; }
.shop-row .shop-name { color: #333; font-weight: 600; }
.eva-section .e-header .e-count { flex: 1; text-align: right; font-size: 24rpx; color: #999; }
.eva-section .e-header .e-more { margin-left: 16rpx; font-size: 26rpx; color: #fa436a; }
.eva-list { padding: 6rpx 0; }
.eva-item { padding: 20rpx 0; border-bottom: 1rpx solid #f2f2f2; }
.eva-item:last-child { border-bottom: none; }
.eva-item-head { display: flex; align-items: center; }
.eva-avatar { width: 56rpx; height: 56rpx; border-radius: 50%; margin-right: 16rpx; background: #f3f3f3; }
.eva-item-meta { flex: 1; display: flex; flex-direction: column; }
.eva-nick { font-size: 26rpx; color: #333; }
.eva-stars { font-size: 24rpx; color: #ffb400; letter-spacing: 2rpx; }
.eva-time { font-size: 22rpx; color: #aaa; }
.eva-content { display: block; margin-top: 12rpx; font-size: 26rpx; color: #333; line-height: 1.6; }
.eva-attr { display: block; margin-top: 6rpx; font-size: 22rpx; color: #999; }
.eva-replay-box { margin-top: 12rpx; padding: 12rpx 16rpx; background: #f7f7f7; border-radius: 8rpx; }
.eva-replay { font-size: 24rpx; color: #666; }
.comment-layer { padding: 24rpx 30rpx 40rpx; max-height: 82vh; overflow-y: auto; box-sizing: border-box; }
.comment-title { font-size: 32rpx; font-weight: 600; color: #333; margin-bottom: 16rpx; }
.pending-list { max-height: 320rpx; overflow-y: auto; }
.pending-item { display: flex; align-items: center; padding: 14rpx; border: 1rpx solid #eee; border-radius: 10rpx; margin-bottom: 12rpx; }
.pending-item.active { border-color: #fa436a; background: #fff5f7; }
.pending-pic { width: 72rpx; height: 72rpx; border-radius: 8rpx; margin-right: 16rpx; background: #f3f3f3; }
.pending-info { flex: 1; display: flex; flex-direction: column; }
.pending-name { font-size: 26rpx; color: #333; }
.pending-attr { font-size: 22rpx; color: #999; }
.star-row { display: flex; align-items: center; margin: 20rpx 0 10rpx; }
.star-row .star { font-size: 46rpx; color: #ddd; margin-right: 14rpx; }
.star-row .star.active { color: #ffb400; }
.comment-input { width: 100%; box-sizing: border-box; min-height: 160rpx; padding: 16rpx; background: #f7f7f7; border-radius: 10rpx; font-size: 26rpx; }
@media (min-width: 900px) {
  .container {
    display: grid;
    grid-template-columns: minmax(0, 530px) minmax(0, 1fr);
    column-gap: 24px;
    align-items: start;
  }
  .demo-product-note { grid-column: 1 / -1; }
  .carousel { grid-column: 1; grid-row: 2 / span 4; width: 100%; height: 530px; background: #fff; }
  .carousel .swiper-item { height: 530px; }
  .introduce-section { grid-column: 2; grid-row: 2; padding: 24px; }
  .share-section { grid-column: 2; grid-row: 3; margin: 0; }
  .c-list { grid-column: 2; grid-row: 4; margin: 0; background: #fff; }
  .page-bottom { grid-column: 2; grid-row: 5; position: static; width: 100%; margin: 16px 0 0; box-shadow: none; border: 1px solid #e1e9df; }
  .eva-section { grid-column: 1 / -1; grid-row: 6; margin-top: 24px; }
  .brand-info { grid-column: 1 / -1; grid-row: 7; }
  .merchant-info { grid-column: 1 / -1; grid-row: 8; }
  .detail-desc { grid-column: 1 / -1; grid-row: 9; }
}
</style>

<style lang="scss">
/* 非 scoped 样式 - 规格选项按钮样式 */
/* 在 uni-app H5 端，scoped 样式无法作用于 text 原生组件 */
.attr-content .item-list text {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eee;
  margin-right: 20rpx;
  margin-bottom: 20rpx;
  border-radius: 100rpx;
  min-width: 60rpx;
  height: 60rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #333;
}

.attr-content .item-list text.selected {
  /* 换肤：跟随根容器 .theme-* 定义的变量，未命中时回落粉色 */
  background: var(--theme-primary-soft, #fbebee);
  color: var(--theme-primary-dark, #e43a55);
}
</style>

<style scoped>
.detail-tabs{display:flex;gap:28px;margin:0 auto;color:#285a62;font-size:15px;font-weight:600}.detail-tabs text{cursor:pointer}.custom-navbar{background:#fffffff2!important;border-bottom:1px solid #edf2f2}.container{overscroll-behavior-x:contain}.nav-back .back-arrow{border-color:#285a62!important}
</style>



<style scoped>
.container{background:#f2f6f5;padding-top:112px;padding-bottom:110px;--theme-primary:#247a80;--theme-primary-dark:#1c6268;--theme-primary-soft:#e6f4f2;--theme-action-gradient:linear-gradient(100deg,#45a49b,#237881)}
.custom-navbar{padding:6px 12px 0;box-sizing:border-box;background:#fff!important;box-shadow:0 2px 14px #173f4410}
.custom-navbar .navbar-content{height:46px;gap:12px;justify-content:flex-start}
.custom-navbar .nav-back{position:static;width:24px;min-width:24px;height:34px;background:none;border-radius:0}
.detail-search{display:flex;align-items:center;gap:8px;flex:1;min-width:0;height:36px;border:1px solid #83b8b8;border-radius:10px;padding:0 8px;background:#f8fcfb;color:#247a80}
.search-placeholder{flex:1;font-size:12px;white-space:nowrap;overflow:hidden;color:#7b9293}.search-action{font-size:12px;padding:3px 6px;background:#e6f4f2;border-radius:5px}
.header-action{width:24px;min-width:24px;text-align:center;color:#234c54;font-size:24px}.header-action .yticon{font-size:23px}
.detail-tabs{height:44px;margin:0;justify-content:space-around;align-items:center;gap:0;color:#57777b}.detail-tabs text:first-child{color:#1c6268}
.demo-product-note{font-size:10px;background:#edf5f3;color:#698b85;border-radius:8px;margin:0 10px 10px;padding:6px 10px}
.carousel{margin:0 10px;border-radius:16px;overflow:hidden;background:white}.introduce-section{margin:10px;border-radius:16px;padding:20px;background:#fff}.introduce-section .title{font-size:18px;line-height:1.5;font-weight:600}.introduce-section .title2{font-size:12px;color:#879393;line-height:1.7}.introduce-section .price{color:#237881;font-size:30px}.introduce-section .bot-row{margin-top:14px;font-size:12px}
.share-section{margin:0 10px;background:#fff;padding:16px;border-bottom:1px solid #f2f5f4}.share-section .tit{font-size:14px}.share-section .share-btn{font-size:12px;color:#247a80}
.c-list,.eva-section,.brand-info,.detail-desc,.recommend-section{margin:10px;border-radius:16px;overflow:hidden;background:#fff}.c-list .c-row{padding:17px 15px;min-height:50px}.c-list .tit{font-size:13px;color:#829593}.c-list .con{font-size:13px}.eva-section .e-header{padding:18px}.eva-empty{color:#93a2a1;text-align:center;padding:30px 16px}.brand-info .brand-box{padding:16px}.detail-desc .d-header{font-size:17px}.detail-desc{padding-bottom:14px}
.section-heading{display:flex;justify-content:space-between;padding:18px;font-size:17px;font-weight:600;color:#234c54}.section-heading text:last-child{font-size:11px;font-weight:400;color:#98a6a4}.recommend-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;padding:0 14px 18px}.recommend-card{min-width:0;cursor:pointer}.recommend-card image{width:100%;height:160px;border-radius:12px;background:#f4f7f6}.recommend-name{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;font-size:12px;line-height:1.6;margin:8px 0}.recommend-price{font-size:17px;color:#237881;font-weight:600}.recommend-empty{padding:28px;text-align:center;color:#8fa19e;font-size:12px}
.page-bottom{left:0;bottom:0;width:100%;height:auto;min-height:68px;box-sizing:border-box;padding:8px 12px calc(8px + env(safe-area-inset-bottom));border-radius:0;box-shadow:0 -2px 16px #173f4410;background:#fff;gap:6px}.page-bottom .p-b-btn{width:42px;min-width:42px;height:50px;font-size:10px}.page-bottom .p-b-btn .yticon{font-size:23px;line-height:28px}.page-bottom .line-icon{width:22px;height:22px;margin-bottom:4px;fill:none;stroke:#7f9899;stroke-width:1.6;stroke-linecap:round;stroke-linejoin:round}.page-bottom .p-b-btn.active .line-icon{stroke:#247a80}.page-bottom .action-btn-group{flex:1;margin:0 0 0 8px;height:42px;box-shadow:none;border-radius:12px;background:transparent}.page-bottom .action-btn-group .action-btn{flex:1;min-width:0;width:auto;font-size:13px}.page-bottom .add-cart-btn{background:#deeeea!important;color:#236b6b!important}.page-bottom .buy-now-btn{background:#247a80!important;color:#fff!important}.to-top{position:fixed;right:14px;bottom:94px;border:1px solid #e4efeb;border-radius:50%;width:45px;height:45px;background:#fffffff5;display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:90;color:#247a80}.to-top text{font-size:10px}
@media(min-width:900px){.container{padding-top:112px;max-width:1200px;margin:auto}.custom-navbar{max-width:1200px;left:50%;transform:translateX(-50%)}.detail-search{max-width:700px}.recommend-section{grid-column:1/-1;grid-row:10}.recommend-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.recommend-card image{height:230px}.page-bottom{position:static;width:100%;padding:12px;border-radius:12px}.carousel{margin:0}.share-section{margin:0}.introduce-section{margin:0}.detail-tabs{max-width:640px;margin:auto}.to-top{right:30px}}
</style>


<style>
@media(min-width:900px){body:has(.detail-tabs) .custom-navbar{display:block!important}body:has(.detail-tabs) .desktop-site-header{display:none!important}}
</style>
<style scoped>
.product-travel-note{margin:0 10px 10px;padding:14px 18px;border-radius:14px;background:linear-gradient(90deg,#173e49cc,#173e4933),url('/static/travel-photo/coastal-retreat.jpg') center 55%/cover;color:white;display:flex;flex-direction:column;gap:5px;font-size:13px;font-weight:600}.product-travel-note text:last-child{font-size:10px;opacity:.85;font-weight:400}
@media(min-width:900px){.product-travel-note{grid-column:1/-1;margin:0 0 14px;grid-row:2}.carousel{grid-row:3/span 4}.introduce-section{grid-row:3}.share-section{grid-row:4}.c-list{grid-row:5}.page-bottom{grid-row:6}.eva-section{grid-row:7}.brand-info{grid-row:8}.detail-desc{grid-row:9}.recommend-section{grid-row:10}}
</style>
