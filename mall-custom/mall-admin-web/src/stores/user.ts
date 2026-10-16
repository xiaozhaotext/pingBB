import { defineStore } from 'pinia'
import { getAdminInfoAPI, adminLoginAPI, adminLogoutAPI } from '@/apis/admin'
import { ref } from 'vue'
import type { LoginParam, UserInfo } from '@/types/admin'

const storageKey = import.meta.env.VITE_SANDBOX_MODE === 'true' ? 'sandbox-admin-user' : 'user'

// Older official builds persisted the plaintext login password in this store.
try {
  const saved = localStorage.getItem(storageKey)
  if (saved) {
    const parsed = JSON.parse(saved)
    if (parsed?.userInfo && 'password' in parsed.userInfo) {
      delete parsed.userInfo.password
      localStorage.setItem(storageKey, JSON.stringify(parsed))
    }
  }
} catch {
  // A malformed local cache must not prevent the login page from opening.
}

export const useUserStore = defineStore(
  'user',
  () => {
    // 用户信息
    const userInfo = ref<UserInfo>({
      username: '',
      avatar: '',
      roles: [],
      token: '',
      menus: [],
    })

    // 用户登录（silent=true 时不弹全局错误提示，由调用方自行处理，例如锁屏解锁）
    const userLogin = async (loginParam: LoginParam, silent = false) => {
      const res = await adminLoginAPI(loginParam, silent)
      const tokenStr = res.data.tokenHead + res.data.token
      userInfo.value.token = tokenStr
      userInfo.value.username = loginParam.username
      await getUserInfo()
    }

    // 获取用户信息
    const getUserInfo = async () => {
      const res = await getAdminInfoAPI()
      if (res.data.roles && res.data.roles.length > 0) {
        // 验证返回的roles是否是一个非空数组
        userInfo.value.roles = res.data.roles
      } else {
        throw new Error('该用户暂未分配角色，请先分配角色！')
      }
      userInfo.value.menus = res.data.menus
      userInfo.value.avatar = res.data.icon
    }

    // 用户登出
    const userLogout = async () => {
      try { await adminLogoutAPI() } catch { /* Local logout must work when the API session has expired. */ }
      finally {
        userInfo.value = { username: '', avatar: '', roles: [], token: '', menus: [] }
      }
    }

    // 前端登出
    const fedLogout = () => {
      userInfo.value = { username: '', avatar: '', roles: [], token: '', menus: [] }
    }

    return {
      userInfo,
      userLogin,
      getUserInfo,
      userLogout,
      fedLogout,
    }
  },
  {
    // 持久化配置
    persist: { key: storageKey },
  },
)
