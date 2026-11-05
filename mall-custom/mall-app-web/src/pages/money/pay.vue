<template>
  <view class="app">
    <view class="price-box">
      <text>支付金额</text>
      <text class="price">{{ orderInfo.payAmount }}</text>
    </view>

    <view class="pay-type-list" v-if="USE_ALIPAY || USE_WECHAT_PAY">
      <view class="type-item b-b" v-if="USE_ALIPAY" @click="handleChangePayType(1)">
        <text class="icon yticon icon-alipay"></text>
        <view class="con">
          <text class="tit">支付宝支付</text>
          <text>推荐使用支付宝支付</text>
        </view>
        <label class="radio">
          <radio name="payType" value="" color="#fa436a" :checked="payType == 1" />
        </label>
      </view>
      <view class="type-item b-b" v-if="USE_WECHAT_PAY" @click="handleChangePayType(2)">
        <text class="icon yticon icon-weixinzhifu"></text>
        <view class="con">
          <text class="tit">微信支付</text>
        </view>
        <label class="radio">
          <radio name="payType" value="" color="#fa436a" :checked="payType == 2" />
        </label>
      </view>
    </view>

    <view v-else-if="USE_MOCK_PAYMENT" class="mock-notice">沙盒演示支付：不扣真实资金。支付成功后按真实业务规则更新测试订单、库存和积分。</view>
    <view v-else class="mock-notice">在线支付尚未开放，请勿付款。</view>

    <view v-if="paymentState !== 'ready' || paymentMessage" class="payment-status" role="status">
      <text>{{ paymentMessage }}</text>
      <text v-if="paymentState === 'pending' || paymentState === 'error'" class="status-action" @click="retryCheck">重新查询支付结果</text>
      <text v-if="paymentState !== 'ready'" class="status-action" @click="viewOrder">查看订单</text>
    </view>
    <text v-if="paymentState === 'ready' && (USE_ALIPAY || USE_WECHAT_PAY || USE_MOCK_PAYMENT)" class="mix-btn" @click="handleConfirmPay">{{ paying ? '正在发起支付…' : USE_ALIPAY || USE_WECHAT_PAY ? '确认支付' : '确认模拟支付' }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrderDetailAPI, confirmMockPaymentAPI, wechatOrderPayH5API, wechatOrderPayJsapiAPI, getWechatOrderPaymentStatusAPI } from '@/apis/order'
import { ensureWechatOauth, invokeWechatJsapi, isWechatBrowser } from '@/utils/wechatPay'
import { isOrderPaid, wechatReturnUrl, withWechatReturnUrl } from '@/utils/paymentResult'
import { tokenStorageKey } from '@/utils/sessionKeys'
import type { OmsOrderDetail } from '@/types/order'

// 是否使用支付宝支付（H5端启用，通过环境变量配置）
const USE_ALIPAY = import.meta.env.VITE_USE_ALIPAY === 'true'
// 是否使用微信H5支付（H5端启用，通过环境变量配置）
const USE_WECHAT_PAY = import.meta.env.VITE_USE_WECHAT_PAY === 'true'
const USE_MOCK_PAYMENT = import.meta.env.VITE_USE_MOCK_PAYMENT === 'true'

// ===== 页面数据 =====
// 订单ID
const orderId = ref<number | null>(null)
// 支付方式：1->支付宝；2->微信
const payType = ref(USE_ALIPAY ? 1 : USE_WECHAT_PAY ? 2 : 0)
// 订单信息
const orderInfo = ref<Partial<OmsOrderDetail>>({})
const paymentState = ref<'loading' | 'ready' | 'checking' | 'pending' | 'closed' | 'review' | 'error'>('loading')
const paymentMessage = ref('正在读取订单…')
const paying = ref(false)
const checking = ref(false)
let initialized = false
let disposed = false
let navigating = false
const pendingKey = () => `${tokenStorageKey}:order-payment:${orderId.value}`
const showSuccess = () => {
  if (navigating || disposed) return
  navigating = true
  uni.removeStorageSync(pendingKey())
  uni.redirectTo({ url: `/pages/money/paySuccess?orderId=${orderId.value}` })
}
const viewOrder = () => uni.redirectTo({ url: `/pages/order/orderDetail?orderId=${orderId.value}` })

