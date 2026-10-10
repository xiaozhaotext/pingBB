import http from '@/utils/http'

export type P0Row = Record<string, unknown>
export type P0Data = P0Row | P0Row[]

export function getP0<T = P0Data>(path: string, params?: Record<string, unknown>) {
  return http<T>({ url: `/p0/${path}`, method: 'get', params })
}

export function postP0<T = unknown>(path: string, data: Record<string, unknown>) {
  return http<T>({ url: `/p0/${path}`, method: 'post', data })
}
