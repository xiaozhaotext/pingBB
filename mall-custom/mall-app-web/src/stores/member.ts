import type { MemberInfo } from '@/types/member'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginAPI, getMemberInfoAPI } from '@/apis/member'
import { memberStoreId, tokenStorageKey, usernameStorageKey, passwordStorageKey } from '@/utils/sessionKeys'

// 定义会员Store
export const useMemberStore = defineStore(
  memberStoreId,
  () => {
    // 会员信息
    const memberInfo = ref<MemberInfo>()

    // 是否已登录
    const hasLogin = computed(() => !!memberInfo.value)

    // 保存会员信息，登录时使用
    const setMemberInfo = (val: MemberInfo) => {
      memberInfo.value = val
    }

    // 会员登录（核心业务逻辑，不包含 UI 交互）
    const memberLogin = async (username: string, password: string) => {
      // 1. 调用登录接口获取 token
      const loginRes = await loginAPI({ username, password })

      // 2. 拼接完整 token 并存储
      const loginData = loginRes.data
      if (!loginData?.token || !loginData.tokenHead) throw new Error('登录响应缺少凭证，请重试')
      const token = `${loginData.tokenHead}${loginData.token}`
      uni.setStorageSync(tokenStorageKey, token)
      uni.setStorageSync(usernameStorageKey, username)
      uni.removeStorageSync(passwordStorageKey)

      // 3. 获取用户信息
      try {
        const memberRes = await getMemberInfoAPI()
        if (!memberRes.data?.id) throw new Error('会员资料加载失败，请重新登录')
        // 4. 保存用户信息到 Pinia
        setMemberInfo(memberRes.data)
      } catch (error) {
        // Do not leave a partial session when login succeeded but the profile request failed.
        if (uni.getStorageSync(tokenStorageKey) === token) memberLogout()
        throw error
      }
    }

    // 会员退出登录
    const memberLogout = () => {
      memberInfo.value = undefined
      uni.removeStorageSync(tokenStorageKey)
      uni.removeStorageSync(passwordStorageKey)
    }

    return {
      memberInfo,
      hasLogin,
      setMemberInfo,
      memberLogin,
      memberLogout,
    }
  },
  // TODO: 持久化
  {
    // 网页端用法
    // persist: true,
    // 兼容多端的用法
    persist: {
      storage: {
        setItem(key, value) {
          uni.setStorageSync(key, value)
        },
        getItem(key) {
          return uni.getStorageSync(key)
        },
      },
    },
  },
)
