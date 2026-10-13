<template>
  <view class="container">
    <view class="left-bottom-sign"></view>
    <view class="back-btn yticon icon-zuojiantou-up" @click="navBack"></view>
    <view class="right-top-sign"></view>

    <!-- 注册表单 -->
    <view class="wrapper">
      <view class="left-top-sign">REGISTER</view>
      <view class="welcome">注册账号！</view>
      <view class="registration-note">当前是功能测试环境；验证码为演示验证码，不会发送真实短信。</view>
      <view v-if="demoCode" class="registration-note">本次演示验证码：{{ demoCode }}</view>
      <view v-if="registrationError" class="registration-error">{{ registrationError }}</view>
      <view class="input-content">
        <view class="input-item">
          <text class="tit">用户名</text>
          <input
            type="text"
            v-model="formData.username" @blur="formData.username = $event.detail.value"
            placeholder="请输入用户名"
            :maxlength="64"
          />
        </view>
        <view class="input-item">
          <text class="tit">手机号</text>
          <input
            type="number"
            v-model="formData.telephone" @blur="formData.telephone = $event.detail.value"
            placeholder="请输入手机号"
            :maxlength="11"
          />
        </view>
        <view class="input-item">
          <text class="tit">密码</text>
          <input
            v-model="formData.password" @blur="formData.password = $event.detail.value"
            placeholder="设置至少8位的登录密码"
            placeholder-class="input-empty"
            :maxlength="72"
            password
          />
        </view>
        <view class="input-item">
          <text class="tit">验证码</text>
          <view class="auth-code-row">
            <input
              type="text"
              v-model="formData.authCode" @blur="formData.authCode = $event.detail.value"
              placeholder="请输入验证码"
              :maxlength="6"
            />
            <button
              class="get-code-btn"
              :class="{ disabled: countdown > 0 }"
              :disabled="countdown > 0"
              @click="handleGetAuthCode"
            >
              {{ countdown > 0 ? `${countdown}s后重试` : '获取验证码' }}
            </button>
          </view>
        </view>
      </view>
      <button class="confirm-btn" @click="handleRegister" :disabled="registering"> 注册 </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { registerAPI, getAuthCodeAPI } from '@/apis/member'
import type { RegisterParam } from '@/types/member'

// ===== 页面数据 =====
// 注册表单数据
const formData = ref<RegisterParam>({
  username: '',
  password: '',
  telephone: '',
  authCode: '',
})
// 注册加载状态
const registering = ref(false)
const demoCode = ref('')
const registrationError = ref('')
function errorMessage(error: unknown): string {
  const value=error as {data?:{message?:string};errMsg?:string;message?:string}
  return value?.data?.message || value?.message || (value?.errMsg ? '网络连接失败，请重试' : '请求失败，请重试')
}
// 验证码倒计时（秒）
const countdown = ref(0)
// 倒计时定时器
let countdownTimer: ReturnType<typeof setInterval> | null = null

// 获取验证码
const handleGetAuthCode = async () => {
  if (!formData.value.telephone) {
    uni.showToast({
      title: '请输入手机号',
      icon: 'none',
    })
    return
  }
  if (!/^1[3-9]\d{9}$/.test(formData.value.telephone)) {
    uni.showToast({
      title: '手机号格式不正确',
      icon: 'none',
    })
    return
  }

  try {
    const res = await getAuthCodeAPI(formData.value.telephone)
    // 模拟验证码展示：弹出5秒toast让用户获取验证码
    const code = res.data || '验证码已发送'
    demoCode.value = /^\d{6}$/.test(String(res.data)) ? String(res.data) : ''
    registrationError.value = ''
    uni.showToast({
      title: `验证码：${code}`,
      icon: 'none',
      duration: 5000,
    })
    // 开始60秒倒计时
    startCountdown()
<<<<<<< HEAD
  } catch (error) {
    registrationError.value = errorMessage(error)
    uni.showToast({
      title: registrationError.value,
      icon: 'none',
    })
=======
  } catch (e) {
    // http 工具已按后端 message 弹过提示，这里不再重复
    console.error('获取验证码失败', e)
>>>>>>> origin/main
  }
}

