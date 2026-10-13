import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', () => {
  // 侧边栏状态
  const sidebar = reactive({
    opened: true,
    withoutAnimation: false,
  })
  // 设备状态
  const device = ref('desktop')
  // 系统锁屏状态：锁定后必须重新校验登录密码才能继续操作
  const locked = ref(false)

  // 锁定屏幕
  function lock() {
    locked.value = true
  }

  // 解除锁屏
  function unlock() {
    locked.value = false
  }

  // 切换侧边栏
  function toggleSideBar() {
    sidebar.opened = !sidebar.opened
  }

  // 关闭侧边栏
  function closeSideBar(withoutAnimation: boolean) {
    sidebar.opened = false
    sidebar.withoutAnimation = withoutAnimation
  }

  // 切换设备：desktop or mobile
  function toggleDevice(deviceType: string) {
    device.value = deviceType
  }

  return {
    sidebar,
    device,
    locked,
    toggleSideBar,
    closeSideBar,
    toggleDevice,
    lock,
    unlock,
  }
})
