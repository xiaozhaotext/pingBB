import { http } from '@/utils/http'
import type { CartItem, CartProduct } from '@/types/cart'

/** 添加购物车 */
export const addCartAPI = (data: Partial<CartItem>) => {
  return http({
    method: 'POST',
    url: '/cart/add',
    data,
  })
}

/** 获取购物车列表 */
export const getCartListAPI = () => {
  return http<CartItem[]>({
    method: 'GET',
    url: '/cart/list',
  })
}

/** 获取购物车中指定商品的规格（用于列表页快速加购） */
export const getCartProductAPI = (productId: number) => {
  return http<CartProduct>({
    method: 'GET',
    url: `/cart/getProduct/${productId}`,
  })
}

/** 获取购物车中各商品所属商家名称 */
export const getCartShopsAPI = () => {
  return http<{ productId: number; shopName: string }[]>({
    method: 'GET',
    url: '/cart/shops',
  })
}

/** 删除购物车商品 */
export const deleteCartAPI = (params: { ids: string }) => {
  return http({
    method: 'POST',
    url: '/cart/delete',
    params: params,
  })
}

/** 更新商品数量 */
export const updateCartQuantityAPI = (data: { id: string; quantity: number }) => {
  return http({
    method: 'POST',
    url: `/cart/update/quantity?id=${data.id}&quantity=${data.quantity}`,
  })
}

/** 清空购物车 */
export const clearCartAPI = () => {
  return http({
    method: 'POST',
    url: '/cart/clear',
  })
}
