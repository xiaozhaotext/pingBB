<template>
  <view v-for="offer in offers" :key="`${offer.productId}-${offer.zoneCode}`" class="full-reduction">
    <text class="product-name">{{ offer.productName }}</text>
    <text v-if="orderId" class="scope">以下为按当前活动重新凑单的参考，原订单应付金额以上方为准。</text>
    <text>满{{ money(offer.threshold) }}元减{{ money(offer.discount) }}元，当前已选{{ money(offer.currentAmount) }}元</text>
    <view class="gap-row">
      <text>还差 ¥{{ money(offer.gap) }} 达到满减</text>
      <button size="mini" @click="go(offer)">去凑单</button>
    </view>
    <text class="scope">同款商品、同专区的不同规格可合并计算</text>
  </view>
</template>

<script setup lang="ts">
import type { FullReductionOffer } from '@/types/order'
import { fullReductionUrl } from '@/utils/fullReduction'
const props = defineProps<{ offers: FullReductionOffer[]; cartIds?: number[]; orderId?: number }>()
const money = (value: number) => Number(value).toFixed(2)
const go = (offer: FullReductionOffer) => uni.navigateTo({ url: fullReductionUrl(offer, props.cartIds, props.orderId) })
</script>

<style scoped>
.full-reduction { margin: 20rpx 24rpx; padding: 24rpx; background: #fff7e8; border-radius: 16rpx; color: #7d5319; font-size: 26rpx; line-height: 1.7; }
.product-name, .scope { display: block; }
.product-name { font-weight: 600; }
.scope { font-size: 23rpx; color: #806c51; }
.gap-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin: 12rpx 0; }
.gap-row button { flex-shrink: 0; margin: 0; background: #a7772e; color: white; }
</style>
