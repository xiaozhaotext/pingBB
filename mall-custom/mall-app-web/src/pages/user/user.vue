<template>
  <DesktopHeader />
  <view class="account-reference">
    <view class="account-top"><text>我的</text></view>
    <view class="account-profile" @click="!hasLogin && handleNavTo('/pages/public/login')"><image :src="memberInfo?.icon || '/static/missing-face.png'"/><view><view class="account-name">{{ memberInfo?.nickname || memberInfo?.username || '点击登录' }}<text>{{ identityLabel }}</text></view><text class="account-id">{{ hasLogin ? '会员 ID：' + memberInfo?.id : '登录后查看您的账户与订单' }}</text></view></view>
    <view class="account-panels">
      <view class="account-panel"><view class="account-section-title">数据资产<small>账户实际数据</small></view><view class="account-assets"><view><text>{{ productBalance }}</text><small>商品积分</small></view><view><text>{{ rebuyBalance }}</text><small>复购积分</small></view><view><text>{{ couponCount ?? '—' }}</text><small>优惠券</small></view></view></view>
      <view class="account-panel"><view class="account-section-title">数据看板<small>会员概览</small></view><view class="account-stat-grid"><view><text>{{ inviteCount }}</text><small>我的推荐人数</small></view><view><text>{{ identityLabel }}</text><small>会员身份</small></view><view><text>{{ memberInfo?.growth ?? '—' }}</text><small>成长值</small></view><view><text>未开放</text><small>团队业绩与奖励</small></view></view></view>
      <view class="account-panel"><view class="account-section-title">推荐进度<small>奖励规则未启用</small></view><view class="recommend-layout"><view class="recommend-stage" v-for="stage in ['邀请记录','关系确认','奖励规则']" :key="stage"><view class="stage-dot">{{ stage === '邀请记录' ? '1' : stage === '关系确认' ? '2' : '3' }}</view><text>{{ stage }}</text><small>{{ stage === '奖励规则' ? '待确认' : '查看记录' }}</small></view><view class="recommend-count"><text>{{ inviteCount }}</text><small>推荐人数</small></view></view><view class="account-section-footer"><text>展示推荐关系，不启用推广计酬</text><text @click="handleNavTo('/pages/member/invitation')">查看推荐 ›</text></view></view>
      <view class="account-panel"><view class="account-section-title">我的订单<small @click="handleNavTo('/pages/order/order?state=0')">查看全部 »</small></view><view class="account-services"><view v-for="item in orderEntries" :key="item.label" @click="handleNavTo(item.url)"><text class="yticon" :class="item.icon"></text><text>{{ item.label }}</text></view></view></view>
      <view class="account-panel"><view class="account-section-title">我的服务</view><view class="account-services service-grid"><view v-for="item in serviceEntries" :key="item.label" @click="handleNavTo(item.url)"><text class="yticon" :class="item.icon"></text><text>{{ item.label }}</text></view></view></view>
      <view v-if="accountError" class="account-error" @click="loadData">{{ accountError }}，点击重试</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import DesktopHeader from '@/components/DesktopHeader.vue'
import { ref, computed } from 'vue'
import { onShow, onNavigationBarButtonTap } from '@dcloudio/uni-app'
import { useMemberStore } from '@/stores/member'
import { getMemberCouponListAPI } from '@/apis/coupon'
import { getMyIdentityAPI, getPointsAccountAPI, getMyInviteesAPI } from '@/apis/member'
import mixListCell from '@/components/mix-list-cell.vue'

// ===== Store 相关 =====
// 获取会员store
const memberStore = useMemberStore()
// 是否登录
const hasLogin = computed(() => !!memberStore.memberInfo)
// 会员信息
const memberInfo = computed(() => memberStore.memberInfo)

