import { http } from '@/utils/http'
import type { PmsProduct, PmsProductCategory } from '@/types/product'
import type { HomeContentResult } from '@/types/home'
import type { PageParam } from '@/types/common'

/** 站点公开信息：公司资料、图片水印与合规文本（后台“平台设置”维护） */
export type SiteInfo = {
  company?: Record<string, string | null>
  watermark?: Record<string, string | null>
  contents?: Record<string, string | null>
}

export const getSiteInfoAPI = () => {
  return http<SiteInfo>({
    method: 'GET',
    url: '/home/siteInfo',
  })
}

/** 首页内容 */
export const getHomeContentAPI = () => {
  return http<HomeContentResult>({
    method: 'GET',
    url: '/home/content',
  })
}

/** 推荐商品列表 */
export const getRecommendProductListAPI = (params?: PageParam) => {
  return http<PmsProduct[]>({
    method: 'GET',
    url: '/home/recommendProductList',
    data: params,
  })
}

/** 商品分类列表 */
export const getProductCateListAPI = (parentId: string) => {
  return http<PmsProductCategory[]>({
    method: 'GET',
    url: `/home/productCateList/${parentId}`,
  })
}

/** 新鲜好物列表 */
export const getNewProductListAPI = (params?: PageParam) => {
  return http<PmsProduct[]>({
    method: 'GET',
    url: '/home/newProductList',
    data: params,
  })
}

/** 人气推荐列表 */
export const getHotProductListAPI = (params?: PageParam) => {
  return http<PmsProduct[]>({
    method: 'GET',
    url: '/home/hotProductList',
    data: params,
  })
}
