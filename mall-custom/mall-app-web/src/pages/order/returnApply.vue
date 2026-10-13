<template>
  <view class="app">
    <view class="banner">售后申请提交后由后台人工审核；同一订单同一商品在处理中时不可重复提交。</view>

    <view class="card">
      <text class="title">选择退款商品</text>
      <view v-if="loading" class="tip">加载中…</view>
      <text v-else-if="items.length === 0" class="tip">该订单暂不支持售后或已过售后期</text>
      <view v-else>
        <view
          class="pending-item"
          :class="{ active: form.productId === Number(it.productId) }"
          v-for="it in items"
          :key="it.productId"
          @click="select(it)"
        >
          <image class="pic" :src="it.productPic" mode="aspectFill"></image>
          <view class="info">
            <text class="name">{{ it.productName }}</text>
            <text class="sub">{{ it.productAttr }}</text>
            <text class="sub">单价 ￥{{ it.productPrice }} × {{ it.productQuantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="card">
      <text class="title">退款数量</text>
      <input class="input" type="number" v-model="form.productCount" :placeholder="'最多 ' + maxCount + ' 件'" />
      <text class="title">退款原因</text>
      <view class="reasons">
        <text
          class="reason"
          v-for="r in reasons"
          :key="r"
          :class="{ active: form.reason === r }"
          @click="form.reason = r"
        >{{ r }}</text>
      </view>
      <text class="title">问题描述</text>
      <textarea class="input" v-model="form.description" maxlength="200" placeholder="请补充说明（选填）" />
    </view>

    <view class="card">
      <text class="title">联系人</text>
      <input class="input" v-model="form.returnName" placeholder="姓名" />
      <input class="input" v-model="form.returnPhone" placeholder="手机号" />
      <button class="btn-primary" :disabled="submitting" @click="submit">提交售后申请</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrderDetailAPI, createReturnApplyAPI } from '@/apis/order'

const items = ref<any[]>([])
const loading = ref(true)
const submitting = ref(false)
const reasons = ['不想要了/多拍了', '商品有损坏/缺件', '商品与描述不符', '发错货/漏发货', '其他原因']
const form = ref<{ orderId: number; productId: number | null; productCount: number | string; reason: string; description: string; returnName: string; returnPhone: string }>({
  orderId: 0,
  productId: null,
  productCount: 1,
  reason: reasons[0],
  description: '',
  returnName: '',
  returnPhone: '',
})

const current = computed(() => items.value.find((it) => Number(it.productId) === Number(form.value.productId)))
const maxCount = computed(() => (current.value ? Number(current.value.productQuantity) || 1 : 1))

const select = (it: any) => {
  form.value.productId = Number(it.productId)
  form.value.productCount = 1
}

const loadData = async (orderId: number) => {
  loading.value = true
  try {
    const r = await getOrderDetailAPI(orderId)
    const order: any = r.data || {}
    form.value.orderId = orderId
    form.value.returnName = order.receiverName || ''
    form.value.returnPhone = order.receiverPhone || ''
    items.value = (order.orderItemList || []).map((it: any) => ({ ...it }))
    if (items.value.length) select(items.value[0])
  } catch (e) {
    items.value = []
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!form.value.orderId || !form.value.productId) {
    uni.showToast({ icon: 'none', title: '请选择退款商品' })
    return
  }
  const count = Number(form.value.productCount)
  if (!count || count < 1 || count > maxCount.value) {
    uni.showToast({ icon: 'none', title: '退款数量需在 1-' + maxCount.value + ' 之间' })
    return
  }
  submitting.value = true
  try {
    await createReturnApplyAPI({
      orderId: form.value.orderId,
      productId: form.value.productId,
      productCount: count,
      reason: form.value.reason,
      description: form.value.description,
      returnName: form.value.returnName,
      returnPhone: form.value.returnPhone,
    } as any)
    uni.showToast({ icon: 'success', title: '已提交售后申请' })
    setTimeout(() => uni.navigateBack({ delta: 1 }), 800)
  } catch (e) {
    console.error('提交售后失败', e)
  } finally {
    submitting.value = false
  }
}

onLoad((options) => {
  const oid = options?.orderId ? Number(options.orderId) : 0
  if (oid) loadData(oid)
  else loading.value = false
})
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin: 10rpx 0 16rpx; }
.tip { font-size: 24rpx; color: #909399; }
.pending-item { display: flex; align-items: center; padding: 16rpx; border: 1rpx solid #eee; border-radius: 12rpx; margin-bottom: 14rpx; }
.pending-item.active { border-color: #285b40; background: #f2f8f4; }
.pending-item .pic { width: 90rpx; height: 90rpx; border-radius: 10rpx; margin-right: 16rpx; background: #f3f3f3; }
.pending-item .info { flex: 1; display: flex; flex-direction: column; }
.pending-item .name { font-size: 28rpx; color: #303133; }
.pending-item .sub { font-size: 22rpx; color: #909399; margin-top: 4rpx; }
.input { width: 100%; box-sizing: border-box; min-height: 80rpx; padding: 16rpx; background: #f7f7f7; border-radius: 12rpx; font-size: 26rpx; margin-bottom: 16rpx; }
.reasons { display: flex; flex-wrap: wrap; margin-bottom: 10rpx; }
.reasons .reason { padding: 12rpx 22rpx; margin: 0 14rpx 14rpx 0; border-radius: 100rpx; background: #f4f4f5; color: #606266; font-size: 24rpx; }
.reasons .reason.active { background: #285b40; color: #fff; }
.btn-primary { background: #285b40; color: #fff; border-radius: 12rpx; }
</style>
