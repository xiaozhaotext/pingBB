<template>
  <view class="opening">正在打开提现页面…</view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'

onLoad(() => {
  uni.redirectTo({ url: '/pages/member/walletWithdraw?mode=earnings' })
})
</script>

<style scoped>
.opening { padding: 40rpx; text-align: center; color: #8d6655; }
</style>