// Never turn a cancelled SDK dialog or a network timeout into "payment failed".
const checkPayment = async () => {
  if (checking.value || navigating || disposed || !orderInfo.value.orderSn) return
  checking.value = true
  paymentState.value = 'checking'
  paymentMessage.value = '正在确认支付结果，请勿重复付款…'
  try {
    let canRetry = false
    for (let attempt = 0; attempt < 8; attempt++) {
      const { data } = await getWechatOrderPaymentStatusAPI(String(orderInfo.value.orderSn))
      if (disposed) return
      if (data.state === 'CONFIRMED') { showSuccess(); return }
      if (data.state === 'REVIEW') {
        paymentState.value = 'review'
        paymentMessage.value = '微信已收款，订单需要核实处理。请联系客服，请勿再次付款。'
        return
      }
      if (data.state === 'CLOSED') {
        paymentState.value = 'closed'
        paymentMessage.value = '订单或支付已关闭。如已扣款，请联系客服核实退款，请勿重复付款。'
        return
      }
      canRetry = data.canRetry
      if (attempt < 7) await new Promise(resolve => setTimeout(resolve, 1500))
      if (disposed) return
    }
    if (canRetry && uni.getStorageSync(pendingKey()) !== 'submitted') {
      uni.removeStorageSync(pendingKey())
      paymentState.value = 'ready'
      paymentMessage.value = '微信查单显示尚未支付。可重试支付；如银行已扣款，请先查询订单或联系客服。'
    } else {
      paymentState.value = 'pending'
      paymentMessage.value = '支付结果仍在确认中，并不代表支付失败。如已扣款，请勿重复付款，可稍后查询订单。'
    }
  } catch (error) {
    if (!disposed) {
      paymentState.value = 'pending'
      paymentMessage.value = '暂时无法确认支付结果，并不代表支付失败。如已扣款，请勿重复付款，稍后重新查询。'
    }
  } finally {
    checking.value = false
  }
}

// ===== 数据加载 =====
// 加载订单详情
const loadData = async () => {
  try {
    const res = await getOrderDetailAPI(orderId.value!)
    orderInfo.value = res.data
    if (isOrderPaid(res.data.status)) { showSuccess(); return }
    if (res.data.status !== 0) {
      paymentState.value = 'closed'
      paymentMessage.value = '订单已关闭或不可支付。如已扣款，请联系客服核实。'
      // A late payment on a closed order must still be reconciled, never paid again.
      if (uni.getStorageSync(pendingKey())) await checkPayment()
      return
    }
    if (uni.getStorageSync(pendingKey())) await checkPayment()
    else { paymentState.value = 'ready'; paymentMessage.value = '' }
  } catch (e) {
    paymentState.value = 'error'
    paymentMessage.value = '订单信息暂时无法读取，请重新查询；如已付款请勿重复支付。'
  }
}

// ===== 生命周期 =====
// 页面加载
onLoad(async (options: any) => {
  const id = Number(options.orderId)
  if (!Number.isSafeInteger(id) || id <= 0) {
    paymentState.value = 'error'; paymentMessage.value = '订单链接无效，请从我的订单进入。'; return
  }
  orderId.value = id
  if (options.checkPayment === '1') uni.setStorageSync(pendingKey(), 'attempted')
  await loadData()
  initialized = true
})
const retryCheck = () => orderInfo.value.orderSn && uni.getStorageSync(pendingKey()) ? checkPayment() : loadData()
const resumePayment = () => {
  if (initialized && !paying.value && !disposed && uni.getStorageSync(pendingKey())) void checkPayment()
}
onShow(resumePayment)
const visibilityChanged = () => { if (document.visibilityState === 'visible') resumePayment() }
onMounted(() => {
  // #ifdef H5
  document.addEventListener('visibilitychange', visibilityChanged)
  window.addEventListener('pageshow', resumePayment)
  // #endif
})
onUnmounted(() => {
  disposed = true
  // #ifdef H5
  document.removeEventListener('visibilitychange', visibilityChanged)
  window.removeEventListener('pageshow', resumePayment)
  // #endif
})

// ===== 事件处理方法 =====
// 选择支付方式
const handleChangePayType = (type: number) => {
  payType.value = type
}

