<template>
  <view class="travel-form">
    <text class="title">定制旅游路线</text>
    <text>填写目的地和行程偏好，工作人员将在在线咨询中回复。</text>
    <text v-if="error" class="error">{{ error }}</text>
    <template v-if="eligible">
      <input v-model="form.route" :maxlength="500" placeholder="目的地和路线，如：成都 → 九寨沟 → 成都" />
      <view>行程天数<input v-model.number="form.days" type="number" /></view>
      <view>出行人数<input v-model.number="form.people" type="number" /></view>
      <textarea v-model="form.preferences" :maxlength="2000" placeholder="景点、交通、住宿及其他偏好（选填）" />
      <input v-model="form.contact" :maxlength="128" placeholder="手机号或其他联系方式" />
      <button :disabled="busy" @click="submit">提交定制需求</button>
    </template>
  </view>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTriPoolsAPI } from '@/apis/triPools'
import { http } from '@/utils/http'
const eligible = ref(false), busy = ref(false), error = ref('')
const form = ref({ route: '', days: 3, people: 1, preferences: '', contact: '' })
onMounted(async () => {
  try { eligible.value = !!(await getTriPoolsAPI()).data.customTravelEligible; if (!eligible.value) error.value = '旅游路线定制仅限会员及以上身份' }
  catch { error.value = '会员权益加载失败，请返回重试' }
})
async function submit() {
  if (busy.value) return
  if (!form.value.route.trim() || !form.value.contact.trim()) { error.value = '请填写目的地路线和联系方式'; return }
  busy.value = true
  try {
    await http({ method: 'POST', url: '/member/tri-pools/travel', data: form.value })
    uni.redirectTo({ url: '/pages/consultation/consultation' })
  } catch { error.value = '提交未完成，请检查信息后重试' }
  finally { busy.value = false }
}
</script>
<style scoped>.travel-form{max-width:720px;margin:auto;padding:24px;display:flex;flex-direction:column;gap:18px}.title{font-size:24px;font-weight:600}input,textarea{padding:16px;background:#fff;border:1px solid #e5e7eb;border-radius:8px;box-sizing:border-box;width:100%}.error{color:#a52828}button{background:#167a80;color:white}</style>