// ===== 页面数据 =====
// 优惠券数量
const couponCount = ref<number | null>(null)
const identityLabel = ref('会员身份加载中')
const productBalance=ref<string | number>('—'),rebuyBalance=ref<string | number>('—'),inviteCount=ref<string | number>('—'),accountError=ref('')
const orderEntries=[{label:'全部订单',icon:'icon-shouye',url:'/pages/order/order?state=0'},{label:'待付款',icon:'icon-daifukuan',url:'/pages/order/order?state=1'},{label:'待收货',icon:'icon-yishouhuo',url:'/pages/order/order?state=2'},{label:'已完成',icon:'icon-shouhoutuikuan',url:'/pages/order/order?state=3'}]
const serviceEntries=[{label:'会员中心',icon:'icon-iLinkapp-',url:'/pages/member/center'},{label:'收货地址',icon:'icon-dizhi',url:'/pages/address/address'},{label:'我的推荐',icon:'icon-shoucang',url:'/pages/member/invitation'},{label:'我的足迹',icon:'icon-lishijilu',url:'/pages/user/readHistory'},{label:'我的收藏',icon:'icon-shoucang_xuanzhongzhuangtai',url:'/pages/user/productCollection'},{label:'品牌关注',icon:'icon-shoucang',url:'/pages/user/brandAttention'},{label:'设置',icon:'icon-shezhi1',url:'/pages/set/set'}]
// 遮罩层变换样式
const coverTransform = ref('translateY(0px)')
// 遮罩层过渡动画
const coverTransition = ref('0s')
// 是否正在拖动
const moving = ref(false)
// 触摸起始Y坐标
let startY = 0
// 触摸移动Y坐标
let moveY = 0
// 页面是否在顶部
let pageAtTop = true

// ===== 数据加载 =====
// 加载优惠券数据
const loadData = async () => {
  accountError.value=''; productBalance.value='—'; rebuyBalance.value='—'; inviteCount.value='—'
  if (hasLogin.value) {
    const results=await Promise.allSettled([getPointsAccountAPI(1),getPointsAccountAPI(2),getMyInviteesAPI()])
    if (!hasLogin.value) {
      identityLabel.value='游客'; couponCount.value=null
      return
    }
    results.forEach((result,index)=>{if(result.status==='fulfilled'){const data=result.value.data;if(index===0)productBalance.value=data?.balance??'未开户';if(index===1)rebuyBalance.value=data?.balance??'未开户';if(index===2)inviteCount.value=Array.isArray(data)?data.length:'—'}else{accountError.value='部分账户信息暂不可用'}})

    try {
      const identity = await getMyIdentityAPI()
      identityLabel.value = identity.data?.identityType == null
        ? '普通会员'
        : ({ 0: '普通会员', 1: '合伙人', 2: '联创' } as Record<number, string>)[identity.data.identityType] || '未知身份'
    } catch {
      identityLabel.value = '会员身份获取失败'
    }
    try {
      const res = await getMemberCouponListAPI(0)
      const list = res.data
      couponCount.value = Array.isArray(list) ? list.length : null
    } catch {
      couponCount.value = null
    }
  } else {
    identityLabel.value = '游客'
    couponCount.value = null
  }
}

// ===== 生命周期 =====
// 页面显示时加载数据
onShow(() => {
  loadData()
})