// 确认支付
const handleConfirmPay = async () => {
  if (paying.value || checking.value || paymentState.value !== 'ready' || !orderInfo.value.orderSn) return
  paying.value = true
  try {
  // #ifdef H5
  if (USE_ALIPAY && payType.value == 1) {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
    const query = new URLSearchParams({
      outTradeNo: String(orderInfo.value.orderSn),
      subject: '商城订单',
    })
    window.location.href = `${apiBaseUrl}/alipay/webPay?${query}`
    return
  }
  if (USE_WECHAT_PAY && payType.value == 2) {
    try {
      if (isWechatBrowser()) {
        if (!(await ensureWechatOauth('ORDER', Number(orderId.value)))) return
        const jsapi = await wechatOrderPayJsapiAPI(String(orderInfo.value.orderSn))
        uni.setStorageSync(pendingKey(), 'attempted')
        await invokeWechatJsapi(jsapi.data)
        uni.setStorageSync(pendingKey(), 'submitted')
        await checkPayment()
        return
      }
      const res = await wechatOrderPayH5API(String(orderInfo.value.orderSn))
      const h5Url = res.data?.h5Url
      if (h5Url) {
        uni.setStorageSync(pendingKey(), 'attempted')
        window.location.href = withWechatReturnUrl(h5Url, wechatReturnUrl(window.location.href, Number(orderId.value)))
        return
      }
      uni.showToast({ title: '微信支付下单失败', icon: 'none' })
    } catch (e) {
      if (uni.getStorageSync(pendingKey())) {
        if (e instanceof Error && e.message.includes(':cancel')) {
          uni.removeStorageSync(pendingKey())
          paymentMessage.value = '已取消本次支付，可查看订单确认状态。'
          await loadData()
        } else await checkPayment()
      } else {
        paymentMessage.value = '暂时无法发起微信支付，请稍后重试。'
      }
    }
    return
  }
  if (USE_ALIPAY || USE_WECHAT_PAY) {
    uni.showToast({ title: '当前支付方式未开通', icon: 'none' })
    return
  }
  // #endif

  if (!USE_MOCK_PAYMENT) {
    uni.showToast({ title: '在线支付尚未开放', icon: 'none' })
    return
  }
  try {
    await confirmMockPaymentAPI(orderId.value!)
    uni.redirectTo({
      url: `/pages/money/paySuccess?orderId=${orderId.value}`,
    })
  } catch (e) {
    console.error('支付失败', e)
  }
  } finally { paying.value = false }
}
</script>

<style lang="scss" scoped>
.payment-status { margin: 28rpx; padding: 24rpx; border-radius: 12rpx; background: #fff7e6; color: #714d13; display: flex; flex-direction: column; gap: 22rpx; line-height: 1.6; }
.status-action { color: #286da8; text-decoration: underline; }
.mock-notice { margin: 20rpx; padding: 22rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 26rpx; line-height: 1.5; }
.app {
  width: 100%;
}

.price-box {
  background-color: #fff;
  height: 265rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  font-size: 28rpx;
  color: #909399;

  .price {
    font-size: 50rpx;
    color: #303133;
    margin-top: 12rpx;

    &:before {
      content: '￥';
      font-size: 40rpx;
    }
  }
}

.pay-type-list {
  margin-top: 20rpx;
  background-color: #fff;
  padding-left: 60rpx;

  .type-item {
    height: 120rpx;
    padding: 20rpx 0;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-right: 60rpx;
    font-size: 30rpx;
    position: relative;
  }

  .icon {
    width: 100rpx;
    font-size: 52rpx;
  }

  .icon-erjiye-yucunkuan {
    color: #fe8e2e;
  }

  .icon-weixinzhifu {
    color: #36cb59;
  }

  .icon-alipay {
    color: #01aaef;
  }

  .tit {
    font-size: $font-lg;
    color: $font-color-dark;
    margin-bottom: 4rpx;
  }

  .con {
    flex: 1;
    display: flex;
    flex-direction: column;
    font-size: $font-sm;
    color: $font-color-light;
  }
}

.mix-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 630rpx;
  height: 80rpx;
  margin: 80rpx auto 30rpx;
  font-size: $font-lg;
  color: #fff;
  background-color: $base-color;
  border-radius: 10rpx;
  box-shadow: 1px 2px 5px rgba(219, 63, 96, 0.4);
}
</style>
