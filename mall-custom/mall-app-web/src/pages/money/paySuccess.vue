<template>
  <view class="content">
    <text v-if="confirmed" class="success-icon yticon icon-xuanzhong2"></text>
    <text class="tit">{{ payText }}</text>
    <text class="result-message">{{ resultMessage }}</text>
    <text v-if="orderSn" class="result-message">订单号：{{ orderSn }}</text>
    <text v-if="!confirmed" class="result-message" @click="loadData">重新查询支付结果</text>

    <view class="btn-group">
      <navigator url="/pages/order/order?state=0" open-type="redirect" class="mix-btn">
        查看订单
      </navigator>
      <navigator url="/pages/index/index" open-type="switchTab" class="mix-btn hollow">
        返回商城
      </navigator>
      <navigator url="/pages/cart/cart" open-type="switchTab" class="mix-btn hollow">
        返回购物车
      </navigator>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { fetchAliapyStatusAPI, getOrderDetailAPI } from '@/apis/order'
import { isOrderPaid } from '@/utils/paymentResult'

// 是否使用支付宝支付（H5端启用，通过环境变量配置）
const USE_ALIPAY = import.meta.env.VITE_USE_ALIPAY === 'true'

// ===== 页面数据 =====
// 支付结果文字
const payText = ref('')
const confirmed = ref(false)
const resultMessage = ref('')
const orderSn = ref('')
let orderId: number | null = null
let alipayOrderSn = ''
let leaving = false
let loading = false
let returnTimer: ReturnType<typeof setTimeout> | undefined
const showConfirmed = () => {
  if (leaving) return
  confirmed.value = true
  payText.value = '支付成功，订单已确认'
  resultMessage.value = '无需再次付款，2秒后自动返回商城。'
  returnTimer = setTimeout(() => { if (!leaving) uni.switchTab({ url: '/pages/index/index' }) }, 2000)
}

// ===== 数据加载 =====
// 查询支付宝交易状态
const loadData = async () => {
  if (loading || confirmed.value || leaving) return
  loading = true
  payText.value = '正在核对支付结果…'
  try {
    if (orderId) {
      const { data } = await getOrderDetailAPI(orderId)
      if (leaving) return
      orderSn.value = data.orderSn
      if (isOrderPaid(data.status)) { showConfirmed(); return }
    } else if (USE_ALIPAY && alipayOrderSn) {
      const { data } = await fetchAliapyStatusAPI({ outTradeNo: alipayOrderSn })
      if (leaving) return
      if (data === 'TRADE_SUCCESS' || data === 'TRADE_FINISHED') { showConfirmed(); return }
    }
    payText.value = '支付结果待确认'
    resultMessage.value = '尚未获得后台成功确认，不代表付款失败。如已扣款，请勿重复付款，稍后查询订单或联系客服。'
  } catch (e) {
    payText.value = '暂时无法确认支付结果'
    resultMessage.value = '请稍后重新查询或查看订单。如已扣款，请勿重复付款。'
  } finally { loading = false }
}

// ===== 生命周期 =====
// 页面加载
onLoad(async (options: any) => {
  const id = Number(options.orderId)
  orderId = Number.isSafeInteger(id) && id > 0 ? id : null
  alipayOrderSn = String(options.out_trade_no || '')
  await loadData()
})
onUnload(() => {
  leaving = true
  if (returnTimer) clearTimeout(returnTimer)
})
</script>

<style lang="scss" scoped>
.result-message { display: block; margin: 24rpx 32rpx 0; color: #666; line-height: 1.7; text-align: center; }
.content {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.success-icon {
  font-size: 160rpx;
  color: #fa436a;
  margin-top: 100rpx;
}

.tit {
  font-size: 38rpx;
  color: #303133;
}

.btn-group {
  padding-top: 100rpx;
}

.mix-btn {
  margin-top: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 600rpx;
  height: 80rpx;
  font-size: $font-lg;
  color: #fff;
  background-color: $base-color;
  border-radius: 10rpx;

  &.hollow {
    background: #fff;
    color: #303133;
    border: 1px solid #ccc;
  }
}
</style>
