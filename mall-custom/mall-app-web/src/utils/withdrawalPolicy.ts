export interface WithdrawalPolicy { mode: 'HUNDRED' | 'NONE' | 'CUSTOM'; minimum: number; maximum: number; feeRate: number }
export function withdrawalError(value: string, policy: WithdrawalPolicy): string {
  if (!/^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/.test(value) || Number(value) <= 0) return '请输入大于0、最多两位小数的提现金额'
  const cents = Math.round(Number(value) * 100)
  if (cents < Math.round(Number(policy.minimum) * 100)) return `当前提现门槛为${Number(policy.minimum)}元，提现金额未达到门槛`
  if (policy.mode === 'HUNDRED' && cents % 10000 !== 0) return '当前提现门槛为100元，金额须为100元的整数倍'
  if (cents > Number(policy.maximum) * 100) return `单笔提现金额不得超过${policy.maximum}元`
  return ''
}
export function showWithdrawalError(content: string) { uni.showModal({ title: '提现提示', content, showCancel: false }) }