// 注册
const handleRegister = async () => {
  const { username, telephone, password, authCode } = formData.value
  if (!username) {
    uni.showToast({ title: '请输入用户名', icon: 'none' })
    return
  }
  if (!telephone) {
    uni.showToast({ title: '请输入手机号', icon: 'none' })
    return
  }
  if (!password) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }
  if (!authCode) {
    uni.showToast({ title: '请输入验证码', icon: 'none' })
    return
  }

  if (!/^1[3-9]\d{9}$/.test(telephone)) { registrationError.value='手机号格式不正确'; return }
  if (password.length<8) { registrationError.value='密码至少8位'; return }
  if (!/^\d{6}$/.test(authCode)) { registrationError.value='请输入6位验证码'; return }
  registrationError.value=''
  registering.value = true
  try {
    await registerAPI(formData.value)
    uni.showToast({
      title: '注册成功',
      icon: 'success',
    })
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/public/login' })
    }, 1500)
  } catch (e: any) {
    // http 工具已按后端 message 弹过提示，这里不再重复
    console.error('注册失败', e)
  } finally {
    registering.value = false
  }
}

// ===== 其他方法 =====
// 开始60秒倒计时
const startCountdown = () => {
  countdown.value = 60
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      countdown.value = 0
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
    }
  }, 1000)
}

// 返回上一页
const navBack = () => {
  uni.navigateBack()
}
</script>

<style lang="scss">
page {
  background: #fff;
}
</style>

<style lang="scss" scoped>
.container {
  padding-top: 115px;
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #fff;
}

.wrapper {
  position: relative;
  z-index: 90;
  background: #fff;
  padding-bottom: 40rpx;
}

.back-btn {
  position: absolute;
  left: 40rpx;
  z-index: 9999;
  top: calc(var(--status-bar-height) + 40rpx);
  font-size: 40rpx;
  color: $font-color-dark;
}

.left-top-sign {
  font-size: 120rpx;
  color: $page-color-base;
  position: relative;
  left: -16rpx;
}

.right-top-sign {
  position: absolute;
  top: calc(var(--status-bar-height) + 80rpx);
  right: -30rpx;
  z-index: 95;

  &:before,
  &:after {
    display: block;
    content: '';
    width: 400rpx;
    height: 80rpx;
    background: #b4f3e2;
  }

  &:before {
    transform: rotate(50deg);
    border-radius: 0 50px 0 0;
  }

  &:after {
    position: absolute;
    right: -198rpx;
    top: 0;
    transform: rotate(-50deg);
    border-radius: 50px 0 0 0;
  }
}

.left-bottom-sign {
  position: absolute;
  left: -270rpx;
  bottom: -320rpx;
  border: 100rpx solid #d0d1fd;
  border-radius: 50%;
  padding: 180rpx;
}

.welcome {
  position: relative;
  left: 50rpx;
  top: -90rpx;
  font-size: 46rpx;
  color: #555;
  text-shadow: 1px 0px 1px rgba(0, 0, 0, 0.3);
}

.empty {
  position: fixed;
  left: 0;
  top: 0;
  width: 100%;
  height: 100vh;
  padding-bottom: 100rpx;
  display: flex;
  justify-content: center;
  flex-direction: column;
  align-items: center;
  background: #fff;

  image {
    width: 420rpx;
    height: 420rpx;
    margin-bottom: 30rpx;
  }

  .empty-tips {
    display: flex;
    font-size: 28rpx;
    color: $font-color-disabled;

    .navigator {
      color: $uni-color-primary;
      margin-left: 0rpx;
    }
  }
}

.input-content {
  padding: 0 60rpx;
}

.input-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 0 30rpx;
  background: $page-color-light;
  height: 120rpx;
  border-radius: 4px;
  margin-bottom: 50rpx;

  &:last-child {
    margin-bottom: 0;
  }

  .tit {
    height: 50rpx;
    line-height: 56rpx;
    font-size: $font-sm + 2rpx;
    color: $font-color-base;
  }

  input {
    height: 60rpx;
    font-size: $font-base + 2rpx;
    color: $font-color-dark;
    width: 100%;
  }
}

.auth-code-row {
  display: flex;
  align-items: center;
  width: 100%;

  input {
    flex: 1;
  }

  .get-code-btn {
    flex-shrink: 0;
    width: 200rpx;
    height: 60rpx;
    line-height: 60rpx;
    font-size: 24rpx;
    color: $uni-color-primary;
    background: #fff;
    border: 1rpx solid $uni-color-primary;
    border-radius: 8rpx;
    padding: 0;
    margin: 0;
    margin-left: 20rpx;

    &::after {
      border: none;
    }

    &.disabled {
      color: $font-color-disabled;
      border-color: $font-color-disabled;
    }
  }
}

.confirm-btn {
  width: 630rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 50px;
  margin-top: 70rpx;
  background: $uni-color-primary;
  color: #fff;
  font-size: $font-lg;

  &:after {
    border-radius: 100px;
  }
}
</style>

<style scoped>.registration-note{font-size:12px;color:#557f87;line-height:1.6;margin:12px 24px}.registration-error{font-size:14px;color:#b84242;background:#fff0f0;padding:12px;margin:12px 24px;border-radius:8px}</style>
