export function isContactHistoryEntry(url: string): boolean {
  try {
    const path = url.includes('#') ? url.split('#')[1] : new URL(url, 'https://app.local').pathname
    return (path || '').split('?')[0] === '/pages/member/contact'
  } catch { return false }
}
export function contactSaveKey(memberId: number) { return `contact-saved-return:${memberId}` }
