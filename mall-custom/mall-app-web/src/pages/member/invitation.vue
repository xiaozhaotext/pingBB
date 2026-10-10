<template>
  <view class="app">
    <view class="mock-banner">邀请与推荐：邀请码自动生成，绑定关系防循环、防重复。仅记录推荐关系，不产生任何自动收益。</view>

    <view class="card">
      <text class="title">我的邀请码</text>
      <view class="code-box">
        <text class="code">{{ myCode || '—' }}</text>
        <button class="btn" size="mini" @click="copyCode">复制</button>
      </view>
      <text class="tip" v-if="detail">已邀请下级：{{ inviteeCount }} 人</text>
    </view>

    <view class="card">
      <text class="title">绑定推荐人</text>
      <input class="input" v-model="bindCode" placeholder="输入邀请码" />
      <button class="btn-primary" @click="handleBind">绑定</button>
      <text class="tip" v-if="inviter">当前推荐人：{{ inviter.inviterId || '—' }}</text>
    </view>

    <view class="card">
      <text class="title">我的下级列表</text>
      <view class="list" v-if="invitees.length">
        <view class="item" v-for="(it, i) in invitees" :key="i">
          <text>会员ID：{{ it.memberId }}</text>
          <text class="sub">绑定路径：{{ it.bindPath }}</text>
        </view>
      </view>
      <text class="tip" v-else>暂无下级</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getMyInviteCodeAPI,
  getInvitationDetailAPI,
  bindInviterAPI,
  getMyInviterAPI,
  getMyInviteesAPI,
} from '@/apis/member'

const myCode = ref('')
const detail = ref<any>(null)
const inviteeCount = ref(0)
const bindCode = ref('')
const inviter = ref<any>(null)
const invitees = ref<any[]>([])

const loadData = async () => {
  try { const r = await getMyInviteCodeAPI(); myCode.value = r.data || '' } catch (e) {}
  try { const r = await getInvitationDetailAPI(); detail.value = r.data; inviteeCount.value = r.data?.inviteeCount ?? 0 } catch (e) {}
  try { const r = await getMyInviterAPI(); inviter.value = r.data || null } catch (e) {}
  try { const r = await getMyInviteesAPI(); invitees.value = r.data || [] } catch (e) {}
}

const copyCode = () => {
  if (!myCode.value) return
  uni.setClipboardData({ data: myCode.value })
}

const handleBind = async () => {
  if (!bindCode.value) { uni.showToast({ title: '请输入邀请码', icon: 'none' }); return }
  try {
    await bindInviterAPI({ code: bindCode.value })
    uni.showToast({ title: '绑定成功', icon: 'success' })
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.mock-banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.code-box { display: flex; align-items: center; gap: 16rpx; }
.code { font-size: 40rpx; font-weight: 700; color: #285b40; letter-spacing: 4rpx; }
.input { margin: 16rpx 0; height: 80rpx; border: 1rpx solid #dcdfe6; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; }
.btn { background: #f4f4f5; color: #606266; }
.btn-primary { margin-top: 10rpx; background: #285b40; color: #fff; border-radius: 12rpx; }
.tip { display: block; margin-top: 12rpx; font-size: 24rpx; color: #909399; }
.list .item { padding: 16rpx 0; border-bottom: 1rpx solid #f2f2f2; font-size: 28rpx; color: #303133; }
.list .item .sub { display: block; font-size: 22rpx; color: #909399; margin-top: 6rpx; }
</style>
