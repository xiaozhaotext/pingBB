<template>
  <view class="app">
    <view class="card">
      <text class="title">联创权益</text>
      <view class="row"><text>权益价格</text><text class="v">10000 元</text></view>
      <view class="row"><text>赠送商品积分</text><text class="v">10000</text></view>
      <view class="row"><text>赠送复购积分</text><text class="v">10000</text></view>
      <view class="row"><text>直接分享奖励</text><text class="v">10% 现金奖励（单笔封顶 200 元）</text></view>
      <view class="row"><text>每日分红</text><text class="v">3 份</text></view>
    </view>

    <view class="card">
      <text class="title">选择支付方式</text>
      <view class="pay-methods">
        <view class="pay-item" :class="{ active: payChannel === 'WECHAT' }" @click="payChannel = 'WECHAT'">
          <text>微信支付</text>
        </view>
        <view class="pay-item" :class="{ active: payChannel === 'ALIPAY' }" @click="payChannel = 'ALIPAY'">
          <text>支付宝</text>
        </view>
      </view>
      <button class="btn-primary mt" :disabled="submitting" @click="handleBuy">立即购买</button>
      <view v-if="orderInfo" class="result">
        下单成功：订单号 {{ orderInfo.merchantOrderNo }}，金额 {{ orderInfo.amount }} 元。{{ payHint }}
      </view>
      <view v-if="actionError" class="result error">{{ actionError }}</view>
      <view class="hint">下单后将跳转到支付平台完成付款；支付结果以实际到账通知为准，身份与积分在支付成功后自动发放。</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMemberStore } from '@/stores/member'
import { createMembershipOrderAPI,
  confirmMockMembershipPaymentAPI, getMemberInfoAPI } from '@/apis/member'

const payChannel = ref<'WECHAT' | 'ALIPAY'>('WECHAT')
const submitting = ref(false)
const orderInfo = ref<any>(null)
const payHint = ref('')
const actionError = ref('')

/** 生成幂等键：8-64 位字母数字 */
const genRequestId = () => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let s = 'MP'
  for (let i = 0; i < 24; i++) s += chars[Math.floor(Math.random() * chars.length)]
  return s
}

const resolveMemberId = async (): Promise<number | undefined> => {
  const cached = useMemberStore().memberInfo?.id
  if (cached) return cached
  try {
    const r = await getMemberInfoAPI()
    return r.data?.id
  } catch {
    return undefined
  }
}

const handleBuy = async () => {
  actionError.value = ''
  orderInfo.value = null
  payHint.value = ''
  const memberId = await resolveMemberId()
  if (!memberId) {
    actionError.value = '请先登录后再购买。'
    return
  }
  submitting.value = true
  try {
    const r = await createMembershipOrderAPI({
      memberId,
      level: 'COFOUNDER',
      payChannel: payChannel.value,
      payMode: payChannel.value === 'WECHAT' ? 'WECHAT_H5' : 'ALIPAY_WAP',
      requestId: genRequestId(),
    })
    const o = r.data
    orderInfo.value = o
    const payment = o.payment || {}
    if (payment.mock === 'true' || payment.mock === true) {
      payHint.value = '正在确认本地演示支付…'
      await confirmMockMembershipPaymentAPI(o.orderId)
      payHint.value = '支付已完成，权益生效中…'
      uni.showToast({ icon: 'success', title: '支付成功' })
      setTimeout(() => {
        uni.redirectTo({ url: '/pages/member/center' })
      }, 800)
      return
    }
    if (payment.type === 'WECHAT_H5_URL' && payment.h5Url) {
      payHint.value = '正在跳转微信支付…'
      // #ifdef H5
      window.location.href = payment.h5Url
      // #endif
      // #ifndef H5
      uni.setClipboardData({ data: payment.h5Url })
      payHint.value = '支付链接已复制，请粘贴到浏览器完成支付。'
      // #endif
    } else if (payment.type === 'ALIPAY_FORM' && payment.formHtml) {
      payHint.value = '正在跳转支付宝…'
      // #ifdef H5
      const div = document.createElement('div')
      div.innerHTML = payment.formHtml
      document.body.appendChild(div)
      const form = div.querySelector('form')
      if (form) form.submit()
      // #endif
      // #ifndef H5
      payHint.value = '订单已创建，请在支付宝内完成支付。'
      // #endif
    } else {
      payHint.value = '订单已创建，请按提示完成支付。'
    }
  } catch (e: any) {
    const code = e?.data?.code
    const msg = e?.data?.message
    if (code === 56003) {
      actionError.value = '会员购买暂未开放，敬请期待。'
    } else if (code === 56004) {
      actionError.value = '您已是联创，无需重复购买。'
    } else if (code === 56002) {
      actionError.value = '支付渠道尚未开通，请稍后再试。'
    } else {
      actionError.value = '下单失败：' + (msg || '请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 32rpx; font-weight: 700; color: #303133; display: block; margin-bottom: 16rpx; }
.row { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 28rpx; color: #606266; }
.row .v { color: #303133; font-weight: 600; }
.pay-methods { display: flex; gap: 20rpx; margin: 8rpx 0 8rpx; }
.pay-item { flex: 1; text-align: center; padding: 24rpx 0; border: 2rpx solid #e5e5e5; border-radius: 12rpx; font-size: 28rpx; color: #606266; }
.pay-item.active { border-color: #285b40; color: #285b40; font-weight: 700; background: #f0f7f3; }
.btn-primary { background: #285b40; color: #fff; border-radius: 12rpx; }
.btn-primary[disabled] { opacity: 0.6; }
.mt { margin-top: 16rpx; }
.result { margin-top: 16rpx; font-size: 26rpx; color: #285b40; line-height: 1.6; }
.hint { margin-top: 16rpx; font-size: 22rpx; color: #909399; line-height: 1.6; }
.error { color: #b42318; }
</style>
