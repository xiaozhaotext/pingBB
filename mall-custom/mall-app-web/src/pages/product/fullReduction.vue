<template>
  <view class="area">
    <view v-if="sourceOrderId" class="order-note">
      <text>当前待付款订单已生成，新增商品需要重新合并下单才能享受满减。</text>
      <text>下面按当前购物车重新计算。原订单中的商品和金额单独保留，请处理原订单后再提交新订单。</text>
      <button size="mini" @click="viewOriginalOrder">查看原订单</button>
    </view>
    <view v-if="loading" class="notice">正在核对商品、满减规则和可售库存…</view>
    <view v-else-if="error" class="notice"><text>{{ error }}</text><button @click="loadData">重新加载</button></view>
    <template v-else-if="selection && detail">
      <view class="summary">
        <text class="title">{{ detail.product.name }}</text>
        <text>同款同专区满{{ money(selection.rule.fullPrice) }}元减{{ money(selection.rule.reducePrice) }}元</text>
        <text>当前购物车已选 ¥{{ money(selection.current) }}，还差 ¥{{ money(selection.gap) }}</text>
        <text>本页仅列出库存足够凑足门槛的同款规格，凑单后按最新规则统一结算。</text>
      </view>
      <view v-if="selection.gap <= 0" class="notice">
        <text>当前购物车商品已达到该满减门槛。</text>
        <button :disabled="busy" @click="checkout(cartIds)">返回确认订单</button>
      </view>
      <view v-else-if="selection.candidates.length === 0" class="notice">当前没有库存足够凑足门槛的规格，请返回调整商品或稍后再试。</view>
      <view v-for="candidate in selection.candidates" :key="candidate.sku.id" class="candidate">
        <image :src="productImageUrl(candidate.sku.pic || detail.product.pic)" mode="aspectFill" />
        <view class="candidate-info">
          <text>{{ specification(candidate.sku.spData) }}</text>
          <text>单价 ¥{{ money(candidate.sku.price ?? detail.product.price) }}</text>
          <text>再加 {{ candidate.quantity }} 件，共 ¥{{ money(candidate.amount) }}，可达到满减门槛</text>
          <button size="mini" :disabled="busy" @click="addAndCheckout(candidate.sku.id, candidate.quantity)">{{ busy ? '正在处理…' : `加${candidate.quantity}件去结算` }}</button>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getProductDetailAPI } from '@/apis/product'
import { getCartListAPI, addCartAPI } from '@/apis/cart'
import { generateConfirmOrderAPI, getOrderDetailAPI } from '@/apis/order'
import { parseCartIds, buildCartCheckoutUrl } from '@/utils/cartCheckout'
import { fullReductionCandidates } from '@/utils/fullReduction'
import { productImageUrl } from '@/utils/productImage'
import type { PmsPortalProductDetail } from '@/types/product'

const detail = ref<PmsPortalProductDetail>()
const selection = ref<ReturnType<typeof fullReductionCandidates>>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const cartIds = ref<number[]>([])
const sourceOrderId = ref<number>()
let productId = 0
let zone = ''
let threshold = 0
let initialized = false
const money = (value: number) => Number(value).toFixed(2)
const specification = (value: string) => {
  try {
    return JSON.parse(value || '[]')
      .map((attribute: { key: string; value: string }) => `${attribute.key}：${attribute.value}`)
      .join(' / ') || '标准规格'
  } catch {
    return '标准规格'
  }
}
const viewOriginalOrder = () => uni.navigateTo({ url: `/pages/order/orderDetail?orderId=${sourceOrderId.value}` })

