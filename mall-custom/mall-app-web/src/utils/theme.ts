/**
 * 主题换肤：两套主题，用 uni.storage 持久化，刷新后保持。
 * - pink（活力粉）：与现有商品详情页粉色主色一致，为默认主题，不改变现有外观。
 * - green（清新绿）：与“我的 / 收益”页绿色主色一致。
 * 页面通过在根容器绑定 `theme-<key>` 类 + CSS 变量 var(--theme-*) 生效。
 */

export type AppTheme = 'pink' | 'green'

export const THEME_STORAGE_KEY = 'app-theme'

export const THEMES: { key: AppTheme; label: string; primary: string }[] = [
  { key: 'pink', label: '活力粉', primary: '#fa436a' },
  { key: 'green', label: '清新绿', primary: '#285b40' },
]

/** 读取已保存的主题；非法值回落默认 pink */
export function getTheme(): AppTheme {
  try {
    const saved = uni.getStorageSync(THEME_STORAGE_KEY)
    return saved === 'green' ? 'green' : 'pink'
  } catch {
    return 'pink'
  }
}

/** 保存主题并应用（H5 下同步写到 documentElement，供全局 CSS 变量使用） */
export function setTheme(theme: AppTheme): void {
  try {
    uni.setStorageSync(THEME_STORAGE_KEY, theme)
  } catch {
    /* 忽略存储异常，保持当前主题 */
  }
  applyTheme(theme)
}

/** 仅应用主题到文档根（H5）；小程序/APP 端由各页面根容器绑定 theme-* 类生效 */
export function applyTheme(theme: AppTheme): void {
  // #ifdef H5
  try {
    document.documentElement.setAttribute('data-theme', theme)
  } catch {
    /* 忽略 */
  }
  // #endif
}

/** 主色值（供 switch 等不支持 CSS 变量的组件属性使用） */
export function getThemePrimary(theme: AppTheme): string {
  return theme === 'green' ? '#285b40' : '#fa436a'
}
