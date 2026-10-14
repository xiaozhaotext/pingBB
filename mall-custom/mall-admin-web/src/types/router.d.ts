import type { RouteMeta, RouteRecordRaw } from 'vue-router'

/** vue-router路由扩展 */
export type RouteRecordExt = RouteRecordRaw & {
  /** 前端隐藏 */
  hidden?: boolean
  /** 前端排序 */
  sort?: number
  /** 下级子路由 */
  children?: RouteRecordExt[]
  /** 是否永远显示 */
  alwaysShow?: boolean
  /** 后台总控分组定位（p0 工作台按此切换分区） */
  meta?: RouteMeta & { section?: string }
}
