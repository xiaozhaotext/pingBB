import { http } from '@/utils/http'

export interface ActivityProduct { id: number; name: string; pic: string; price: number }
export interface PickActivity {
  id: number
  title: string
  imageUrl: string
  rules: string
  products?: ActivityProduct[]
}
export const getPickActivitiesAPI = () => http<PickActivity[]>({ method: 'GET', url: '/product/pickActivity' })
export const getPickActivityAPI = (id: number) => http<PickActivity>({ method: 'GET', url: `/product/pickActivity/${id}` })
