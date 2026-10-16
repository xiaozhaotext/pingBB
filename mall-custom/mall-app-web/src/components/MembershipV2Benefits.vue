<template>
  <view class="benefits">
    <text class="title">会员与晋升权益</text>
    <text v-if="error" class="error">{{ error }}</text>
    <view v-if="info" class="card"><text>当前身份：{{ tierNames[info.tier || 'VISITOR'] }}</text><text>有效直邀会员：{{ info.directPaidCount || 0 }} 人</text></view>
    <view class="card"><text class="subtitle">游客 → 会员</text><text>注册默认为游客，不享有奖励。自选区购买3999元身份牌后成为会员，获得3999复购积分、3999消费积分及旅游路线定制权益。</text><text>会员在合伙人池有本人基础1份，累计分红上限1200元。每直接邀请1位付费会员增加1份，并获1200复购积分及399.90元直邀奖励。</text></view>
    <view class="card"><text class="subtitle">会员 → 合伙人 → 联创</text><text>直邀3名付费会员后晋升合伙人；直邀9名后晋升联创。所有奖励只计直接邀请的付费会员。</text><text>合伙人池上限：0或1人1200元，2人2400元，3人8000元，4人9200元，5人10400元，6人16000元，7人17200元，8人18400元，9人及以上24000元。</text><text>联创另享联创池1份、独立累计上限1200元，并继续享有合伙人池权益。升级不重置已领取金额。</text></view>
    <view v-if="info?.tier === 'VISITOR'" class="card"><text class="subtitle">会员身份牌</text><button v-for="p in info.membershipProducts" :key="p.id" @click="openProduct(p.id)">{{ p.name }} · ¥3999</button><text v-if="!info.membershipProducts?.length">暂无已上架的会员身份牌，请联系平台。</text></view>
    <button v-if="info?.tier && info.tier !== 'VISITOR'" @click="openDividend">查看我的分红与旅游权益</button>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTriPoolsAPI, tierNames, type TriSummary } from '@/apis/triPools'
const info = ref<TriSummary | null>(null), error = ref('')
onMounted(async () => { try { info.value = (await getTriPoolsAPI()).data } catch { error.value = '权益加载失败，请返回重试' } })
const openProduct = (id: number) => uni.navigateTo({ url: `/pages/product/product?id=${id}&zone=PICK` })
const openDividend = () => uni.navigateTo({ url: '/pages/member/dividend' })
</script>
<style scoped>.benefits{max-width:800px;margin:auto;padding:24px;display:flex;flex-direction:column;gap:18px}.title{font-size:24px;font-weight:600}.card{padding:22px;background:white;border:1px solid #e8eeec;border-radius:12px;display:flex;flex-direction:column;gap:14px;font-size:15px;line-height:1.8}.subtitle{font-weight:600;color:#167a80}.error{color:#ae2929}button{background:#167a80;color:white;font-size:15px}</style>
