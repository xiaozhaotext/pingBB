import { onMounted, onBeforeMount, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'

const WIDTH = 1024

export default function useResizeHandler() {
  const route = useRoute()
  const appStore = useAppStore()

  // 监听路由变化
  watch(
    () => route.path,
    () => {
      if (appStore.device === 'mobile' && appStore.sidebar.opened) {
        appStore.closeSideBar(false)
      }
    },
  )

  // 判断是否为移动端访问
  const isMobile = () => {
    return window.innerWidth < WIDTH
  }

  const resizeHandler = () => {
    if (!document.hidden) {
      const mobile = isMobile()
      const changed = appStore.device !== (mobile ? 'mobile' : 'desktop')
      appStore.toggleDevice(mobile ? 'mobile' : 'desktop')
      // Mobile browser chrome changes viewport height during scrolling. Only a
      // real breakpoint transition should close an already open drawer.
      if (mobile && changed) {
        appStore.closeSideBar(true)
      }
    }
  }

  // 在组件挂在之前调用
  onBeforeMount(() => {
    window.addEventListener('resize', resizeHandler)
  })

  // 在组件挂在之后调用
  onMounted(() => {
    const mobile = isMobile()
    appStore.toggleDevice(mobile ? 'mobile' : 'desktop')
    if (mobile) {
      appStore.closeSideBar(true)
    }
  })

  // 在组件卸载之前调用
  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeHandler)
  })

  return {
    isMobile,
    resizeHandler,
  }
}
