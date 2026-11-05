import { http } from '@/utils/http'

export type Notice = {
  id: number
  title: string
  content: string
  type: string
  publish_time: string
  is_read?: number
}

export const getAnnouncementsAPI = () => http<Notice[]>({ method: 'GET', url: '/home/announcements' })
export const getAnnouncementDetailAPI = (id: number) => http<Notice>({ method: 'GET', url: `/home/announcements/${id}` })
export const getNoticesAPI = () => http<Notice[]>({ method: 'GET', url: '/member/notice/list' })
export const readNoticeAPI = (id: number) => http({ method: 'POST', url: '/member/notice/read', data: { noticeId: id } })
