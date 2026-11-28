import { http } from '@/utils/http'

export interface ConsumeTheme {
  id: number
  name: string
  imageUrl: string
  description: string
}
export interface ConsumeDestination {
  id: number
  themeId: number
  name: string
  imageUrl: string
  description: string
}
export interface ConsumeRoute {
  id: number
  destinationId: number
  name: string
  imageUrl: string
  description: string
  productId: number
  productName?: string
  productPic?: string
  productSubTitle?: string
  price?: number
}

export const getConsumeThemesAPI = () =>
  http<ConsumeTheme[]>({ method: 'GET', url: '/product/consumeRoute/themes' })

export const getConsumeDestinationsAPI = (themeId: number) =>
  http<ConsumeDestination[]>({ method: 'GET', url: '/product/consumeRoute/destinations', params: { themeId } })

export const getConsumeRoutesAPI = (destinationId: number) =>
  http<ConsumeRoute[]>({ method: 'GET', url: '/product/consumeRoute/routes', params: { destinationId } })
