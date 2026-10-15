<template>
  <view>
    <!-- 地址 -->
    <navigator url="/pages/address/address?source=1" class="address-section">
      <view class="order-content">
        <text class="yticon icon-shouhuodizhi"></text>
        <view class="cen">
          <view class="top">
            <text class="name">{{ currentAddress.name }}</text>
            <text class="mobile">{{ currentAddress.phoneNumber }}</text>
          </view>
          <text class="address">
            {{ currentAddress.province }} {{ currentAddress.city }} {{ currentAddress.region }}
            {{ currentAddress.detailAddress }}
          </text>
        </view>
        <text class="yticon icon-you"></text>
      </view>

      <image
        class="a-bg"
        src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAu4AAAAFCAYAAAAaAWmiAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyJpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDUuMy1jMDExIDY2LjE0NTY2MSwgMjAxMi8wMi8wNi0xNDo1NjoyNyAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIENTNiAoV2luZG93cykiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6Rjk3RjkzMjM2NzMxMTFFOUI4RkU4OEZGMDcxQzgzOEYiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6Rjk3RjkzMjQ2NzMxMTFFOUI4RkU4OEZGMDcxQzgzOEYiPiA8eG1wTU06RGVyaXZlZEZyb20gc3RSZWY6aW5zdGFuY2VJRD0ieG1wLmlpZDpGOTdGOTMyMTY3MzExMUU5QjhGRTg4RkYwNzFDODM4RiIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDpGOTdGOTMyMjY3MzExMUU5QjhGRTg4RkYwNzFDODM4RiIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/PrEOZlQAAAiuSURBVHjazJp7bFvV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"
      ></image>
    </navigator>

    <view class="goods-section">
      <view class="g-header b-b">
        <text class="name">商品信息</text>
      </view>
      <!-- 商品列表 -->
      <view class="g-item" v-for="item in cartPromotionItemList" :key="item.id">
        <image :src="item.productPic"></image>
        <view class="right">
          <text class="zone-tag">{{ item.zoneLabel || '普通商品' }}</text>
          <text class="title clamp">{{ item.productName }}</text>
          <text class="spec">{{ formatProductAttr(item.productAttr) }}</text>
          <text class="promotion clamp">{{ item.promotionMessage }}</text>
          <view class="price-box">
            <text class="price">￥{{ item.price }}</text>
            <text class="number">x {{ item.quantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 支付方式 -->
    <view class="yt-list">
      <view class="g-header b-b">
        <text class="name">支付方式</text>
      </view>
      <view class="zone-plan-empty" v-if="zonePlans.length === 0">暂无可结算商品</view>
      <!-- 按专区隔离结算：各专区只能使用本专区规则，不能串用 -->
      <view class="zone-plan" v-for="plan in zonePlans" :key="plan.zoneCode">
        <view class="zone-plan-head">
          <text class="zone-plan-title">{{ plan.zoneLabel }}</text>
          <text class="zone-plan-rule">{{ plan.zoneRule }}</text>
          <text class="zone-plan-state" :class="{ blocked: !plan.payable }">{{ plan.payable ? '可结算' : '不可结算' }}</text>
        </view>
        <view class="yt-list-cell b-b">
          <text class="cell-tit clamp">商品金额</text>
          <text class="cell-tip">{{ money(plan.grossAmount) }}</text>
        </view>
        <view class="yt-list-cell b-b" v-if="num(plan.promotionAmount) > 0">
          <text class="cell-tit clamp">活动优惠</text>
          <text class="cell-tip red">-{{ money(plan.promotionAmount) }}</text>
        </view>
        <view class="yt-list-cell b-b" v-if="num(plan.couponAmount) > 0">
          <text class="cell-tit clamp">优惠券抵扣</text>
          <text class="cell-tip red">-{{ money(plan.couponAmount) }}</text>
        </view>
        <!-- 自选区：现金或余额（充值本金）二选一 -->
        <view class="yt-list-cell b-b" v-if="plan.zoneCode === 'PICK'">
          <text class="cell-tit clamp">使用余额支付<text class="cell-sub">（可用 {{ money(rechargeBalance) }}，余额与现金二选一）</text></text>
          <switch :checked="useBalance" @change="handleBalanceToggle" />
        </view>
        <!-- 复购区：30%复购积分 + 70%现金 -->
        <view class="yt-list-cell b-b" v-if="plan.zoneCode === 'REBUY'">
          <text class="cell-tit clamp">复购积分{{ rebuyPercent }}%+现金<text class="cell-sub">（需 {{ plan.repurchasePoints }} 积分，可用 {{ repurchasePointsBalance }}）</text></text>
          <switch :checked="useRepurchasePoints" @change="handleRepurchaseToggle" />
        </view>
        <!-- 消费区：仅用商品积分购买 -->
        <view class="yt-list-cell b-b" v-if="plan.zoneCode === 'CONSUME'">
          <text class="cell-tit clamp">商品积分购买<text class="cell-sub">（需 {{ plan.productPoints }} 积分，可用 {{ productPointsBalance }}）</text></text>
        </view>
        <view class="zone-plan-foot">
          <text v-if="plan.zoneCode === 'CONSUME'">本专区应付 {{ plan.productPoints }} 商品积分</text>
          <text v-else>本专区现金应付 {{ money(plan.cashAmount) }}</text>
          <text v-if="num(plan.balanceAmount) > 0"> · 余额抵扣 {{ money(plan.balanceAmount) }}</text>
          <text v-if="num(plan.repurchasePointAmount) > 0"> · 复购积分抵扣 {{ money(plan.repurchasePointAmount) }}</text>
        </view>
        <view class="zone-plan-blocked" v-if="!plan.payable">{{ plan.zoneLabel }}：{{ plan.blockedReason }}</view>
      </view>
    </view>

    <!-- 优惠明细 -->
    <view class="yt-list">
      <view class="yt-list-cell b-b" @click="handleToggleMask('show')">
        <view class="cell-icon">券</view>
        <text class="cell-tit clamp">优惠券</text>
        <text class="cell-tip active" v-if="currCoupon != null">-￥{{ currCoupon.amount }}</text>
        <text class="cell-tip active" v-else>选择优惠券</text>
        <text class="cell-more wanjia wanjia-gengduo-d"></text>
      </view>
    </view>

    <!-- 金额明细 -->
    <view class="yt-list">
      <view class="yt-list-cell b-b">
        <text class="cell-tit clamp">商品合计</text>
        <text class="cell-tip">￥{{ calcAmount.totalAmount }}</text>
      </view>
      <view class="yt-list-cell b-b">
        <text class="cell-tit clamp">运费</text>
        <text class="cell-tip">￥{{ calcAmount.freightAmount }}</text>
      </view>
      <view class="yt-list-cell b-b">
        <text class="cell-tit clamp">活动优惠</text>
        <text class="cell-tip red">-￥{{ calcAmount.promotionAmount }}</text>
      </view>
      <view class="yt-list-cell b-b">
        <text class="cell-tit clamp">优惠券</text>
        <text class="cell-tip red">-￥{{ num(zoneSettlement.couponAmount) }}</text>
      </view>
      <view class="yt-list-cell b-b" v-if="num(zoneSettlement.balanceAmount) > 0">
        <text class="cell-tit clamp">余额抵扣（自选区）</text>
        <text class="cell-tip red">-￥{{ zoneSettlement.balanceAmount }}</text>
      </view>
      <view class="yt-list-cell b-b" v-if="num(zoneSettlement.repurchasePointAmount) > 0">
        <text class="cell-tit clamp">复购积分抵扣（复购区）</text>
        <text class="cell-tip red">-￥{{ zoneSettlement.repurchasePointAmount }}</text>
      </view>
      <view class="yt-list-cell b-b" v-if="num(zoneSettlement.productPointAmount) > 0">
        <text class="cell-tit clamp">商品积分抵扣（消费区）</text>
        <text class="cell-tip red">-￥{{ zoneSettlement.productPointAmount }}</text>
      </view>
      <view class="yt-list-cell desc-cell">
        <text class="cell-tit clamp">备注</text>
        <input
          class="desc"
          type="text"
          v-model="desc"
          placeholder="请填写备注信息"
          placeholder-class="placeholder"
        />
      </view>
    </view>

    <!-- 底部 -->
    <view class="footer">
      <view class="price-content">
        <view class="pay-total">
          <text>现金应付</text>
          <text class="price-tip">￥</text>
          <text class="price">{{ calcAmount.payAmount }}</text>
        </view>
        <text class="pay-points" v-if="consumePoints > 0">消费区另需 {{ consumePoints }} 商品积分</text>
        <text class="pay-points pay-warn" v-if="hasBlockedZone">存在不可结算专区，请调整支付方式或移除后提交</text>
      </view>
      <text class="submit" @click="handleSubmit">提交订单</text>
    </view>

    <!-- 优惠券面板 -->
    <view
      class="mask"
      :class="maskState === 0 ? 'none' : maskState === 1 ? 'show' : ''"
      @click="handleToggleMask"
    >
      <view class="mask-content" @click.stop.prevent="stopPrevent">
        <!-- 优惠券页面 -->
        <view
          class="coupon-item"
          v-for="(item, index) in couponList"
          :key="index"
          @click="handleSelectCoupon(item)"
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
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { generateConfirmOrderAPI, generateOrderAPI, settlementPreviewAPI } from '@/apis/order'
import { formatDate } from '@/utils/date'
import type { CartPromotionItem, CalcAmount, ConfirmOrderResult, ZoneSettlement } from '@/types/order'
import type { SmsCoupon } from '@/types/coupon'
import type { MemberReceiveAddress } from '@/types/address'

// ===== 页面数据 =====
// 优惠券面板显示状态
const maskState = ref(0)
// 备注
const desc = ref('')
// 优惠券列表
const couponList = ref<SmsCoupon[]>([])
// 收货地址列表
const memberReceiveAddressList = ref<MemberReceiveAddress[]>([])
// 当前收货地址
const currentAddress = ref<MemberReceiveAddress>({} as MemberReceiveAddress)
// 购物车促销商品列表
const cartPromotionItemList = ref<CartPromotionItem[]>([])
// 计算金额
const calcAmount = ref<CalcAmount>({} as CalcAmount)
// 当前选中的优惠券
const currCoupon = ref<SmsCoupon | null>(null)
// 自选区是否使用余额（充值本金）支付；余额与现金二选一
const useBalance = ref(false)
// 复购区是否使用「30%复购积分 + 70%现金」模式
const useRepurchasePoints = ref(false)
// 三个账户可用余额：商品积分（消费区）、复购积分（复购区）、余额（自选区）
const productPointsBalance = ref(0)
const repurchasePointsBalance = ref(0)
const rechargeBalance = ref(0)
// 复购积分抵扣比例
const rebuyPointsRatio = ref(0.3)
// 按专区隔离的结算明细
const zoneSettlement = ref<ZoneSettlement>({ plans: [] } as unknown as ZoneSettlement)
// 购物车ID列表
const cartIds = ref<number[]>([])
// 同一次确认页提交沿用同一幂等键，避免双击与网络重试产生重复订单。
const orderRequestKey = `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`
const submitting = ref(false)

// ===== 计算属性 =====
// 各专区结算明细：每个专区只能使用本专区规则，不能串用
const zonePlans = computed(() => zoneSettlement.value?.plans || [])
// 复购积分抵扣百分比
const rebuyPercent = computed(() => Math.round((Number(rebuyPointsRatio.value) || 0) * 100))
// 消费区需要支付的商品积分总数
const consumePoints = computed(() =>
  zonePlans.value
    .filter((plan) => plan.zoneCode === 'CONSUME')
    .reduce((acc, plan) => acc + (Number(plan.productPoints) || 0), 0),
)
// 是否存在不可结算的专区（如积分/余额不足）
const hasBlockedZone = computed(() => zonePlans.value.some((plan) => !plan.payable))
// 金额显示：空值补 0
const num = (value: number | undefined | null) => Number(value ?? 0)
const money = (value: number | undefined | null) => `￥${num(value).toFixed(2)}`

// ===== 加载数据 =====
// 生成确认单信息并按专区完成一次结算
const loadData = async () => {
  try {
    const res = await generateConfirmOrderAPI(cartIds.value)
    applyConfirmOrder(res.data)
  } catch (e) {
    console.error('加载确认单失败', e)
  }
}

// 切换优惠券或支付方式后，按专区重新计算最终金额
const refreshSettlement = async () => {
  try {
    const res = await settlementPreviewAPI({
      cartIds: cartIds.value.map(Number),
      couponId: currCoupon.value?.id,
      useBalance: useBalance.value,
      useRepurchasePoints: useRepurchasePoints.value,
    })
    applyConfirmOrder(res.data)
  } catch (e) {
    console.error('重新结算失败', e)
  }
}

// 将后端返回的确认单信息写入页面状态
const applyConfirmOrder = (data: ConfirmOrderResult) => {
  if (!data) return
  memberReceiveAddressList.value = data.memberReceiveAddressList || []
  if (!currentAddress.value?.id) {
    currentAddress.value = getDefaultAddress()
  }
  cartPromotionItemList.value = data.cartPromotionItemList || []
  if (couponList.value.length === 0) {
    for (const item of data.couponHistoryDetailList || []) {
      couponList.value.push(item.coupon)
    }
  }
  calcAmount.value = data.calcAmount
  zoneSettlement.value = data.zoneSettlement
  productPointsBalance.value = num(data.productPointsBalance)
  repurchasePointsBalance.value = num(data.repurchasePointsBalance)
  rechargeBalance.value = num(data.rechargeBalance)
  rebuyPointsRatio.value = num(data.rebuyPointsRatio) || 0.3
}

// 设置当前地址（供 address.vue 调用）
const setCurrentAddress = (address: MemberReceiveAddress) => {
  currentAddress.value = address
  // 标记地址是从地址页选择的，防止 onShow 覆盖
  addressSelectedFromPage.value = true
}

// 地址是否从地址选择页选中（防止 onShow 覆盖）
const addressSelectedFromPage = ref(false)

// 页面显示时刷新数据（从地址页面返回时触发）
onShow(() => {
  if (addressSelectedFromPage.value) {
    // 从地址页选择了地址，不覆盖
    addressSelectedFromPage.value = false
    return
  }
  if (memberReceiveAddressList.value.length > 0) {
    currentAddress.value = getDefaultAddress()
  }
})

defineExpose({
  setCurrentAddress,
})

// 页面加载时执行
onLoad((option) => {
  if (option?.cartIds) {
    cartIds.value = JSON.parse(option.cartIds)
    loadData()
  }
})

// ===== 事件处理方法 =====
// 显示/隐藏优惠券面板
const handleToggleMask = (type?: string) => {
  const timer = type === 'show' ? 10 : 300
  const state = type === 'show' ? 1 : 0
  maskState.value = 2
  setTimeout(() => {
    maskState.value = state
  }, timer)
}

// 阻止事件冒泡
const stopPrevent = () => {}

// 自选区切换余额支付（余额与现金二选一）
const handleBalanceToggle = async (event: any) => {
  useBalance.value = Boolean(event.detail.value)
  await refreshSettlement()
}

// 复购区切换「复购积分 + 现金」模式
const handleRepurchaseToggle = async (event: any) => {
  useRepurchasePoints.value = Boolean(event.detail.value)
  await refreshSettlement()
}

// 提交订单
const handleSubmit = async () => {
  if (submitting.value) return
  if (hasBlockedZone.value) {
    const blocked = zonePlans.value.find((plan) => !plan.payable)
    uni.showToast({
      icon: 'none',
      title: blocked ? `${blocked.zoneLabel}：${blocked.blockedReason}` : '当前专区不可结算',
    })
    return
  }
  if (!currentAddress.value?.id) {
    uni.showToast({ icon: 'none', title: '请选择收货地址' })
    return
  }
  submitting.value = true
  const orderParam = {
    payType: 0,
    couponId: undefined as number | undefined,
    cartIds: cartIds.value.map(Number),
    memberReceiveAddressId: currentAddress.value?.id as number | undefined,
    useIntegration: 0,
    useBalance: useBalance.value,
    useRepurchasePoints: useRepurchasePoints.value ? 1 : 0,
  }
  if (currCoupon.value != null) {
    orderParam.couponId = currCoupon.value.id
  }
  try {
    const res = await generateOrderAPI(orderParam, orderRequestKey)
    const orderId = res.data.order.id
    if (res.data.paidImmediately) {
      // 已由余额或商品积分全额抵扣，订单直接完成支付，无需再进入支付页
      uni.showToast({ icon: 'success', title: '下单成功' })
      setTimeout(() => {
        uni.redirectTo({ url: '/pages/order/order?state=0' })
      }, 800)
      return
    }
    uni.showModal({
      title: '提示',
      content: '订单创建成功，是否要立即支付？',
      confirmText: '去支付',
      cancelText: '取消',
      success: function (res) {
        if (res.confirm) {
          uni.redirectTo({
            url: `/pages/money/pay?orderId=${orderId}`,
          })
        } else if (res.cancel) {
          uni.redirectTo({
            url: '/pages/order/order?state=0',
          })
        }
      },
    })
  } catch (e) {
    console.error('提交订单失败', e)
  } finally {
    submitting.value = false
  }
}

// 选择优惠券并按专区重新结算
const handleSelectCoupon = async (coupon: SmsCoupon) => {
  currCoupon.value = coupon
  handleToggleMask()
  await refreshSettlement()
}

// ===== 辅助方法 =====
// 获取默认收货地址
const getDefaultAddress = () => {
  for (const item of memberReceiveAddressList.value) {
    if (item.defaultStatus === 1) {
      return item
    }
  }
  if (memberReceiveAddressList.value != null && memberReceiveAddressList.value.length > 0) {
    return memberReceiveAddressList.value[0]
  }
  return {} as MemberReceiveAddress
}

// 格式化商品属性
const formatProductAttr = (jsonAttr: string) => {
  if (!jsonAttr) return ''
  try {
    const attrArr = JSON.parse(jsonAttr)
    let attrStr = ''
    for (const attr of attrArr) {
      attrStr += attr.key
      attrStr += ':'
      attrStr += attr.value
      attrStr += ';'
    }
    return attrStr
  } catch {
    return jsonAttr
  }
}

// 格式化日期时间
const formatDateTime = (time: string) => {
  if (time == null || time === '') {
    return 'N/A'
  }
  const date = new Date(time)
  return formatDate(date, 'yyyy-MM-dd hh:mm:ss')
}

// 格式化优惠券使用类型
const formatCouponUseType = (useType: number) => {
  if (useType === 0) {
    return '全场通用'
  } else if (useType === 1) {
    return '指定分类商品可用'
  } else if (useType === 2) {
    return '指定商品可用'
  }
  return ''
}
</script>

<style lang="scss">
page {
  background: $page-color-base;
  /* 底部结算栏高度随专区提示行数变化，预留足够空间避免遮挡最后一行 */
  padding-bottom: 180rpx;
}
</style>

<style lang="scss" scoped>
.address-section {
  padding: 30rpx 0;
  background: #fff;
  position: relative;

  .order-content {
    display: flex;
    align-items: center;
  }

  .icon-shouhuodizhi {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 90rpx;
    color: #888;
    font-size: 44rpx;
  }

  .cen {
    display: flex;
    flex-direction: column;
    flex: 1;
    font-size: 28rpx;
    color: $font-color-dark;
  }

  .name {
    font-size: 34rpx;
    margin-right: 24rpx;
  }

  .address {
    margin-top: 16rpx;
    margin-right: 20rpx;
    color: $font-color-light;
  }

  .icon-you {
    font-size: 32rpx;
    color: $font-color-light;
    margin-right: 30rpx;
  }

  .a-bg {
    position: absolute;
    left: 0;
    bottom: 0;
    display: block;
    width: 100%;
    height: 5rpx;
  }
}

.goods-section {
  margin-top: 16rpx;
  background: #fff;
  padding-bottom: 1px;

  .g-header {
    display: flex;
    align-items: center;
    height: 84rpx;
    padding: 0 30rpx;
    position: relative;
  }

  .logo {
    display: block;
    width: 50rpx;
    height: 50rpx;
    border-radius: 100px;
  }

  .name {
    font-size: 30rpx;
    color: $font-color-base;
    margin-left: 24rpx;
  }

  .g-item {
    display: flex;
    margin: 20rpx 30rpx;

    image {
      flex-shrink: 0;
      display: block;
      width: 140rpx;
      height: 140rpx;
      border-radius: 4rpx;
    }

    .right {
      flex: 1;
      padding-left: 24rpx;
      overflow: hidden;
    }

    .title {
      font-size: 30rpx;
      color: $font-color-dark;
    }

    .spec {
      font-size: 26rpx;
      color: $font-color-light;
    }

    .promotion {
      font-size: 24rpx;
      color: $base-color;
    }

    .price-box {
      display: flex;
      align-items: center;
      font-size: 32rpx;
      color: $font-color-dark;
      padding-top: 10rpx;

      .price {
        margin-bottom: 4rpx;
      }

      .number {
        font-size: 26rpx;
        color: $font-color-base;
        margin-left: 20rpx;
      }
    }

    .step-box {
      position: relative;
    }
  }
}

.yt-list {
  margin-top: 16rpx;
  background: #fff;
}

.yt-list-cell {
  display: flex;
  align-items: center;
  padding: 10rpx 30rpx 10rpx 40rpx;
  line-height: 70rpx;
  position: relative;

  &.cell-hover {
    background: #fafafa;
  }

  &.b-b:after {
    left: 30rpx;
  }

  .cell-icon {
    height: 32rpx;
    width: 32rpx;
    font-size: 22rpx;
    color: #fff;
    text-align: center;
    line-height: 32rpx;
    background: #f85e52;
    border-radius: 4rpx;
    margin-right: 12rpx;

    &.hb {
      background: #ffaa0e;
    }

    &.lpk {
      background: #3ab54a;
    }
  }

  .cell-more {
    align-self: center;
    font-size: 24rpx;
    color: $font-color-light;
    margin-left: 8rpx;
    margin-right: -10rpx;
  }

  .cell-tit {
    flex: 1;
    font-size: 26rpx;
    color: $font-color-light;
    margin-right: 10rpx;

    .cell-sub {
      font-size: 22rpx;
      color: #999;
    }
  }

  .cell-tip {
    font-size: 26rpx;
    color: $font-color-dark;

    &.disabled {
      color: $font-color-light;
    }

    &.active {
      color: $base-color;
    }

    &.red {
      color: $base-color;
    }
  }

  &.desc-cell {
    .cell-tit {
      max-width: 90rpx;
    }
  }

  .desc {
    flex: 1;
    font-size: $font-base;
    color: $font-color-dark;
  }

  .integration {
    flex: 1;
    font-size: $font-base;
    color: $font-color-dark;
    text-align: right;
  }
}

/* 支付列表 */
.pay-list {
  padding-left: 40rpx;
  margin-top: 16rpx;
  background: #fff;

  .pay-item {
    display: flex;
    align-items: center;
    padding-right: 20rpx;
    line-height: 1;
    height: 110rpx;
    position: relative;
  }

  .icon-weixinzhifu {
    width: 80rpx;
    font-size: 40rpx;
    color: #6bcc03;
  }

  .icon-alipay {
    width: 80rpx;
    font-size: 40rpx;
    color: #06b4fd;
  }

  .icon-xuanzhong2 {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 60rpx;
    height: 60rpx;
    font-size: 40rpx;
    color: $base-color;
  }

  .tit {
    font-size: 32rpx;
    color: $font-color-dark;
    flex: 1;
  }
}

.footer {
  position: fixed;
  left: 0;
  bottom: 0;
  z-index: 995;
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 90rpx;
  padding: 8rpx 0;
  justify-content: space-between;
  font-size: 30rpx;
  background-color: #fff;
  z-index: 998;
  color: $font-color-base;
  box-shadow: 0 -1px 5px rgba(0, 0, 0, 0.1);

  .price-content {
    padding-left: 30rpx;
  }

  .price-tip {
    color: $base-color;
    margin-left: 8rpx;
  }

  .price {
    font-size: 36rpx;
    color: $base-color;
  }

  .submit {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 280rpx;
    height: 90rpx;
    color: #fff;
    font-size: 32rpx;
    background-color: $base-color;
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
</style>

<style lang="scss" scoped>
.zone-plan-empty{padding:30rpx;text-align:center;font-size:26rpx;color:#909399}
.zone-tag{display:inline-block;align-self:flex-start;font-size:20rpx;color:#8a6d3b;background:#f6efe0;border-radius:8rpx;padding:2rpx 10rpx;margin-bottom:6rpx}
.zone-plan{border-top:1rpx solid #f0f0f0}
.zone-plan:first-of-type{border-top:0}
.zone-plan-head{display:flex;align-items:center;gap:14rpx;padding:20rpx 30rpx 6rpx}
.zone-plan-title{font-size:28rpx;font-weight:600;color:#303133}
.zone-plan-rule{font-size:22rpx;color:#909399}
.zone-plan-state{margin-left:auto;font-size:20rpx;color:#2e8b57;border:1rpx solid #cfe8db;border-radius:10rpx;padding:2rpx 10rpx}
.zone-plan-state.blocked{color:#e93b3d;border-color:#f6cfd0}
.zone-plan-foot{padding:12rpx 30rpx 20rpx;font-size:24rpx;color:#606266;line-height:34rpx}
.zone-plan-blocked{padding:0 30rpx 18rpx;font-size:22rpx;color:#e93b3d;line-height:32rpx}
.footer .price-content .pay-total{display:flex;align-items:center}
.footer .price-content .pay-points{display:block;font-size:20rpx;color:#909399;line-height:28rpx}
.footer .price-content .pay-warn{color:#e93b3d}
</style>
