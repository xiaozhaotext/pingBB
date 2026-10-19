<template>
  <view class="benefits">
    <text class="title">会员与晋升权益</text>
    <text v-if="error" class="error">{{ error }}</text>
    <view v-if="info" class="card"
      ><text>当前身份：{{ tierNames[info.tier || 'VISITOR'] }}</text
      ><text>有效直邀会员：{{ info.directPaidCount || 0 }} 人</text></view
    >
    <view class="card"
      ><text class="subtitle">游客 → 会员</text
      ><text
        >注册默认为游客，不享有奖励。购买任意一条3999元自选区路线后成为会员，获得3999复购积分、3999消费积分及旅游路线定制权益。</text
      ><text
        >会员在合伙人池有本人基础1份，累计分红上限1200元。每直接邀请1位付费会员增加1份，并获1200复购积分及399.90元直邀奖励。</text
      ></view
    >
    <view class="card"
      ><text class="subtitle">会员 → 合伙人 → 联创</text
      ><text>直邀3名付费会员后晋升合伙人；直邀9名后晋升联创。所有奖励只计直接邀请的付费会员。</text
      ><text
        >合伙人池累计上限：有效直邀0、1、2人分别为1200、2400、3600元；满3、6、9、12、15、18人分别为8000、16000、24000、32000、40000、48000元。每满3人增加8000元，即4000×（已满3人档数×2）；档内每增加1人再加1200元，依此递增。</text
      ><text
        >联创另享联创池1份、独立累计上限1200元，并继续享有合伙人池权益；例如直邀12人两池总上限为33200元。升级不重置已领取金额。</text
      ></view
    >
    <view v-if="info?.tier === 'VISITOR'" class="card"
      ><text class="subtitle">选择路线 · ¥3999</text
      ><text>前往自选区选择任意一条路线，单独下单并支付成功后自动成为会员。</text
      ><button @click="openRoute">前往自选区选路线</button
      ><text>也可直接购买3999元会员身份。</text
      ><template v-if="sandboxMode && paymentOptions?.sandbox"
        ><text>演示沙箱：不扣真实资金，身份、积分和三池流水仅写入独立测试库。</text
        ><button
          :disabled="submitting || !paymentOptions.purchaseEnabled"
          @click="buyWithChannel('ALIPAY')"
          >{{ submitting ? '正在确认演示支付…' : '沙箱支付3999元（不扣款）' }}</button
        ></template
      ><template v-else-if="paymentOptions && !paymentOptions.sandbox"
        ><button
          :disabled="submitting || !paymentOptions.purchaseEnabled || !paymentOptions.wechatReady"
          @click="buyWithChannel('WECHAT')"
          >微信支付3999元{{ paymentOptions.wechatReady ? '' : '（未开通）' }}</button
        ><button
          :disabled="submitting || !paymentOptions.purchaseEnabled || !paymentOptions.alipayReady"
          @click="buyWithChannel('ALIPAY')"
          >支付宝支付3999元{{ paymentOptions.alipayReady ? '' : '（未开通）' }}</button
        ><text v-if="!paymentOptions.wechatReady && !paymentOptions.alipayReady"
          >真实支付尚未开通。请从独立演示沙箱测试购买与权益入账。</text
        ></template
      ><text v-else>{{ paymentError || '正在确认支付环境…' }}</text
      ><text v-if="paymentOptions && !paymentOptions.purchaseEnabled"
        >会员购买规则尚未启用，请联系管理员。</text
      ><text v-if="orderInfo">订单号：{{ orderInfo.merchantOrderNo }}，{{ payHint }}</text
      ><text v-if="actionError" class="error">{{ actionError }}</text></view
    >
    <button v-if="info?.tier && info.tier !== 'VISITOR'" @click="openDividend"
      >查看我的分红与旅游权益</button
    >
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
function openRoute() { uni.navigateTo({ url: '/pages/zone/pick' }) }
import { getTriPoolsAPI, tierNames, type TriSummary } from '@/apis/triPools'
const info = ref<TriSummary | null>(null),
  error = ref('')
