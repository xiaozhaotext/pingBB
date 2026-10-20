<template>
  <DesktopHeader />
  <view class="container">
    <!-- 空白页 -->
    <view v-if="!hasLogin || empty === true" class="empty">
      <image src="/static/emptyCart.jpg" mode="aspectFit"></image>
      <view v-if="hasLogin" class="empty-tips">
        空空如也
        <navigator class="navigator" url="../index/index" open-type="switchTab"
          >随便逛逛></navigator
        >
      </view>
      <view v-else class="empty-tips">
        空空如也
        <view class="navigator" @click="handleNavToLogin">去登陆</view>
      </view>
    </view>
    <view v-else>
      <!-- 列表 -->
      <view class="cart-list">
        <block v-for="group in zoneGroups" :key="group.key">
          <view class="zone-group-head">
            <text class="zone-group-title">{{ group.label }}</text>
            <text class="zone-group-rule">{{ group.rule }}</text>
          </view>
          <block v-for="row in group.items" :key="row.item.id">
          <view class="cart-item" :class="{ 'b-b': row.index !== cartList.length - 1 }">
            <view class="image-wrapper" @click="handleNavToProductDetail(row.item.productId)">
              <image
                :src="productImageUrl(row.item.productPic)"
                :class="[row.item.loaded]"
                mode="aspectFill"
                lazy-load
                @load="handleImageLoad(row.index)"
                @error="handleImageError(row.index)"
              ></image>
              <view
                class="yticon icon-xuanzhong2 checkbox"
                :class="{ checked: row.item.checked }"
                @click.stop="handleCheck(row.index)"
              ></view>
            </view>
            <view class="item-right" @click="handleNavToProductDetail(row.item.productId)">
              <text class="shop">{{ group.label }} · {{ row.item.shopName }}</text>
              <text class="clamp title">{{ row.item.productName }}</text>
              <text class="clamp attr">{{ row.item.spDataStr }}</text>
              <text class="price">{{ priceLabel(row.item) }}</text>
              <uni-number-box
                class="step"
                :min="1"
                :max="100"
                :value="row.item.quantity"
                :index="row.index"
                @eventChange="handleNumberChange"
                @click.stop
              ></uni-number-box>
            </view>
            <text class="del-btn yticon icon-fork" @click.stop="handleDeleteCartItem(row.index)"></text>
          </view>
          </block>
        </block>
      </view>
      <!-- 底部菜单栏 -->
      <view class="action-section">
        <view class="checkbox">
          <image
            :src="allChecked ? '/static/selected.png' : '/static/select.png'"
            mode="aspectFit"
            @click="handleCheckAll"
          ></image>
          <view class="clear-btn" :class="{ show: allChecked }" @click="handleClearCart">清空</view>
        </view>
        <view class="total-box" @click="settlementExpanded = !settlementExpanded">
          <text class="summary-amount">已选 {{ selectedCount }} 件</text>
          <view class="detail-toggle" role="button" :aria-expanded="settlementExpanded" aria-label="展开或收起结算详情">
            <text>结算明细</text><text class="detail-arrow" :class="{ expanded: settlementExpanded }">∨</text>
          </view>
        </view>
        <view v-if="settlementExpanded" class="settlement-details">
          <text class="details-title">已选商品小计</text>
          <view class="zone-total-line" v-for="line in zoneTotals" :key="line.key">
            <text class="zone-total-name">{{ line.label }}</text>
            <text class="zone-total-value" :class="{ points: !line.cash }">{{ line.text }}</text>
          </view>
          <view v-if="hasCashZone" class="zone-total-line"><text>现金类商品小计</text><text>¥{{ totalPrice.toFixed(2) }}</text></view>
          <text class="zone-total-hint">自选区：现金或余额；复购区：复购积分抵扣后优先用余额，差额外部支付；消费区：商品积分。</text>
          <text class="zone-total-hint">此处为商品小计，实际抵扣和应付以确认订单页为准。</text>
        </view>
        <button type="primary" class="no-border confirm-btn" :disabled="checkoutNavigating" @click.stop="handleCreateOrder"
          >{{ checkoutNavigating ? '正在跳转…' : '去结算' }}</button
        >
      </view>
    </view>
  </view>
  <MainNavigation active="cart" />
</template>

<script setup lang="ts">
import { productImageUrl } from '@/utils/productImage'
import { buildCartCheckoutUrl } from '@/utils/cartCheckout'
import MainNavigation from '@/components/MainNavigation.vue'
import DesktopHeader from '@/components/DesktopHeader.vue'
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/stores/member'
import { getCartListAPI, getCartShopsAPI, deleteCartAPI, updateCartQuantityAPI, clearCartAPI } from '@/apis/cart'
import type { CartItem } from '@/types/cart'
import uniNumberBox from '@/components/uni-number-box.vue'

