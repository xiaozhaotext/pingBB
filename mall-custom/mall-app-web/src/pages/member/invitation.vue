<template>
  <view class="app">
    <canvas canvas-id="invitationQr" id="invitationQr" style="position:absolute;left:-10000px;top:-10000px;width:240px;height:240px" />
    <view class="mock-banner">邀请关系仅能绑定一次。直邀奖励在被邀请人完成会员购买后按当前规则入账。</view>

    <view class="card">
      <text class="title">我的邀请码</text>
      <view class="code-box">
        <text class="code">{{ myCode || '—' }}</text>
        <button class="btn" size="mini" @click="copyCode">复制</button>
      </view>
      <image v-if="qrImage" :src="qrImage" mode="aspectFit" style="width:240px;height:240px;display:block;margin:16px auto" />
      <button v-if="detail?.shortLink" class="btn" @click="copyInviteLink">复制邀请链接</button>
      <text class="tip" v-if="detail">已邀请下级：{{ inviteeCount }} 人</text>
    </view>

    <view class="card">
      <text class="title">绑定推荐人</text>
      <view v-if="bindState === 'bound'" class="bound-note">已绑定推荐人：会员 ID {{ inviter?.inviterId }}。推荐关系只能绑定一次，不能更换。</view>
      <template v-else-if="bindState === 'unbound'">
        <input class="input" v-model="bindCode" placeholder="输入邀请码" />
        <button class="btn-primary" :disabled="binding" :loading="binding" @click="handleBind">{{ binding ? '绑定中…' : '确认绑定' }}</button>
        <text v-if="bindError" class="bind-error" role="alert">{{ bindError }}</text>
        <text class="tip">绑定成功后不能修改推荐人。</text>
      </template>
      <text v-else-if="bindState === 'loading'" class="tip">正在查询推荐关系…</text>
      <view v-else><text class="tip">推荐关系加载失败，请重试后再绑定。</text><button class="btn" @click="loadInviter">重试</button></view>
    </view>

    <view class="card">
      <text class="title">我的下级列表</text>
      <view class="list" v-if="invitees.length">
        <view class="item" v-for="(it, i) in invitees" :key="i">
          <text>会员ID：{{ it.memberId }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无下级</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, getCurrentInstance } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import QRCode from 'qrcode'
import {
  getMyInviteCodeAPI,
  getInvitationDetailAPI,
  bindInviterAPI,
  getMyInviterAPI,
  getMyInviteesAPI,
} from '@/apis/member'

const qrImage = ref('')
const pageInstance = getCurrentInstance()
const renderInvitationQr = async (content: string): Promise<string> => {
  const modules = QRCode.create(content, { errorCorrectionLevel: 'M' }).modules
  const context = uni.createCanvasContext('invitationQr', pageInstance?.proxy)
  const size = 240
  const cell = size / (modules.size + 8)
  context.setFillStyle('#ffffff')
  context.fillRect(0, 0, size, size)
  context.setFillStyle('#000000')
  for (let row = 0; row < modules.size; row++) {
    for (let column = 0; column < modules.size; column++) {
      if (modules.get(row, column)) {
        const x = Math.round((column + 4) * cell)
        const y = Math.round((row + 4) * cell)
        context.fillRect(x, y, Math.round((column + 5) * cell) - x, Math.round((row + 5) * cell) - y)
      }
    }
  }
  return new Promise((resolve, reject) => {
    context.draw(false, () => {
      uni.canvasToTempFilePath({ canvasId: 'invitationQr', width: size, height: size,
        destWidth: size, destHeight: size,
        success: result => resolve(result.tempFilePath), fail: reject,
      }, pageInstance?.proxy)
    })
  })
}
const myCode = ref('')
const detail = ref<any>(null)
const copyInviteLink = () => {
  if (detail.value?.shortLink) uni.setClipboardData({ data: detail.value.shortLink })
}
const inviteeCount = ref(0)
const bindCode = ref('')
onLoad((options) => { if (options?.inviteCode) { bindCode.value = String(options.inviteCode); uni.setStorageSync('pendingInviteCode',bindCode.value) } else bindCode.value=uni.getStorageSync('pendingInviteCode')||'' })
const inviter = ref<any>(null)
const bindState = ref<'loading' | 'unbound' | 'bound' | 'error'>('loading')
const binding = ref(false)
const bindError = ref('')
const invitees = ref<any[]>([])

const loadInviter = async () => {
  bindState.value = 'loading'
  try {
    const result = await getMyInviterAPI()
    inviter.value = result.data || null
    bindState.value = inviter.value ? 'bound' : 'unbound'
    if (inviter.value) {
      bindCode.value = ''
      uni.removeStorageSync('pendingInviteCode')
    }
  } catch {
    bindState.value = 'error'
  }
}

const loadData = async () => {
  await loadInviter()
  try { const r = await getMyInviteCodeAPI(); myCode.value = r.data || '' } catch (e) {}
  try { const r = await getInvitationDetailAPI(); detail.value = r.data; if(r.data?.qrContent) qrImage.value = await renderInvitationQr(r.data.qrContent) } catch (e) {}
  try { const r = await getMyInviteesAPI(); invitees.value = r.data || []; inviteeCount.value = invitees.value.length } catch (e) { invitees.value = []; inviteeCount.value = 0 }
}

const copyCode = () => {
  if (!myCode.value) return
  uni.setClipboardData({ data: myCode.value })
}

const handleBind = async () => {
  if (bindState.value !== 'unbound' || binding.value) return
  bindError.value = ''
  if (!bindCode.value.trim()) {
    bindError.value = '请输入邀请码'
    uni.showToast({ title: bindError.value, icon: 'none' })
    return
  }
  binding.value = true
  try {
    const result = await bindInviterAPI({ code: bindCode.value.trim().toUpperCase() })
    if (!result.data?.inviterId) throw new Error('绑定结果未确认，请刷新后查看推荐人')
    inviter.value = result.data
    bindState.value = 'bound'
    bindCode.value = ''
    uni.removeStorageSync('pendingInviteCode')
    uni.showToast({ title: '绑定成功', icon: 'success' })
  } catch (e: any) {
    bindError.value = e?.data?.message || e?.message || (e?.errMsg ? '网络异常，请检查网络后重试' : '绑定失败，请稍后重试')
    uni.showModal({ title: '绑定失败', content: bindError.value, showCancel: false })
    await loadInviter()
  } finally {
    binding.value = false
  }
}

onShow(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.code-box { display: flex; align-items: center; gap: 16rpx; }
.code { font-size: 40rpx; font-weight: 700; color: #b84432; letter-spacing: 4rpx; }
.input { margin: 16rpx 0; height: 80rpx; border: 1rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; }
.btn { background: #f4f4f5; color: #606266; }
.btn-primary { margin-top: 10rpx; background: #b84432; color: #fff; border-radius: 12rpx; }
.tip { display: block; margin-top: 12rpx; font-size: 24rpx; color: #909399; }
.bound-note { margin-top: 12rpx; padding: 20rpx; border-radius: 12rpx; background: #eef7f3; color: #b84432; font-size: 26rpx; line-height: 1.6; }
.bind-error { display: block; margin-top: 12rpx; color: #b42318; font-size: 26rpx; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 28rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
</style>
