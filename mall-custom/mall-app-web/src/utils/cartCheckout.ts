const validCartId = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value > 0

export const buildCartCheckoutUrl = (ids: Array<string | number>): string =>
  `/pages/order/createOrder?cartIds=${encodeURIComponent(JSON.stringify(ids))}`

// uni-app 的页面参数在不同端可能已解码，也可能仍是 URL 编码。
export const parseCartIds = (raw: unknown): number[] => {
  if (typeof raw !== 'string' || !raw) return []
  let value = raw
  try {
    value = decodeURIComponent(raw)
  } catch {
    return []
  }
  try {
    const parsed: unknown = JSON.parse(value)
    if (!Array.isArray(parsed) || parsed.length === 0) return []
    const ids = parsed.map((item) => Number(item))
    return ids.every(validCartId) ? ids : []
  } catch {
    return []
  }
}