// ===== Store 相关 =====
// 获取会员store
const memberStore = useMemberStore()
// 是否登录
const hasLogin = computed(() => !!memberStore.memberInfo)

// ===== 页面数据 =====
// 购物车中商品总价
const totalPrice = ref(0)
// 购物车中商品是否全部选中
const allChecked = ref(false)
const settlementExpanded = ref(false)
const checkoutNavigating = ref(false)
const selectedCount = computed(() => cartList.value.filter(item => item.checked).reduce((n, item) => n + Number(item.quantity || 0), 0))
// 购物车中商品是否为空
const empty = ref(false)
// 购物车中商品列表
const cartList = ref<CartItem[]>([])
// ===== 数据加载 =====
// 加载购物车数据
const loadData = async () => {
  if (!hasLogin.value) {
    return
  }
  try {
    const res = await getCartListAPI()
    const list = res.data as CartItem[]
    cartList.value = list.map((item) => {
      item.checked = true
      item.loaded = 'loaded'
      const spDataArr = JSON.parse(item.productAttr)
      let spDataStr = ''
      for (const attr of spDataArr) {
        spDataStr += attr.key
        spDataStr += ':'
        spDataStr += attr.value
        spDataStr += ';'
      }
      item.spDataStr = spDataStr
      return item
    })
    // 展示商家名字：商家模式为平台自营 + 第三方入驻，名称来自后台数据库
    try {
      const shops = await getCartShopsAPI()
      const shopMap = new Map<number, string>(
        (shops.data || []).map((shop) => [Number(shop.productId), shop.shopName] as [number, string]),
      )
      cartList.value.forEach((item) => {
        item.shopName = shopMap.get(Number(item.productId)) || '平台自营'
      })
    } catch (e) {
      console.error('加载商家名称失败', e)
      cartList.value.forEach((item) => {
        item.shopName = item.shopName || '平台自营'
      })
    }
    calcTotal()
  } catch (e) {
    console.error('加载购物车失败', e)
  }
}

// ===== 生命周期 =====
// 监听购物车列表变化
watch(
  () => cartList.value.length,
  (len) => {
    empty.value = len === 0
  },
)

// 页面显示时调用
onShow(() => {
  checkoutNavigating.value = false
  loadData()
})

// ===== 事件处理方法 =====
// 图片加载完成
const handleImageLoad = (index: number) => {
  cartList.value[index].loaded = 'loaded'
}

// 图片加载失败
const handleImageError = (index: number) => {
  cartList.value[index].productPic = '/static/errorImage.jpg'
}

// 跳转登录
const handleNavToLogin = () => {
  uni.navigateTo({
    url: '/pages/public/login',
  })
}

// 选择商品
const handleCheck = (index: number) => {
  cartList.value[index].checked = !cartList.value[index].checked
  calcTotal()
}

// 全选商品
const handleCheckAll = () => {
  const checked = !allChecked.value
  cartList.value.forEach((item) => {
    item.checked = checked
  })
  allChecked.value = checked
  calcTotal()
}

// 修改商品数量
const handleNumberChange = async (data: { number: number; index: number }) => {
  const cartItem = cartList.value[data.index]
  try {
    await updateCartQuantityAPI({ id: cartItem.id, quantity: data.number })
    cartItem.quantity = data.number
    calcTotal()
  } catch (e) {
    console.error('更新数量失败', e)
  }
}

// 删除商品
const handleDeleteCartItem = async (index: number) => {
  const row = cartList.value[index]
  try {
    await deleteCartAPI({ ids: row.id })
    cartList.value.splice(index, 1)
    calcTotal()
    uni.hideLoading()
  } catch (e) {
    console.error('删除失败', e)
  }
}

// 跳转到商品详情
const handleNavToProductDetail = (productId: number) => {
  uni.navigateTo({
    url: `/pages/product/product?id=${productId}`,
  })
}

// 清空购物车
const handleClearCart = () => {
  uni.showModal({
    content: '清空购物车？',
    success: async (e) => {
      if (e.confirm) {
        try {
          await clearCartAPI()
          cartList.value = []
        } catch (err) {
          console.error('清空失败', err)
        }
      }
    },
  })
}

