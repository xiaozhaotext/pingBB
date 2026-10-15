import { http } from '@/utils/http'

export interface ZoneProduct {
  id: number
  name: string
  pic: string
  subTitle: string
  price: number
  sale: number
  productCategoryId: number
}

export type ZoneCode = 'PICK' | 'REBUY' | 'CONSUME'

export const getZoneProductsAPI = (zone: ZoneCode, params: {
  keyword?: string; categoryId?: number; sort?: 'latest' | 'sale' | 'price_asc' | 'price_desc'
} = {}) =>
  http<ZoneProduct[]>({ method: 'GET', url: `/product/zone/${zone}`, params })
