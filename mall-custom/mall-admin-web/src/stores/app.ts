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

  // 空闲自动锁屏：超时分钟数，存 localStorage，默认 30 分钟，可在导航栏“锁屏设置”中修改
  const IDLE_KEY = 'admin_lock_idle_minutes'
  const DEFAULT_IDLE_MINUTES = 30
  const idleTimeoutMinutes = ref(DEFAULT_IDLE_MINUTES)
  try {
    const saved = Number(localStorage.getItem(IDLE_KEY))
    if (Number.isFinite(saved) && saved >= 5 && saved <= 120) {
      idleTimeoutMinutes.value = saved
    }
  } catch {
    // localStorage 不可用时使用默认值
  }

  // 设置空闲锁屏超时（分钟，5~120）
  function setIdleTimeout(minutes: number) {
    const v = Math.round(minutes)
    if (!Number.isFinite(v) || v < 5 || v > 120) return
    idleTimeoutMinutes.value = v
    try {
      localStorage.setItem(IDLE_KEY, String(v))
    } catch {
      // 忽略存储失败
    }
    resetIdleTimer()
  }

  // 空闲检测：每 30 秒检查一次，超过超时且未锁定时自动锁屏
  let idleTimer: ReturnType<typeof setInterval> | null = null
  let lastActivity = Date.now()

  function resetIdleTimer() {
    lastActivity = Date.now()
  }

  function startIdleWatch() {
    resetIdleTimer()
    if (idleTimer) return
    idleTimer = setInterval(() => {
      if (!locked.value && Date.now() - lastActivity > idleTimeoutMinutes.value * 60 * 1000) {
        lock()
      }
    }, 30 * 1000)
  }

  function stopIdleWatch() {
    if (idleTimer) {
      clearInterval(idleTimer)
      idleTimer = null
    }
  }

  // 锁定屏幕
  function lock() {
    locked.value = true
  }

  // 解除锁屏（同时重置空闲计时，避免刚解锁又被锁）
  function unlock() {
    locked.value = false
    resetIdleTimer()
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
    idleTimeoutMinutes,
    setIdleTimeout,
    resetIdleTimer,
    startIdleWatch,
    stopIdleWatch,
    toggleSideBar,
    closeSideBar,
    toggleDevice,
    lock,
    unlock,
  }
})
