import { addCartAPI, getCartProductAPI } from '@/apis/cart'
import { useMemberStore } from '@/stores/member'
import type { ZoneCode } from '@/apis/zone'

/** 未登录时引导登录，已登录返回 true */
const requireLogin = () => {
  if (useMemberStore().hasLogin) return true
  uni.showModal({
    title: '提示',
    content: '你还没登录，是否要登录？',
    confirmText: '去登录',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) uni.navigateTo({ url: '/pages/public/login' })
    },
  })
  return false
}

/**
 * 列表页快速加购：列表接口不返回 SKU，
 * 故先取商品可售规格，再按第一个有货规格加入购物车。
 */
export const addFirstAvailableSkuToCart = async (productId: number, zone?: ZoneCode) => {
  if (!requireLogin()) return
  try {
    uni.showLoading({ title: '加入中', mask: true })
    const res = await getCartProductAPI(productId)
    const skus = res.data?.skuStockList ?? []
    const available = skus.find(
      (sku) => (sku.stock ?? 0) - Math.max(sku.lockStock ?? 0, 0) > 0,
    )
    if (!available) {
      uni.hideLoading()
      uni.showToast({ icon: 'none', title: '商品库存不足' })
      return
    }
    await addCartAPI({ productId, productSkuId: available.id, quantity: 1, zoneCode: zone })
    uni.hideLoading()
    uni.showToast({ icon: 'success', title: '已加入购物车' })
  } catch (e) {
    uni.hideLoading()
    // hideLoading 会顺带关掉 http 层弹出的错误提示，这里按后端信息补一次
    const message = (e as { data?: { message?: string } })?.data?.message
    if (message) {
      setTimeout(() => uni.showToast({ icon: 'none', title: message }), 60)
    }
    console.error('加入购物车失败', e)
  }
}
