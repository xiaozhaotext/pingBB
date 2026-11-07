import { useUserStore } from '@/stores/user'
import type { CommonResult } from '@/types/common'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// 创建axios实例
const http = axios.create({
  baseURL: import.meta.env.VITE_BASE_SERVER_URL || '/admin-api',
  timeout: 15000,
})

function escapeHtml(text: string) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 统一错误提示：短信息用轻提示，长信息（后端会逐条说明少了什么、多了什么、哪里不对）
 * 用弹窗完整显示，避免被截断或一闪而过。
 */
export function showApiError(message?: string, title = '操作没成功') {
  const text = typeof message === 'string' ? message.trim() : ''
  if (!text) {
    ElMessage({ message: '请求失败，请稍后重试', type: 'error', duration: 4000 })
    return
  }
  if (text.length <= 40 && !text.includes('\n')) {
    ElMessage({ message: text, type: 'error', duration: Math.min(8000, 3000 + text.length * 60) })
    return
  }
  ElMessageBox.alert(escapeHtml(text).replace(/\n/g, '<br>'), title, {
    dangerouslyUseHTMLString: true,
    confirmButtonText: '知道了',
    customClass: 'api-error-box',
  }).catch(() => {})
}

// axios请求拦截器
http.interceptors.request.use(
  config => {
    if (config.url?.startsWith('/productCategory')) {
      const segment = window.location.hash.split('/')[1] || ''
      const zone = ({ pick: 'PICK', rebuy: 'REBUY', consume: 'CONSUME' } as Record<string, string>)[segment]
      if (zone) config.params = { ...config.params, zone }
    }
    //从pinia获取token
    const userStore = useUserStore()
    const token = userStore.userInfo.token
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  e => Promise.reject(e),
)

// axios响应拦截器
http.interceptors.response.use(
  response => {
    const res: CommonResult<unknown> = response.data
    if (res.code !== 200) {
      // silent=true 时由调用方自行处理错误提示，不弹全局错误（例如锁屏解锁，避免透露后端错误细节）
      const silent = (response.config as unknown as { silent?: boolean }).silent === true
      if (!silent) {
        // code为非200是抛错，这里统一处理提示信息
        showApiError(res.message)
      }
      return Promise.reject({ code: res.code, message: res.message, authExpired: res.code === 401 })
    } else {
      // 返回响应JSON中的data属性，不包括message和code
      return response.data
    }
  },
  error => {
    // 全局处理异常请求
    const silent = (error?.config as unknown as { silent?: boolean } | undefined)?.silent === true
    if (!silent) {
      const serverMessage = error?.response?.data?.message
      if (error.code === 'ECONNABORTED') {
        ElMessage({ message: '请求超时，请重试；登录状态已保留', type: 'error', duration: 5000 })
      } else if (typeof serverMessage === 'string' && serverMessage) {
        showApiError(serverMessage, `请求未通过（${error?.response?.status ?? ''}）`.replace('（）', ''))
      } else {
        showApiError(error.message)
      }
    }
    return Promise.reject(error)
  },
)

export default http