onMounted(async () => {
  try {
    info.value = (await getTriPoolsAPI()).data
  } catch {
    error.value = '权益加载失败，请返回重试'
  }
  try {
    paymentOptions.value = (await getMembershipPaymentOptionsAPI()).data
  } catch {
    paymentError.value = '支付环境加载失败，请返回重试'
  }
})

const openDividend = () => uni.navigateTo({ url: '/pages/member/dividend' })
import { useMemberStore } from '@/stores/member'
import {
  createMembershipOrderAPI,
  confirmMockMembershipPaymentAPI,
  getMemberInfoAPI,
  getMembershipPaymentOptionsAPI,
  type MembershipPaymentOptions,
} from '@/apis/member'
const payChannel = ref<'WECHAT' | 'ALIPAY'>('WECHAT')
const submitting = ref(false)
const orderInfo = ref<any>(null)
const payHint = ref('')
const actionError = ref('')
const sandboxMode = import.meta.env.VITE_SANDBOX_MODE === 'true'
const paymentOptions = ref<MembershipPaymentOptions | null>(null),
  paymentError = ref('')

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

const purchaseRequestId = (member: number, channel: string) => {
  const key = `membership-pending-${member}-${channel}`
  let id = uni.getStorageSync(key)
  if (!id) {
    id = genRequestId()
    uni.setStorageSync(key, id)
  }
  return String(id)
}
const buyWithChannel = (channel: 'ALIPAY' | 'WECHAT') => {
  if (submitting.value) return
  payChannel.value = channel
  return handleBuy()
}
const handleBuy = async () => {
  if (submitting.value) return
  if (!paymentOptions.value?.purchaseEnabled || sandboxMode !== paymentOptions.value.sandbox) {
    actionError.value = '支付环境与页面不匹配或购买未启用，请重新进入正确的应用入口'
    return
  }
  actionError.value = ''
  orderInfo.value = null
  payHint.value = ''
  submitting.value = true
  const memberId = await resolveMemberId()
  if (!memberId) {
    submitting.value = false
    actionError.value = '请先登录后再购买。'
    return
  }
  try {
    const r = await createMembershipOrderAPI({
      memberId,
      level: 'MEMBER',
      payChannel: payChannel.value,
      payMode: payChannel.value === 'WECHAT' ? 'WECHAT_H5' : 'ALIPAY_WAP',
      requestId: purchaseRequestId(memberId, payChannel.value),
    })
    const o = r.data
    orderInfo.value = o
    const payment = o.payment || {}
    if (String(payment.mock) === 'true') {
      if (!sandboxMode || !paymentOptions.value.sandbox)
        throw new Error('支付环境不匹配，未确认支付')
      payHint.value = '正在确认演示支付…'
      await confirmMockMembershipPaymentAPI(o.orderId)
      payHint.value = '演示支付已确认，权益仅在测试库生效。'
      uni.removeStorageSync(`membership-pending-${memberId}-${payChannel.value}`)
      info.value = (await getTriPoolsAPI()).data
      uni.showToast({ icon: 'success', title: '演示购买成功，权益已到账' })
      setTimeout(() => {
        uni.switchTab({ url: '/pages/user/user' })
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
    const msg =
      e?.data?.message ||
      e?.message ||
      (e?.statusCode ? `请求失败（HTTP ${e.statusCode}），请检查接口代理与服务状态` : '')
    if (code === 56003) {
      actionError.value = '会员购买暂未开放，敬请期待。'
    } else if (code === 56004) {
      actionError.value = '您已是会员，无需重复购买。'
    } else if (code === 56005) {
      actionError.value = '已有待付款订单，请选择原支付渠道继续付款；请勿重复下单。'
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
<style scoped>
.benefits {
  max-width: 800px;
  margin: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.title {
  font-size: 24px;
  font-weight: 600;
}
.card {
  padding: 22px;
  background: white;
  border: 1px solid #e8eeec;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-size: 15px;
  line-height: 1.8;
}
.subtitle {
  font-weight: 600;
  color: #167a80;
}
.error {
  color: #ae2929;
}
button {
  background: #167a80;
  color: white;
  font-size: 15px;
}
</style>
