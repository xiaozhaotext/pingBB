<template><view class="benefits"><text class="title">历史奖励与旅游权益</text><text class="note">此处仅显示旧版权益记录。会员自定义路线请前往“定制路线”。</text><view v-if="error" @click="load">{{ error }}，点击重试</view><view v-for="row in records" :key="row.id" class="card"><text>{{ row.benefit_type === 'TRAVEL' ? '一次旅游权益' : row.benefit_period + ' 月奖励 ¥' + Number(row.amount).toFixed(2) }}</text><text>{{ labels[row.status] || row.status }}</text></view><text v-if="!error && !records.length">暂无历史权益记录。</text></view></template>
<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/http'
type Benefit = { id: number; benefit_type: string; benefit_period: string; amount: number; status: string }
const records = ref<Benefit[]>([]), error = ref('')
const labels: Record<string,string> = { PENDING: '待人工审核', APPROVED: '审核通过，已计入收益', REDEEMED: '已核销', REJECTED: '审核拒绝' }
async function load() { error.value = ''; try { records.value = (await http<Benefit[]>({ method: 'GET', url: '/member/benefits' })).data } catch { error.value = '权益记录加载失败' } }
onShow(load)
</script>
<style scoped>.benefits{padding:24px;display:flex;flex-direction:column;gap:20px}.title{font-size:24px;font-weight:700}.note{font-size:14px;color:#66756d;line-height:1.8}.card{background:white;border-radius:12px;padding:20px;display:flex;justify-content:space-between;gap:15px}</style>
