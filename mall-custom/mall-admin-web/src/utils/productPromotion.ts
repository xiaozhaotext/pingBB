export function promotionValidation(product: any): string {
  const type = Number(product.promotionType)
  const amount = (value: unknown) => /^\d+(?:\.\d{1,2})?$/.test(String(value)) && Number(value) > 0
  if (type === 1) {
    if (!amount(product.promotionPrice) || Number(product.promotionPrice) > Number(product.price)) return '请填写有效促销价，且不能高于商品原价'
    if (product.promotionStartTime && product.promotionEndTime && new Date(product.promotionStartTime).getTime() >= new Date(product.promotionEndTime).getTime()) return '促销结束时间必须晚于开始时间'
  }
  if (type === 3) {
    const tiers = product.productLadderList || []
    if (!tiers.length || tiers.some((x: any) => !Number.isInteger(Number(x.count)) || Number(x.count) < 1 || !(Number(x.discount) > 0 && Number(x.discount) <= 1))) return '阶梯数量须为正整数，折扣填0到1之间（例如0.8表示8折）'
    if (new Set(tiers.map((x: any) => Number(x.count))).size !== tiers.length) return '阶梯数量不能重复'
  }
  if (type === 4) {
    const tiers = product.productFullReductionList || []
    if (!tiers.length || tiers.some((x: any) => !amount(x.fullPrice) || !amount(x.reducePrice) || Number(x.reducePrice) > Number(x.fullPrice))) return '满减门槛和立减金额须大于0、最多两位小数，立减金额不能超过门槛'
    if (new Set(tiers.map((x: any) => Number(x.fullPrice))).size !== tiers.length) return '满减门槛不能重复'
  }
  return ''
}
