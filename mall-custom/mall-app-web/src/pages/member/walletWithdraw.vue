<template>
  <view class="withdraw-shell">
    <view class="withdraw-content">
      <view class="withdraw-modes">
        <button class="mode" :class="{ active: mode === 'earnings' }" :disabled="busy" @click="selectMode('earnings')">提现到余额</button>
        <button class="mode" :class="{ active: mode === 'balance' }" :disabled="busy" @click="selectMode('balance')">余额提现</button>
      </view>
      <view v-show="mode === 'earnings'">
        <EarningsWithdrawalPanel ref="earningsPanel" />
      </view>
      <view v-show="mode === 'balance'">
        <BalanceWithdrawalPanel ref="balancePanel" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import EarningsWithdrawalPanel from '@/components/withdrawal/EarningsWithdrawalPanel.vue'
import BalanceWithdrawalPanel from '@/components/withdrawal/BalanceWithdrawalPanel.vue'

type Mode = 'earnings' | 'balance'
type Panel = { refresh: () => Promise<void>; busy: boolean }
const mode = ref<Mode>('balance')
const earningsPanel = ref<Panel>()
const balancePanel = ref<Panel>()
const busy = computed(() => Boolean(earningsPanel.value?.busy || balancePanel.value?.busy))

function refreshActivePanel() {
  if (busy.value) return
  const panel = mode.value === 'earnings' ? earningsPanel.value : balancePanel.value
  void panel?.refresh()
}
function selectMode(next: Mode) {
  if (busy.value || mode.value === next) return
  mode.value = next
  refreshActivePanel()
}
onLoad(options => { mode.value = options?.mode === 'earnings' ? 'earnings' : 'balance' })
onShow(refreshActivePanel)
</script>

<style scoped>
.withdraw-shell { min-height: 100vh; background: #fff9f1; }
.withdraw-content { width: 100%; max-width: 760px; box-sizing: border-box; margin: 0 auto; padding: 24rpx; }
.withdraw-modes { display: flex; gap: 12rpx; padding: 8rpx; margin-bottom: 24rpx; border: 1px solid #e3e8df; border-radius: 18rpx; background: #fff; }
.mode { flex: 1; margin: 0; border-radius: 12rpx; color: #426454; background: #f4f6f2; font-size: 28rpx; line-height: 80rpx; padding: 0 12rpx; }
.mode::after { border: none; }
.mode.active { color: #fff; background: #285b45; }
.mode[disabled] { opacity: .7; }
</style>
