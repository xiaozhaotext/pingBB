import { useUserStore } from '@/stores/user'
import type { CommonResult } from '@/types/common'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const http = axios.create({
  baseURL: import.meta.env.VITE_BASE_SERVER_URL || '/admin-api',
  timeout: 15000,
})

// axios请求拦截器
http.interceptors.request.use(
  config => {
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
        ElMessage({
          message: res.message,
          type: 'error',
          duration: 3 * 1000,
        })
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
      ElMessage({
        message: error.code === 'ECONNABORTED' ? '请求超时，请重试；登录状态已保留' : error.message,
        type: 'error',
        duration: 3 * 1000,
      })
    }
    return Promise.reject(error)
  },
)

export default http

