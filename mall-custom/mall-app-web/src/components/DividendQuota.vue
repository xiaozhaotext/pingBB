<template>
  <view class="account-panel dividend-quota">
    <view class="quota-heading"><text>分红额度</text><text class="quota-link" @click="openRecords">明细 ›</text></view>
    <view class="quota-status"><text>{{ status }}</text><text class="quota-refresh" @click="refresh">{{ loading ? '更新中…' : '刷新' }}</text></view>
    <view class="quota-total"><text class="quota-currency">¥</text><text>{{ total('remaining') }}</text><text class="quota-caption">剩余额度</text></view>
    <view class="quota-summary"><view><text>¥{{ total('cap') }}</text><text>累计分红上限</text></view><view><text>¥{{ total('received') }}</text><text>累计已领</text></view></view>
    <view v-if="info?.enabled" class="quota-pools">
      <view v-for="pool in info.pools" :key="pool.code" class="quota-pool">
        <view class="quota-pool-heading"><text>{{ poolNames[pool.code] }}</text><text>{{ pool.eligible ? pool.shares + '份' : '未获得资格' }}</text></view>
        <view class="quota-progress"><view :style="{ width: progress(pool) + '%' }" /></view>
        <view class="quota-pool-detail"><text>已领 ¥{{ money(pool.received) }} / {{ money(pool.cap) }}</text><text>剩余 ¥{{ money(pool.remaining) }}</text></view>
      </view>
    </view>
    <view v-if="info?.tier === 'VISITOR'" class="quota-note" @click="buy">购买会员后获得分红额度，查看会员权益 ›</view>
    <view v-else class="quota-note">额度表示累计分红上限，实际到账以分红明细为准。</view>
  </view>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useMemberStore } from '@/stores/member'
import { getTriPoolsAPI, poolNames, type TriSummary, type MemberPool } from '@/apis/triPools'
const props = defineProps<{ active: boolean }>()
const member = useMemberStore(), info = ref<TriSummary | null>(null), loading = ref(false), failed = ref(false), updated = ref('')
let timer: ReturnType<typeof setInterval> | undefined, generation = 0
const money = (value: unknown) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const total = (key: 'cap' | 'received' | 'remaining') => info.value?.enabled ? money((info.value.pools || []).reduce((sum, p) => sum + Number(p[key] || 0), 0)) : '—'
const progress = (p: MemberPool) => p.cap > 0 ? Math.min(100, Math.max(0, p.received / p.cap * 100)) : 0
const status = computed(() => !member.memberInfo ? '登录后查看个人额度' : failed.value ? `更新失败${updated.value ? ' · 上次 ' + updated.value : '，请重试'}` : info.value && !info.value.enabled ? '新分红规则待启用' : updated.value ? `${updated.value} 更新 · 自动刷新` : '正在读取账户额度…')
async function refresh() {
  if (!props.active || !member.memberInfo?.id || loading.value) return
  // #ifdef H5
  if (document.hidden) return
  // #endif
  const current = generation, memberId = member.memberInfo.id
  loading.value = true
  try {
    const result = await getTriPoolsAPI()
    if (current !== generation || member.memberInfo?.id !== memberId) return
    info.value = result.data; failed.value = false
    updated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch { if (current === generation) failed.value = true }
  finally { if (current === generation) loading.value = false }
}
function stop() { if (timer) clearInterval(timer); timer = undefined; generation++; loading.value = false }
watch(() => [props.active, member.memberInfo?.id], ([active, id], old) => {
  stop()
  if (id !== old?.[1]) { info.value = null; updated.value = ''; failed.value = false }
  if (active && id) { refresh(); timer = setInterval(refresh, 30000) }
}, { immediate: true })
onBeforeUnmount(stop)
function openRecords() { uni.navigateTo({ url: member.memberInfo ? '/pages/member/dividend' : '/pages/public/login' }) }
function buy() { uni.navigateTo({ url: '/pages/member/membership' }) }
</script>
<style scoped>
.dividend-quota{box-sizing:border-box;background:linear-gradient(145deg,#fff 50%,#edf7f4)!important}.quota-heading{display:flex;justify-content:space-between;align-items:center;font-size:17px;font-weight:700;color:#284b51;border-left:3px solid #2a8a88;padding-left:8px}.quota-link{font-size:12px;color:#167a80;font-weight:400;cursor:pointer}.quota-status{display:flex;justify-content:space-between;gap:10px;color:#82989a;font-size:10px;margin:12px 0 20px}.quota-refresh{color:#167a80;white-space:nowrap;cursor:pointer}.quota-total{display:flex;align-items:baseline;flex-wrap:wrap;gap:5px;color:#176d70;font-size:32px;font-weight:700;font-variant-numeric:tabular-nums}.quota-currency{font-size:18px}.quota-caption{font-size:12px;font-weight:400;color:#7a9294;margin-left:6px}.quota-summary{display:grid;grid-template-columns:1fr 1fr;gap:15px;margin:18px 0 20px}.quota-summary>view{display:flex;flex-direction:column;gap:6px}.quota-summary>view>text:first-child{font-size:16px;font-weight:600;color:#355c60;font-variant-numeric:tabular-nums}.quota-summary>view>text:last-child{font-size:11px;color:#82989a}.quota-pools{display:flex;flex-direction:column;gap:17px;border-top:1px solid #e2ece8;padding-top:17px}.quota-pool-heading,.quota-pool-detail{display:flex;justify-content:space-between;gap:8px;font-size:12px;color:#365f61}.quota-pool-heading>text:last-child{color:#82989a;font-size:11px}.quota-progress{height:5px;background:#e3eeea;border-radius:8px;overflow:hidden;margin:9px 0}.quota-progress>view{height:100%;background:#318d87;border-radius:8px;transition:width .3s}.quota-pool-detail{font-size:10px;color:#819599;flex-wrap:wrap}.quota-pool-detail>text:last-child{color:#247e7a}.quota-note{font-size:10px;color:#819599;line-height:1.7;margin-top:17px}
</style>
