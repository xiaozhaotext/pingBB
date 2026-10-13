import { http } from '@/utils/http'
import type { CommonPage } from '@/types/common'
import type { PmsProduct } from '@/types/product'
import type { CategoryTreeNode, ProductListParam, PmsPortalProductDetail } from '@/types/product'

/** 商品分类树 */
export const getCategoryTreeAPI = () => {
  return http<CategoryTreeNode[]>({
    method: 'GET',
    url: '/product/categoryTreeList',
  })
}

/** 商品列表搜索 */
export const searchProductListAPI = (params: ProductListParam) => {
  return http<CommonPage<PmsProduct>>({
    method: 'GET',
    url: '/product/search',
    params,
  })
}

export type ProductComment = {
  id: number
  memberNickName: string
  memberIcon: string
  star: number
  content: string
  pics?: string
  productAttribute?: string
  createTime: string
  replayCount?: number
  replays?: { id: number; content: string; memberNickName?: string; createTime?: string }[]
}

export type PendingCommentProduct = {
  orderId: number
  productId: number
  productName: string
  productPic: string
  productAttr: string
  orderSn: string
}

/** 商品评价列表（含商家回复） */
export const getProductCommentListAPI = (productId: number, pageNum = 1, pageSize = 10) => {
  return http<{ list: ProductComment[]; total: number; pageNum: number; pageSize: number; avgStar: number | null }>({
    method: 'GET',
    url: '/product/comment/list',
    params: { productId, pageNum, pageSize },
  })
}

/** 发表商品评价（需订单已完成） */
export const createProductCommentAPI = (data: {
  productId: number
  orderId: number
  star: number
  content: string
}) => {
  return http({ method: 'POST', url: '/product/comment/create', data })
}

/** 当前会员可评价的订单商品 */
export const getProductCommentPendingAPI = () => {
  return http<PendingCommentProduct[]>({ method: 'GET', url: '/product/comment/pending' })
}

/** 商品详情 */
export const getProductDetailAPI = (id: number) => {
  return http<PmsPortalProductDetail>({
    method: 'GET',
    url: `/product/detail/${id}`,
  })
}
