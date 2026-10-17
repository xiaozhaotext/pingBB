import { ref } from 'vue'
import { getP0, type P0Row } from '@/apis/p0'
import { getOrderDetailByIdAPI } from '@/apis/order'
import { businessSource } from '@/utils/adminDisplay'

/** Resolve existing read APIs without changing financial API responses. */
export function useLedgerDisplay() {
  const names = ref<Record<string, string>>({})
  const orders = ref<Record<string, string>>({})
  const requestedMembers = new Set<string>(), requestedOrders = new Set<string>()
  async function hydrate(rows: P0Row[], resolveOrders = false) {
    const jobs: (() => Promise<void>)[] = []
    for (const row of rows) {
      const id = String(row.member_id ?? '')
      if (/^[1-9]\d*$/.test(id) && !requestedMembers.has(id)) {
        requestedMembers.add(id)
        jobs.push(async () => {
          try {
            const result = (await getP0<{ profile: P0Row }>(`members/${id}`)).data.profile
            names.value[id] = String(result.nickname || result.username || `会员 ${id}`)
          } catch { /* Keep an explicit ID when the lookup is unavailable. */ }
        })
      }
      const orderId = String(row.biz_id ?? '')
      if (resolveOrders && ['order_pay', 'order_refund'].includes(String(row.change_type)) && /^[1-9]\d*$/.test(orderId) && !requestedOrders.has(orderId)) {
        requestedOrders.add(orderId)
        jobs.push(async () => {
          try { orders.value[orderId] = (await getOrderDetailByIdAPI(Number(orderId))).data.orderSn || '' }
          catch { /* Never invent an order number. */ }
        })
      }
    }
    let cursor = 0
    await Promise.all(Array.from({ length: Math.min(4, jobs.length) }, async () => {
      while (cursor < jobs.length) await jobs[cursor++]!()
    }))
  }
  function memberName(row: P0Row) {
    return String(names.value[String(row.member_id)] || row.nickname || row.username || `会员 ${row.member_id}`)
  }
  function source(row: P0Row) { return businessSource(row.biz_id, `${row.change_type || ''} ${row.remark || ''}`, row.order_sn || orders.value[String(row.biz_id)]) }
  return { hydrate, memberName, source }
}
