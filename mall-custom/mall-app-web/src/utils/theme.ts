/** 商城统一暖色主题；历史 pink/green 缓存自动迁移。 */
export type AppTheme = 'warm'
export const THEME_STORAGE_KEY = 'app-theme'
export const THEMES: { key: AppTheme; label: string; primary: string }[] = [
  { key: 'warm', label: '暖橙红', primary: '#e85b36' },
]
export function getTheme(): AppTheme {
  try {
    if (uni.getStorageSync(THEME_STORAGE_KEY) !== 'warm') uni.setStorageSync(THEME_STORAGE_KEY, 'warm')
  } catch { /* 存储受限时仍使用暖色主题 */ }
  return 'warm'
}
export function setTheme(theme: AppTheme): void {
  try { uni.setStorageSync(THEME_STORAGE_KEY, theme) } catch { /* 保持本次主题 */ }
  applyTheme(theme)
}
export function applyTheme(theme: AppTheme): void {
  // #ifdef H5
  try { document.documentElement.setAttribute('data-theme', theme) } catch { /* 非文档环境 */ }
  // #endif
}
export function getThemePrimary(_theme: AppTheme): string { return '#e85b36' }