// 去结算
const handleCreateOrder = () => {
  if (checkoutNavigating.value) return
  const list = cartList.value
  const cartIds: string[] = []
  list.forEach((item) => {
    if (item.checked) {
      cartIds.push(item.id)
    }
  })
  if (cartIds.length === 0) {
    uni.showToast({
      title: '您还未选择要下单的商品！',
      icon: 'none',
      duration: 1500,
    })
    return
  }
  checkoutNavigating.value = true
  try {
    uni.navigateTo({
      url: buildCartCheckoutUrl(cartIds),
      fail: () => {
        checkoutNavigating.value = false
        uni.showToast({ title: '跳转失败，请重试', icon: 'none' })
      }
    })
  } catch (e) {
    checkoutNavigating.value = false
    uni.showToast({ title: '跳转失败，请重试', icon: 'none' })
  }
}

// ===== 其他方法 =====
// 专区隔离：不同专区按各自规则展示与汇总，不能混算
type ZoneMeta = { label: string; rule: string; cash: boolean }
const zoneMetaMap: Record<string, ZoneMeta> = {
  PICK: { label: '自选区', rule: '现金或余额购买', cash: true },
  REBUY: { label: '复购区', rule: '30%复购积分＋余款余额优先，差额外部支付', cash: true },
  CONSUME: { label: '消费区', rule: '商品积分购买', cash: false },
  NONE: { label: '普通商品', rule: '现金或余额购买', cash: true },
}
const zoneKeyOf = (item: CartItem) => {
  const code = String(item.zoneCode || '').toUpperCase()
  return code === 'PICK' || code === 'REBUY' || code === 'CONSUME' ? code : 'NONE'
}
// 按专区展示顺序分组，保持购物车商品原始索引以便沿用既有交互
const zoneGroups = computed(() =>
  ['PICK', 'REBUY', 'CONSUME', 'NONE']
    .map((key) => ({
      key,
      label: zoneMetaMap[key].label,
      rule: zoneMetaMap[key].rule,
      items: cartList.value
        .map((item, index) => ({ item, index }))
        .filter((row) => zoneKeyOf(row.item) === key),
    }))
    .filter((group) => group.items.length > 0),
)
// 已选商品的专区小计：消费区按商品积分展示，其余专区按现金展示
const zoneTotals = computed(() =>
  zoneGroups.value
    .map((group) => {
      const checked = group.items.filter((row) => row.item.checked === true)
      if (checked.length === 0) return null
      const sum = checked.reduce(
        (acc, row) => acc + (Number(row.item.price) || 0) * (Number(row.item.quantity) || 0),
        0,
      )
      const amount = Number(sum.toFixed(2))
      const cash = zoneMetaMap[group.key].cash
      return { key: group.key, label: group.label, cash, amount, text: cash ? `¥${amount}` : `${amount} 积分` }
    })
    .filter((line): line is { key: string; label: string; cash: boolean; amount: number; text: string } => line !== null),
)
const hasCashZone = computed(() => zoneTotals.value.some((line) => line.cash))
// 消费区按商品积分计价，其余专区按现金计价
const priceLabel = (item: CartItem) => (zoneKeyOf(item) === 'CONSUME' ? `${item.price} 积分` : `¥${item.price}`)
// 计算商品总价
const calcTotal = () => {
  const list = cartList.value
  if (list.length === 0) {
    empty.value = true
    totalPrice.value = 0
    allChecked.value = false
    return
  }
  let checked = true
  list.forEach((item) => {
    if (item.checked !== true) checked = false
  })
  allChecked.value = checked
  // 现金应付只汇总现金专区，消费区的商品积分不折算成现金
  totalPrice.value = Number(
    zoneTotals.value.filter((line) => line.cash).reduce((acc, line) => acc + line.amount, 0).toFixed(2),
  )
}
</script>

<style lang="scss" scoped>
.container {
  padding-bottom: calc(240rpx + env(safe-area-inset-bottom));

  .empty {
    position: fixed;
    left: 0;
    top: 0;
    width: 100%;
    height: 100vh;
    padding-bottom: 100rpx;
    display: flex;
    justify-content: center;
    flex-direction: column;
    align-items: center;
    background: #fff;

    image {
      width: 240rpx;
      height: 160rpx;
      margin-bottom: 30rpx;
    }

    .empty-tips {
      display: flex;
      font-size: 26rpx;
      color: #c0c4cc;

      .navigator {
        color: #fa436a;
        margin-left: 16rpx;
      }
    }
  }
}

