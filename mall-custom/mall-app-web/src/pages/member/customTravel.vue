<template>
  <view class="page">
    <view class="card">
      <text class="title">自定义旅游路线</text>
      <text class="hint">会员可提交目的地、出行时间和同行人数，客服会根据需求联系确认。</text>
      <view v-if="!eligible" class="hint">{{ loading ? '正在核验会员身份…' : '购买会员身份牌后可提交路线需求。' }}</view>
      <template v-else>
        <text class="label">目的地</text>
        <input v-model="destination" :maxlength="100" placeholder="例如：云南大理、丽江" />
        <text class="label">计划出发日期（可选）</text>
        <picker mode="date" :start="today" :value="departureDate" @change="departureDate = String($event.detail.value)">
          <view class="picker">{{ departureDate || '请选择日期' }}</view>
        </picker>
        <text class="label">出行人数</text>
        <input v-model.number="travelerCount" type="number" placeholder="1-50 人" />
        <text class="label">路线要求（可选）</text>
        <textarea v-model="requirements" :maxlength="2000" placeholder="想去的景点、出行偏好等" />
        <button class="submit" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '提交需求' }}</button>
      </template>
    </view>
    <view v-if="eligible" class="card">
      <text class="title">我的路线需求</text>
      <text v-if="!requests.length" class="hint">暂无已提交需求</text>
      <view v-for="request in requests" :key="request.id" class="request">
        <view><strong>{{ request.destination }}</strong><text>{{ statusName(request.status) }}</text></view>
        <text>出发日期：{{ request.departure_date || '待定' }} · {{ request.traveler_count }} 人</text>
        <text v-if="request.admin_note">处理备注：{{ request.admin_note }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyDividendQuotaAPI, getMyCustomTravelRequestsAPI, submitCustomTravelRequestAPI,
  type CustomTravelRequest } from '@/apis/member'

const today = new Date().toISOString().slice(0, 10)
const loading = ref(true)
const eligible = ref(false)
const submitting = ref(false)
const destination = ref('')
const departureDate = ref('')
const travelerCount = ref(1)
const requirements = ref('')
const requests = ref<CustomTravelRequest[]>([])
const statusName = (status: string) => ({ SUBMITTED: '待处理', IN_REVIEW: '处理中', CONFIRMED: '已确认', CLOSED: '已关闭' } as Record<string, string>)[status] || status

async function load() {
  loading.value = true
  try {
    const quota = await getMyDividendQuotaAPI()
    eligible.value = Number(quota.data?.identityType || 0) > 0
    if (eligible.value) requests.value = (await getMyCustomTravelRequestsAPI()).data || []
  } catch {
    eligible.value = false
  } finally { loading.value = false }
}

async function submit() {
  const place = destination.value.trim()
  if (place.length < 2 || place.length > 100 || travelerCount.value < 1 || travelerCount.value > 50) {
    uni.showToast({ icon: 'none', title: '请填写目的地和1-50人的出行人数' })
    return
  }
  submitting.value = true
  try {
    await submitCustomTravelRequestAPI({ destination: place, departureDate: departureDate.value || undefined,
      travelerCount: Number(travelerCount.value), requirements: requirements.value.trim() })
    destination.value = ''; departureDate.value = ''; travelerCount.value = 1; requirements.value = ''
    uni.showToast({ icon: 'success', title: '需求已提交' })
    await load()
  } catch {
    uni.showToast({ icon: 'none', title: '提交失败，请稍后重试' })
  } finally { submitting.value = false }
}
onShow(load)
</script>

<style scoped>
.page{min-height:100vh;padding:24rpx;background:#f2f6f5}.card{padding:32rpx;margin-bottom:24rpx;border-radius:24rpx;background:#fff}.title{display:block;color:#28565a;font-size:34rpx;font-weight:700}.hint{display:block;margin:20rpx 0;color:#829599;font-size:24rpx;line-height:1.6}.label{display:block;margin:24rpx 0 10rpx;color:#405e62;font-size:26rpx}.card input,.card textarea,.picker{box-sizing:border-box;width:100%;min-height:76rpx;padding:18rpx;border:1rpx solid #d8e4e0;border-radius:12rpx;font-size:26rpx}.card textarea{height:180rpx}.picker{color:#405e62}.submit{margin-top:28rpx;background:#167a80;color:#fff}.request{padding:24rpx 0;border-bottom:1rpx solid #edf2f1;color:#60777a;font-size:24rpx}.request view{display:flex;justify-content:space-between;margin-bottom:12rpx;color:#28565a}.request text{display:block;line-height:1.6}
</style>
