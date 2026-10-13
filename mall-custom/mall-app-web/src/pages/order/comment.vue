<template>
  <view class="app">
    <view class="banner">仅支持对已完成订单中的商品进行评价，同一订单同一商品只可评价一次。</view>

    <view class="card">
      <text class="title">选择要评价的商品</text>
      <view v-if="loading" class="tip">加载中…</view>
      <text v-else-if="list.length === 0" class="tip">暂无可评价的订单商品</text>
      <view v-else class="pending-list">
        <view
          class="pending-item"
          :class="{ active: form.orderId === Number(p.orderId) && form.productId === Number(p.productId) }"
          v-for="p in list"
          :key="p.orderId + '-' + p.productId"
          @click="select(p)"
        >
          <image class="pic" :src="p.productPic" mode="aspectFill"></image>
          <view class="info">
            <text class="name">{{ p.productName }}</text>
            <text class="sub">{{ p.productAttr }}</text>
            <text class="sub">订单号：{{ p.orderSn }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="card">
      <text class="title">打分</text>
      <view class="star-row">
        <text class="star" :class="{ active: st <= form.star }" v-for="st in 5" :key="st" @click="form.star = st">★</text>
        <text class="star-tip">{{ form.star }} 分</text>
      </view>
      <text class="title">评价内容</text>
      <textarea class="input" v-model="form.content" maxlength="500" placeholder="说说这件商品怎么样（1-500字）" />
    </view>

    <view class="card"><button class="btn-primary" :disabled="submitting" @click="submit">提交评价</button></view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProductCommentPendingAPI, createProductCommentAPI } from '@/apis/product'

const list = ref<any[]>([])
const loading = ref(true)
const submitting = ref(false)
const form = ref<{ orderId: number | null; productId: number | null; star: number; content: string }>({
  orderId: null,
  productId: null,
  star: 5,
  content: '',
})

const select = (p: any) => {
  form.value.orderId = Number(p.orderId)
  form.value.productId = Number(p.productId)
}

const loadData = async (productId?: number, orderId?: number) => {
  loading.value = true
  try {
    const r = await getProductCommentPendingAPI()
    let all = r.data || []
    if (orderId) all = all.filter((p: any) => Number(p.orderId) === Number(orderId))
    if (productId) all = all.filter((p: any) => Number(p.productId) === Number(productId))
    list.value = all
    if (list.value.length) select(list.value[0])
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!form.value.orderId || !form.value.productId) {
    uni.showToast({ icon: 'none', title: '请选择要评价的商品' })
    return
  }
  if (!form.value.content.trim()) {
    uni.showToast({ icon: 'none', title: '请填写评价内容' })
    return
  }
  submitting.value = true
  try {
    await createProductCommentAPI({
      productId: form.value.productId,
      orderId: form.value.orderId,
      star: form.value.star,
      content: form.value.content.trim(),
    })
    uni.showToast({ icon: 'success', title: '评价成功' })
    setTimeout(() => uni.navigateBack({ delta: 1 }), 800)
  } catch (e) {
    console.error('提交评价失败', e)
  } finally {
    submitting.value = false
  }
}

onLoad((options) => {
  const pid = options?.productId ? Number(options.productId) : undefined
  const oid = options?.orderId ? Number(options.orderId) : undefined
  loadData(pid, oid)
})
</script>

<style lang="scss" scoped>
.app { padding: 20rpx; }
.banner { margin-bottom: 20rpx; padding: 20rpx; border-radius: 12rpx; background: #fff3d8; color: #805b1d; font-size: 24rpx; line-height: 1.6; }
.card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 20rpx; }
.title { font-size: 30rpx; font-weight: 600; color: #303133; display: block; margin-bottom: 16rpx; }
.tip { font-size: 24rpx; color: #909399; }
.pending-item { display: flex; align-items: center; padding: 16rpx; border: 1rpx solid #eee; border-radius: 12rpx; margin-bottom: 14rpx; }
.pending-item.active { border-color: #285b40; background: #f2f8f4; }
.pending-item .pic { width: 90rpx; height: 90rpx; border-radius: 10rpx; margin-right: 16rpx; background: #f3f3f3; }
.pending-item .info { flex: 1; display: flex; flex-direction: column; }
.pending-item .name { font-size: 28rpx; color: #303133; }
.pending-item .sub { font-size: 22rpx; color: #909399; margin-top: 4rpx; }
.star-row { display: flex; align-items: center; margin-bottom: 20rpx; }
.star-row .star { font-size: 48rpx; color: #ddd; margin-right: 14rpx; }
.star-row .star.active { color: #ffb400; }
.star-row .star-tip { font-size: 26rpx; color: #909399; margin-left: 8rpx; }
.input { width: 100%; box-sizing: border-box; min-height: 180rpx; padding: 16rpx; background: #f7f7f7; border-radius: 12rpx; font-size: 26rpx; }
.btn-primary { background: #285b40; color: #fff; border-radius: 12rpx; }
</style>
