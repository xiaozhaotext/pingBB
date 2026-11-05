type Product = { price?: unknown; promotionType?: unknown; promotionPrice?: unknown; promotionStartTime?: string; promotionEndTime?: string }
type Sku = { price?: unknown; promotionPrice?: unknown }
const money = (value: unknown) => value != null && Number.isFinite(Number(value)) ? Number(value) : NaN
const date = (value: string) => new Date(value.replace(' ', 'T')).getTime()
export const promotionActive = (product: Product, now = Date.now()) =>
  (!product.promotionStartTime || date(product.promotionStartTime) <= now)
  && (!product.promotionEndTime || date(product.promotionEndTime) >= now)
export const displayedSkuPrice = (product: Product, sku: Sku, fixedRoute = false, now = Date.now()) => {
  const original = money(sku.price ?? product.price)
  const promotional = money(sku.promotionPrice ?? product.promotionPrice)
  return !fixedRoute && Number(product.promotionType) === 1 && promotionActive(product, now)
    && Number.isFinite(promotional) && promotional >= 0 && promotional <= original ? promotional : original
}
export function promotionTips(data: any, fixedRoute = false, now = Date.now()): string[] {
  if (fixedRoute) return ['3999元会员路线，不参与活动优惠']
  const product: Product = data.product || {}
  switch (Number(product.promotionType)) {
    case 1: return promotionActive(product, now) ? ['特惠促销，按所选规格活动价结算'] : ['特惠未开始或已结束，按原价结算']
    case 2: return ['会员价格尚未接入结算，当前按普通价格购买']
    case 3: {
      const tiers = (data.productLadderList || []).filter((x: any) => Number.isInteger(Number(x.count)) && x.count > 0 && x.discount > 0 && x.discount <= 1)
      return tiers.length ? [...tiers.map((x: any) => `同款同专区满${x.count}件打${Number((x.discount * 10).toFixed(2))}折`), '实际优惠在确认订单时计算'] : ['暂无有效阶梯优惠']
    }
    case 4: {
      const tiers = (data.productFullReductionList || []).filter((x: any) => x.fullPrice > 0 && x.reducePrice > 0 && Number(x.reducePrice) <= Number(x.fullPrice))
      return tiers.length ? [...tiers.map((x: any) => `同款同专区满${x.fullPrice}元减${x.reducePrice}元`), '实际优惠在确认订单时计算'] : ['暂无有效满减优惠']
    }
    default: return ['暂无优惠']
  }
}
