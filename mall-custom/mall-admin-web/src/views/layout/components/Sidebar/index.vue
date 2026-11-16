<script lang="ts" setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SidebarItem from './SidebarItem.vue'
import ScrollBar from '@/components/ScrollBar/index.vue'
import { useAppStore } from '@/stores/app'
import usePermissionStore from '@/stores/permission'
import type { RouteRecordExt } from '@/types/router'

// 定义组件名称
defineOptions({
  name: 'Sidebar'
})

// 使用 Pinia stores
const appStore = useAppStore()
const permissionStore = usePermissionStore()

const route = useRoute()
// 所有路由
// 仅恢复线上侧栏分组展示，已注册路由、权限和子路由保持原样。
const routes = computed(() => {
  const hiddenSidebarNames = new Set(['systemDatabaseManagement', 'p0Database'])
  const hideDatabaseMenu = (item: RouteRecordExt): RouteRecordExt => ({
    ...item,
    children: item.children?.filter(child => !hiddenSidebarNames.has(String(child.name))).map(hideDatabaseMenu)
  }) as RouteRecordExt
  const all = (permissionStore.routers as RouteRecordExt[])
    .filter(item => !hiddenSidebarNames.has(String(item.name)))
    .map(hideDatabaseMenu)
  const byName = new Map(all.map(route => [String(route.name), route]))
  const used = new Set<string>()
  const take = (name: string) => {
    const route = byName.get(name)
    if (route) used.add(name)
    return route
  }
  const group = (name: string, title: string, icon: string, names: string[]) => {
    const children = names.map(take).filter((route): route is RouteRecordExt => !!route)
    return children.length ? [{ name, path: '', meta: { title, icon }, alwaysShow: true, children } as RouteRecordExt] : []
  }
  const ordered = [
    take('serviceDesktop'),
    take('memberMgr'),
    ...group('sidebarProducts', '商品管理', 'product', ['pick', 'rebuy', 'consume', 'columnImagesMgr']),
    take('orderMgr'),
    ...group('sidebarFinance', '财务管理', 'total-today', ['financeMgr', 'spendableWalletMgr', 'walletWithdrawal', 'withdrawalSettings', 'dividendPoolMgr']),
    take('sms'),
    ...group('sidebarServiceSystem', '服务与系统', 'ums', ['serviceMgr', 'systemSettings']),
  ].filter((route): route is RouteRecordExt => !!route)
  // 其他已授权或隐藏路由保留原有顺序。
  return [...ordered, ...all.filter(route => !used.has(String(route.name)))]
})
// 侧边栏打开状态
const isCollapse = computed(() => !appStore.sidebar.opened)
const scrollBar = ref<InstanceType<typeof ScrollBar> | null>(null)
const showActiveMenu = async () => {
  await nextTick()
  scrollBar.value?.scrollActiveIntoView()
}
onMounted(showActiveMenu)
watch(() => route.name, showActiveMenu)
</script>

<template>
  <aside class="sidebar-shell">
  <scroll-bar ref="scrollBar">
    <el-menu mode="vertical" :show-timeout="200" :default-active="String(route.name || route.path)" :collapse="isCollapse"
      background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
      <sidebar-item :routes="routes"></sidebar-item>
    </el-menu>
  </scroll-bar>
  </aside>
</template>
