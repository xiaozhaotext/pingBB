<script lang="ts" setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useAppStore } from '@/stores/app'
import Navbar from './components/Navbar.vue'
import Sidebar from './components/Sidebar/index.vue'
import AppMain from './components/AppMain.vue'
import LockScreen from '@/components/LockScreen/index.vue'
import useResizeHandler from './composables/useResizeHandler'

// 使用 Pinia store
const appStore = useAppStore()

// 获取响应式状态
const sidebar = computed(() => appStore.sidebar)
const device = computed(() => appStore.device)

// 计算类名
const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile'
}))

// 使用 resize handler composable
useResizeHandler()

// 空闲自动锁屏：监听用户操作，重置空闲计时
const IDLE_EVENTS = ['mousemove', 'keydown', 'mousedown', 'touchstart', 'wheel']
const onActivity = () => appStore.resetIdleTimer()
onMounted(() => {
  appStore.startIdleWatch()
  IDLE_EVENTS.forEach(e => window.addEventListener(e, onActivity, { passive: true }))
})
onUnmounted(() => {
  IDLE_EVENTS.forEach(e => window.removeEventListener(e, onActivity))
  appStore.stopIdleWatch()
})
</script>


<template>
  <div class="app-wrapper" :class="classObj">
    <Sidebar class="sidebar-container"></Sidebar>
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="appStore.closeSideBar(false)" aria-label="关闭菜单"></div>
    <div class="main-container">
      <Navbar></Navbar>
      <AppMain></AppMain>
    </div>
    <LockScreen v-if="appStore.locked"></LockScreen>
  </div>
</template>

<style lang="scss" scoped>
@use "@/styles/mixin.scss";

.app-wrapper {
  @include mixin.clearfix;
  position: relative;
  height: 100%;
  width: 100%;
}
.drawer-bg{position:fixed;inset:50px 0 0;background:rgba(0,0,0,.2);z-index:1000;touch-action:none}
</style>