const loadData = async () => {
  if (busy.value) return
  loading.value = true
  error.value = ''
  selection.value = null
  try {
    if (!Number.isSafeInteger(productId) || productId <= 0 || !['NONE', 'REBUY', 'CONSUME'].includes(zone)) {
      throw Error('凑单链接无效，请从确认订单页进入')
    }
    const [product, cart] = await Promise.all([getProductDetailAPI(productId), getCartListAPI()])
    if (!product.data?.product) throw Error('商品已下架或不存在')
    detail.value = product.data
    // Include all existing cart rows for this SPU/zone so an unselected SKU is never added twice.
    const sameProductIds = cart.data
      .filter(item => Number(item.productId) === productId && (item.zoneCode || 'NONE') === zone)
      .map(item => Number(item.id))
    const liveIds = new Set(cart.data.map(item => Number(item.id)))
    cartIds.value = Array.from(new Set([...cartIds.value.filter(id => liveIds.has(id)), ...sameProductIds]))
    const items = cartIds.value.length ? (await generateConfirmOrderAPI(cartIds.value)).data.cartPromotionItemList : []
    selection.value = fullReductionCandidates(product.data, items, zone, threshold)
    if (!selection.value) throw Error('该商品当前没有可参与的满减活动')
  } catch (e: any) {
    error.value = e?.message || '凑单信息读取失败，请重试'
  } finally { loading.value = false }
}

const checkout = async (ids: number[]) => {
  if (sourceOrderId.value) {
    // An existing order is immutable. Require explicit handling before creating a replacement.
    const original = (await getOrderDetailAPI(sourceOrderId.value)).data
    if (original.status !== 4 && original.status !== 5) {
      uni.showModal({
        title: '请先处理原订单',
        content: '请先在原订单中取消待付款订单，再返回本页合并结算。如已付款，请查看原订单，勿重复购买。',
        confirmText: '查看原订单',
        success: result => { if (result.confirm) viewOriginalOrder() },
      })
      return
    }
  }
  if (!ids.length) return
  uni.redirectTo({ url: buildCartCheckoutUrl(ids) })
}

const addAndCheckout = async (skuId: number, quantity: number) => {
  if (busy.value || loading.value || error.value) return
  busy.value = true
  try {
    if (sourceOrderId.value) {
      const original = (await getOrderDetailAPI(sourceOrderId.value)).data
      if (original.status !== 4 && original.status !== 5) {
        await checkout(cartIds.value)
        return
      }
    }
    await addCartAPI({ productId, productSkuId: skuId, quantity, zoneCode: zone === 'NONE' ? undefined : zone })
    const cart = (await getCartListAPI()).data
    const added = cart.find(item => Number(item.productId) === productId && Number(item.productSkuId) === skuId && (item.zoneCode || 'NONE') === zone)
    if (!added) throw Error('已加购，请返回购物车核对后重新结算')
    cartIds.value = Array.from(new Set([...cartIds.value, Number(added.id)]))
    // Reload before checkout so retries use the new cart quantity rather than adding it again.
    busy.value = false
    await loadData()
    if (error.value) return
    if (selection.value && selection.value.gap > 0) {
      uni.showToast({ title: '商品或活动已更新，请核对最新差额后继续凑单', icon: 'none' })
      return
    }
    await checkout(cartIds.value)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '加购失败，请核对购物车后重试', icon: 'none' })
    busy.value = false
    await loadData()
  } finally { busy.value = false }
}

onLoad(async options => {
  productId = Number(options?.productId)
  zone = String(options?.zone || 'NONE')
  threshold = Number(options?.threshold)
  sourceOrderId.value = Number(options?.orderId) || undefined
  cartIds.value = parseCartIds(options?.cartIds)
  await loadData()
  initialized = true
})
onShow(() => { if (initialized) void loadData() })
</script>

<style scoped>
.area { padding: 24rpx; background: #f6f7f8; min-height: 100vh; color: #333; font-size: 28rpx; }
.summary, .notice, .candidate, .order-note { margin-bottom: 24rpx; padding: 28rpx; background: white; border-radius: 16rpx; line-height: 1.8; }
.summary text, .order-note text, .candidate-info text { display: block; }
.title { font-weight: 600; font-size: 32rpx; }
.order-note { background: #fff4df; color: #805a24; }
.candidate { display: flex; gap: 24rpx; }
.candidate image { width: 140rpx; height: 140rpx; flex-shrink: 0; }
.candidate-info { flex: 1; }
button { margin: 16rpx 0 0; background: #a7772e; color: white; }
</style>
