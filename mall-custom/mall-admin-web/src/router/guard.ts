import router from '@/router/index'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'
import usePermissionStore from '@/stores/permission'

// 无需登陆的白名单路径
const whiteList = ['/login']
// 配置路由前置守卫函数（每次路由跳转都会执行）
router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  if (userStore.userInfo.token) {
    if (to.path === '/connection-error') { next(); return }
    if (to.path === '/login') {
      // 登录状态下访问login直接跳转到首页
      next({ path: '/' })
      NProgress.done()
    } else {
      if (permissionStore.addRouters.length === 0) {
        // 旧版将菜单持久化在浏览器中；每次启动先向服务端读取当前授权，
        // 防止菜单调整后仍显示“商品”等过期入口。
        try {
          await userStore.getUserInfo()
        } catch (error) {
          const authExpired = (error as { authExpired?: boolean; response?: { status?: number } })?.authExpired === true
            || (error as { response?: { status?: number } })?.response?.status === 401
          if (authExpired) {
            userStore.fedLogout()
            next({ path: '/login', query: { redirect: to.fullPath } })
          } else {
            next({ path: '/connection-error', query: { redirect: to.fullPath } })
          }
          NProgress.done()
          return
        }
        permissionStore.generateRoutes({
          menus: userStore.userInfo.menus,
          username: userStore.userInfo.username,
        })
        permissionStore.addRouters.forEach(route => {
          router.addRoute(route)
        })
        // Resolve the original path again after adding authorized routes. Keeping
        // the temporary catch-all name would strand valid deep links on 404.
        next({ path: to.path, query: to.query, hash: to.hash, replace: true })
      } else {
        next()
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      // 未登录状态下白名单路径放行
      next()
    } else {
      // 未登录状态下非白名单路径跳转到登录页
      next('/login')
      NProgress.done()
    }
  }
})

// 配置路由后置函数守卫函数
router.afterEach(() => {
  NProgress.done()
})

