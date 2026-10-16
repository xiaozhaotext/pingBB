import { http } from '@/utils/http'
export type MembershipTier = 'VISITOR' | 'MEMBER' | 'PARTNER' | 'COFOUNDER'
export const tierNames: Record<MembershipTier, string> = { VISITOR: '游客', MEMBER: '会员', PARTNER: '合伙人', COFOUNDER: '联创' }
export const poolNames: Record<string, string> = { PARTNER: '合伙人池', COFOUNDER: '联创池', OPERATIONS: '运营池' }
export interface MemberPool { code: string; eligible: boolean; shares: number; cap: number; received: number; remaining: number }
export interface TriSummary { enabled: boolean; tier?: MembershipTier; directPaidCount?: number; pools?: MemberPool[]; customTravelEligible?: boolean; membershipProducts?: { id: number; name: string }[] }
export interface TriRecord { id: number; pool_code: string; identity_snapshot: MembershipTier; shares: number; total_shares: number; amount: number; created_at: string; cap_amount: number; received_before: number }
export const getTriPoolsAPI = () => http<TriSummary>({ method: 'GET', url: '/member/tri-pools' })
export const getTriPoolRecordsAPI = (page: number) => http<TriRecord[]>({ method: 'GET', url: '/member/tri-pools/records', params: { page } })
