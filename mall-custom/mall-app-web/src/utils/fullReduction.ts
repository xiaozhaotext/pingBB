import type { FullReductionOffer, CartPromotionItem } from '@/types/order'
import type { PmsPortalProductDetail } from '@/types/product'

export const cents = (value: unknown) => Math.round(Number(value) * 100)

export function fullReductionOffers(items: CartPromotionItem[]): FullReductionOffer[] {
  const offers = new Map<string, FullReductionOffer>()
  for (const item of items) {
    const offer = item.fullReductionOffer
    if (offer && Number(offer.gap) > 0 && offer.zoneCode !== 'PICK') {
      offers.set(`${offer.productId}:${offer.zoneCode}`, offer)
    }
  }
  return Array.from(offers.values())
}

export function fullReductionUrl(offer: FullReductionOffer, cartIds: number[] = [], orderId?: number) {
  return `/pages/product/fullReduction?productId=${offer.productId}&zone=${encodeURIComponent(offer.zoneCode)}`
    + `&threshold=${offer.threshold}&cartIds=${encodeURIComponent(JSON.stringify(cartIds))}`
    + (orderId ? `&orderId=${orderId}` : '')
}

/** The area lists only SKUs of the same SPU with enough available stock to reach the selected tier. */
export function fullReductionCandidates(detail: PmsPortalProductDetail, items: CartPromotionItem[], zone: string, target: number) {
  if (zone === 'PICK' || Number(detail.product.promotionType) !== 4) return null
  const rules = (detail.productFullReductionList || []).filter(rule =>
    cents(rule.fullPrice) > 0 && cents(rule.reducePrice) > 0 && cents(rule.reducePrice) <= cents(rule.fullPrice))
    .sort((a, b) => cents(a.fullPrice) - cents(b.fullPrice) || cents(b.reducePrice) - cents(a.reducePrice))
  const rule = rules.find(rule => cents(rule.fullPrice) === cents(target)) || rules[0]
  if (!rule) return null
  const sameItems = items.filter(item => Number(item.productId) === Number(detail.product.id) && (item.zoneCode || 'NONE') === zone)
  const current = sameItems.reduce((sum, item) => sum + cents(item.price) * item.quantity, 0)
  const gap = Math.max(0, cents(rule.fullPrice) - current)
  const candidates = (detail.skuStockList || []).flatMap(sku => {
    const price = cents(sku.price ?? detail.product.price)
    const quantity = price > 0 ? Math.ceil(gap / price) : 0
    const existing = sameItems.filter(item => Number(item.productSkuId) === Number(sku.id))
      .reduce((sum, item) => sum + item.quantity, 0)
    const available = Math.max(0, Number(sku.stock || 0) - Number(sku.lockStock || 0))
    return quantity > 0 && quantity <= 99 && existing + quantity <= Math.min(99, available)
      ? [{ sku, quantity, amount: price * quantity / 100 }] : []
  })
  return { rule, current: current / 100, gap: gap / 100, candidates }
}