.cart-item {
  display: flex;
  position: relative;
  padding: 30rpx 40rpx;

  .image-wrapper {
    width: 230rpx;
    height: 230rpx;
    flex-shrink: 0;
    width: 120rpx;
    position: relative;

    image {
      width: 100%;
      height: 100%;
      border-radius: 8rpx;
    }
  }

  .checkbox {
    position: absolute;
    left: -16rpx;
    top: -16rpx;
    z-index: 8;
    font-size: 44rpx;
    line-height: 1;
    padding: 4rpx;
    color: #c0c4cc;
    background: #fff;
    border-radius: 50px;
  }

  .item-right {
    display: flex;
    flex-direction: column;
    flex: 1;
    overflow: hidden;
    position: relative;
    padding-left: 30rpx;

    .shop {
      font-size: 24rpx;
      color: #e93b3d;
      height: 36rpx;
      line-height: 36rpx;
    }

    .title,
    .price {
      font-size: 30rpx;
      color: #303133;
      height: 40rpx;
      line-height: 40rpx;
    }

    .attr {
      font-size: 26rpx;
      color: #909399;
      height: 50rpx;
      line-height: 50rpx;
    }

    .price {
      height: 50rpx;
      line-height: 50rpx;
    }
  }

  .del-btn {
    padding: 4rpx 10rpx;
    font-size: 34rpx;
    height: 50rpx;
    color: #909399;
  }
}

.action-section {
  /* #ifdef H5 */
  margin-bottom: 100rpx;
  /* #endif */
  position: fixed;
  left: 30rpx;
  bottom: calc(30rpx + env(safe-area-inset-bottom));
  z-index: 95;
  display: flex;
  align-items: center;
  right: 30rpx;
  width: auto;
  box-sizing: border-box;
  height: 100rpx;
  padding: 0 30rpx;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 20rpx 0 rgba(0, 0, 0, 0.5);
  border-radius: 16rpx;

  .checkbox {
    height: 52rpx;
    flex-shrink: 0;
    position: relative;

    image {
      width: 52rpx;
      height: 100%;
      position: relative;
      z-index: 5;
    }
  }

  .clear-btn {
    position: absolute; left: 40rpx; top: 0; height: 52rpx; line-height: 52rpx;
    padding: 0 12rpx; font-size: 22rpx; color: #707782; white-space: nowrap; display: none;
    &.show { display: block; }
  }

  .total-box {
    flex: 1;
    display: flex;
    flex-direction: column;
    text-align: right;
    min-width: 0;
    padding: 0 16rpx;

    .price {
      font-size: 32rpx;
      color: #303133;
    }
  }

  .confirm-btn {
    padding: 0 26rpx;
    flex-shrink: 0;
    margin: 0;
    border-radius: 100px;
    height: 88rpx;
    line-height: 88rpx;
    min-width: 170rpx;
    position: relative;
    z-index: 1;
    font-size: 30rpx;
    background: #fa436a;
    box-shadow: 1px 2px 5px rgba(217, 60, 93, 0.72);
  }
}

.action-section .checkbox.checked,
.cart-item .checkbox.checked {
  color: #fa436a;
}
</style>

<style scoped>.action-section{bottom:calc(76px + env(safe-area-inset-bottom));margin-bottom:8px;z-index:98}</style>

<style scoped>
.zone-group-head{display:flex;align-items:center;gap:16rpx;padding:16rpx 30rpx;background:#f7f8f6;flex-wrap:wrap}
.zone-group-title{font-size:26rpx;font-weight:600;color:#303133}
.zone-group-rule{font-size:22rpx;color:#909399}
.summary-amount{font-size:26rpx;line-height:36rpx;color:#303133}
.detail-toggle{display:flex;justify-content:flex-end;align-items:center;gap:12rpx;font-size:24rpx;color:#707782;min-height:40rpx}
.detail-arrow{display:inline-block;transition:transform .2s;font-size:30rpx}
.detail-arrow.expanded{transform:rotate(180deg)}
.settlement-details{position:absolute;bottom:calc(100% + 12rpx);left:0;right:0;box-sizing:border-box;padding:24rpx;background:#fff;border-radius:16rpx;box-shadow:0 0 16rpx #0002;max-height:40vh;overflow-y:auto;overflow-wrap:anywhere}
.details-title{display:block;font-size:26rpx;font-weight:600;margin-bottom:12rpx}
.zone-total-line{display:flex;justify-content:space-between;gap:16rpx;font-size:24rpx;line-height:38rpx;flex-wrap:wrap}
.zone-total-name{color:#707782}
.zone-total-value{color:#303133}
.zone-total-value.points{color:#b78235;font-weight:600}
.zone-total-hint{display:block;font-size:22rpx;line-height:32rpx;color:#707782;margin-top:12rpx}
</style>