// 导航栏按钮点击事件
onNavigationBarButtonTap((e: { index: number }) => {
  const index = e.index
  if (index === 0) {
    handleNavTo('/pages/set/set')
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

// ===== 事件处理方法 =====
// 页面导航（未登录时跳转登录页）
const handleNavTo = (url: string) => {
  if (!hasLogin.value) {
    url = '/pages/public/login'
  }
  uni.navigateTo({
    url,
  })
}

// 跳转到通知页（小程序端导航栏右侧按钮）
const handleNavToNotice = () => {
  uni.navigateTo({
    url: '/pages/notice/notice',
  })
}

// 遮罩层触摸开始
const handleCoverTouchstart = (e: TouchEvent) => {
  if (!pageAtTop) {
    return
  }
  coverTransition.value = 'transform .1s linear'
  startY = e.touches[0].clientY
}

// 遮罩层触摸移动
const handleCoverTouchmove = (e: TouchEvent) => {
  moveY = e.touches[0].clientY
  let moveDistance = moveY - startY
  if (moveDistance < 0) {
    moving.value = false
    return
  }
  moving.value = true
  if (moveDistance >= 80 && moveDistance < 100) {
    moveDistance = 80
  }

  if (moveDistance > 0 && moveDistance <= 80) {
    coverTransform.value = `translateY(${moveDistance}px)`
  }
}

// 遮罩层触摸结束
const handleCoverTouchend = () => {
  if (moving.value === false) {
    return
  }
  moving.value = false
  coverTransition.value = 'transform 0.3s cubic-bezier(.21,1.93,.53,.64)'
  coverTransform.value = 'translateY(0px)'
}
</script>

<style lang="scss" scoped>
%flex-center {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

%section {
  display: flex;
  justify-content: space-around;
  align-content: center;
  background: #fff;
  border-radius: 10rpx;
}

.user-section {
  height: 520rpx;
  padding: 100rpx 30rpx 0;
  position: relative;

  .mp-nav-btns {
    position: absolute;
    top: 60rpx;
    right: 30rpx;
    z-index: 10;
    display: flex;
    align-items: center;
    gap: 30rpx;

    .nav-btn {
      font-size: 48rpx;
      color: #fff;
    }
  }

  .bg {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    filter: blur(1px);
    opacity: 0.7;
  }
}

.user-info-box {
  height: 180rpx;
  display: flex;
  align-items: center;
  position: relative;
  z-index: 1;

  .portrait {
    width: 130rpx;
    height: 130rpx;
    border: 5rpx solid #fff;
    border-radius: 50%;
  }

  .username {
    font-size: 38rpx;
    color: #303133;
    margin-left: 20rpx;
  }
}

.vip-card-box {
  display: flex;
  flex-direction: column;
  color: #f7d680;
  height: 240rpx;
  background: linear-gradient(to right, rgba(0, 0, 0, 0.7), rgba(0, 0, 0, 0.8));
  border-radius: 16rpx 16rpx 0 0;
  overflow: hidden;
  position: relative;
  padding: 20rpx 24rpx;

  .card-bg {
    position: absolute;
    top: 20rpx;
    right: 0;
    width: 380rpx;
    height: 260rpx;
  }

  .b-btn {
    position: absolute;
    right: 20rpx;
    top: 16rpx;
    width: 132rpx;
    height: 40rpx;
    text-align: center;
    line-height: 40rpx;
    font-size: 22rpx;
    color: #36343c;
    border-radius: 20px;
    background: linear-gradient(to right, #f9e6af, #ffd465);
    z-index: 1;
  }

  .tit {
    font-size: 30rpx;
    color: #f7d680;
    margin-bottom: 28rpx;

    .yticon {
      color: #f6e5a3;
      margin-right: 16rpx;
    }
  }

  .e-b {
    font-size: 24rpx;
    color: #d8cba9;
    margin-top: 10rpx;
  }
}

.cover-container {
  background: #f8f8f8;
  margin-top: -150rpx;
  padding: 0 30rpx;
  position: relative;
  background: #f5f5f5;
  padding-bottom: 20rpx;

  .arc {
    position: absolute;
    left: 0;
    top: -34rpx;
    width: 100%;
    height: 36rpx;
  }
}

.tj-sction {
  @extend %section;

  .tj-item {
    @extend %flex-center;
    flex-direction: column;
    height: 140rpx;
    font-size: 24rpx;
    color: #75787d;
  }

  .num {
    font-size: 32rpx;
    color: #303133;
    margin-bottom: 8rpx;
  }
}

.order-section {
  @extend %section;
  padding: 28rpx 0;
  margin-top: 20rpx;

  .order-item {
    @extend %flex-center;
    width: 120rpx;
    height: 120rpx;
    border-radius: 10rpx;
    font-size: 24rpx;
    color: #303133;
  }

  .yticon {
    font-size: 48rpx;
    margin-bottom: 18rpx;
    color: #fa436a;
  }

  .icon-shouhoutuikuan {
    font-size: 44rpx;
  }
}

.history-section {
  padding: 30rpx 0 0;
  margin-top: 20rpx;
  background: #fff;
  border-radius: 10rpx;
}
</style>

<style scoped>
.account-reference{background:#f8f5f0;min-height:100vh;padding-bottom:85px}.account-top{position:sticky;top:0;z-index:10;text-align:center;padding:13px;color:#fff;background:#d8ad50;font-size:18px;font-weight:600}.account-settings{position:absolute;right:18px;font-size:12px;top:16px}.account-profile{height:153px;background:linear-gradient(90deg,#244b36cc,#9f865e44),url('/static/travel-photo/alpine-lake.jpg') center/cover;display:flex;align-items:flex-start;padding:26px 22px;gap:14px;color:#fff}.account-profile image{width:66px;height:66px;border-radius:22px;background:#fff;border:2px solid #ffffffa0}.account-name{font-size:20px;font-weight:700;padding-top:6px}.account-name text{font-size:10px;margin-left:8px;background:#fff3c85c;border-radius:12px;padding:3px 7px;font-weight:400}.account-id{display:block;font-size:11px;margin-top:12px;color:#fff3d8}.account-panels{position:relative;margin:-29px 12px 0}.account-panel{background:#fffefb;border-radius:18px;padding:17px 13px;margin-bottom:13px;box-shadow:0 6px 20px #9e895c06}.account-section-title{display:flex;justify-content:space-between;align-items:center;font-size:17px;font-weight:700;color:#39372f;padding-left:8px;border-left:3px solid #c99455;margin-bottom:17px}.account-section-title small{font-size:10px;color:#a19c92;font-weight:400}.account-assets{display:grid;grid-template-columns:repeat(3,1fr);text-align:center;padding:9px 0 5px}.account-assets text{display:block;font-size:19px;color:#b45e35;font-weight:700}.account-assets small{display:block;font-size:11px;color:#89857c;margin-top:7px}.account-stat-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px}.account-stat-grid>view{background:#fcfaf3;border-radius:7px;padding:13px 10px}.account-stat-grid text{display:block;font-size:16px;font-weight:700;color:#887240}.account-stat-grid small{display:block;font-size:11px;color:#a19a8a;margin-top:8px}.recommend-layout{display:grid;grid-template-columns:repeat(3,1fr) .9fr;gap:5px;text-align:center}.recommend-stage{background:#fcfbf7;border-radius:10px;padding:9px 3px}.stage-dot{width:29px;height:29px;border-radius:50%;background:#f0ede4;color:#979184;margin:0 auto 10px;line-height:29px;font-size:13px}.recommend-stage text{display:block;font-size:11px;font-weight:600;color:#45413a}.recommend-stage small{display:block;font-size:9px;color:#b0a99b;margin-top:8px}.recommend-count{background:#f6f2e9;border-radius:10px;padding:21px 0}.recommend-count text{display:block;font-size:20px;font-weight:700;color:#887240}.recommend-count small{font-size:9px;color:#938a7a}.account-section-footer{display:flex;justify-content:space-between;margin-top:15px;font-size:10px;color:#a8a18f;gap:7px}.account-section-footer text:last-child{color:#b36a3c;cursor:pointer}.account-services{display:grid;grid-template-columns:repeat(4,1fr);row-gap:22px;text-align:center}.account-services>view{cursor:pointer;display:flex;flex-direction:column;align-items:center;gap:10px;font-size:11px;color:#514c43}.account-services .yticon{font-size:23px;color:#bb8b58}.service-grid{padding:5px 0 10px}.account-error{font-size:12px;color:#ae5c32;padding:12px;text-align:center}
@media(min-width:900px){.account-reference{max-width:1240px;margin:auto}.account-profile{height:215px;padding:40px}.account-profile image{width:85px;height:85px}.account-name{font-size:27px}.account-panels{margin:-35px 30px 0;display:grid;grid-template-columns:1fr 1fr;gap:18px}.account-panel{margin:0;padding:23px}.account-panel:last-of-type{grid-column:1/-1}.account-section-title{font-size:20px}.account-stat-grid>view{padding:18px}.account-services{padding:12px}.account-assets{padding:20px}.account-error{grid-column:1/-1}}
</style>

<style scoped>
.account-reference{background:#f2f6f5}.account-top{position:static;background:#e5f0ef;color:#245660;border-bottom:1px solid #d7e6e5}.account-panel{background:#fff;border:1px solid #e7eeeb;box-shadow:0 5px 20px #315c4f05}.account-section-title{border-color:#2a8a88;color:#284b51}.account-assets text{color:#167a80}.account-assets small,.account-section-title small{color:#819599}.account-stat-grid>view{background:#f3f8f6}.account-stat-grid text,.recommend-count text{color:#326f62}.account-stat-grid small{color:#7e9890}.recommend-stage{background:#f7faf9}.stage-dot{background:#e7f0ec;color:#628c7c}.recommend-count{background:#eaf3ef}.account-section-footer text:last-child,.account-services .yticon{color:#288784}.account-services>view{color:#405e62}.account-section-footer{color:#829896}.account-error{color:#386c73}
</style>
