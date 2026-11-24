/**
 * 格式化日期
 * @param date 日期对象
 * @param fmt 格式模板，如 'yyyy-MM-dd hh:mm'
 * @returns 格式化后的日期字符串
 */
export const formatDate = (date: Date, fmt: string, timeZone?: string): string => {
  let result = fmt

  const zonedParts: Record<string, number> = {}
  // 微信真机 JS 环境可能没有 Intl，商城的北京时间直接由 UTC+8 计算。
  if (timeZone === 'Asia/Shanghai') {
    const beijing = new Date(date.getTime() + 8 * 60 * 60 * 1000)
    Object.assign(zonedParts, {
      year: beijing.getUTCFullYear(), month: beijing.getUTCMonth() + 1,
      day: beijing.getUTCDate(), hour: beijing.getUTCHours(),
      minute: beijing.getUTCMinutes(), second: beijing.getUTCSeconds(),
    })
  } else if (timeZone && typeof Intl !== 'undefined' && typeof Intl.DateTimeFormat === 'function') {
    for (const part of new Intl.DateTimeFormat('en-US', {
      timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
    }).formatToParts(date)) {
      if (part.type !== 'literal') zonedParts[part.type] = Number(part.value)
    }
  }

  // 处理年份
  result = result.replace(/(y+)/, (_, yearPattern) => {
    return (String(zonedParts.year ?? date.getFullYear())).slice(4 - yearPattern.length)
  })

  const o: Record<string, number> = {
    'M+': zonedParts.month ?? date.getMonth() + 1,
    'd+': zonedParts.day ?? date.getDate(),
    'h+': zonedParts.hour ?? date.getHours(),
    'm+': zonedParts.minute ?? date.getMinutes(),
    's+': zonedParts.second ?? date.getSeconds(),
  }

  for (const [key, value] of Object.entries(o)) {
    const regex = new RegExp(`(${key})`)
    result = result.replace(regex, (_, pattern) => {
      const str = value + ''
      return pattern.length === 1 ? str : padLeftZero(str)
    })
  }

  return result
}

const padLeftZero = (str: string): string => {
  return ('00' + str).slice(str.length)
}
