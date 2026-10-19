<script lang="ts" setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user';
import { isvalidUsername } from '@/utils/validate';
import { type FormInstance, type FormRules } from 'element-plus';

// 使用Vue Router和Pinia
const router = useRouter()
const userStore = useUserStore()

// 表单引用
const loginFormRef = ref<FormInstance>()

// 登陆表单数据
const loginForm = reactive({
  username: '',
  password: '',
})

// 表单参数校验规则
const loginRules = reactive<FormRules<typeof loginForm>>({
  username: [{ required: true, trigger: 'blur', validator: validateUsername }],
  password: [{ required: true, trigger: 'blur', validator: validatePass }]
})

// 登陆按钮进度条
const loading = ref(false)

// 用户名验证函数
function validateUsername(rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!isvalidUsername(value)) {
    callback(new Error('请输入正确的用户名'))
  } else {
    callback()
  }
};

// 密码验证函数
function validatePass(rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value.length < 3) {
    callback(new Error('密码不能小于3位'))
  } else {
    callback()
  }
};

// 组件挂载完成后调用
onMounted(() => {
  loginForm.username = userStore.userInfo.username
})

// 处理登录按钮事件
const handleLogin = () => {
  loginFormRef.value!.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.userLogin({
          username: loginForm.username.trim(),
          password: loginForm.password
        })
        loading.value = false
        router.push({ path: '/' })
      }
      catch (err) {
        loading.value = false
        console.log(err)
      }
    } else {
      console.log('参数验证不合法！')
    }
  })
}
</script>

<template>
  <div class="login-page">
    <div class="login-intro">
      <span class="login-eyebrow">智游美 · 管理后台</span>
      <h1>山水相伴<br>经营有序</h1>
      <p>在旅途与生活之间，管理每一份美好。</p>
    </div>
    <el-card class="login-form-layout">
      <el-form autoComplete="on" :model="loginForm" :rules="loginRules" ref="loginFormRef" label-position="left">
        <div style="text-align: center">
          <img src="/youxuan.svg" width="56" height="56" alt="智游美" />
        </div>
        <h2 class="login-title color-main">智游美管理后台</h2>
        <el-form-item prop="username">
          <el-input name="username" type="text" v-model="loginForm.username" autoComplete="on" placeholder="请输入用户名">
            <template #prefix>
              <span>
                <svg-icon icon-class="user" class="color-main"></svg-icon>
              </span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input name="password" @keyup.enter="handleLogin" v-model="loginForm.password" autoComplete="on"
            show-password placeholder="请输入密码">
            <template #prefix>
              <span>
                <svg-icon icon-class="password" class="color-main"></svg-icon>
              </span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item style="margin-bottom: 60px;text-align: center">
          <el-button style="width: 100%" type="primary" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7vw;
  padding: 40px;
  background: linear-gradient(90deg,#102b38b8,#18374120), url('../../../assets/images/travel-photo/alpine-lake.jpg') center / cover;
  box-sizing: border-box;
}
.login-intro { max-width: 440px; color: #fff; text-shadow: 0 2px 18px #112c3870; }
.login-eyebrow { font-size: 14px; letter-spacing: 3px; font-weight: 700; }
.login-intro h1 { font-size: 48px; line-height: 1.22; margin: 22px 0; }
.login-intro p { color: #e7ebdf; font-size: 17px; }
.login-form-layout {
  width: 390px;
  border: 1px solid #fff9e2;
  border-radius: 22px;
  border-top: 4px solid #baa16c; background: #fffffff2;
  box-shadow: 0 24px 70px #102b3859;
}

.login-title {
  text-align: center;
}

@media (max-width: 760px) {
  .login-page { flex-direction: column; gap: 12px; padding: 24px; }
  .login-intro { text-align: center; }
  .login-intro h1 { font-size: 30px; margin: 12px 0; }
  .login-form-layout { width: min(390px, 100%); }
}
</style>
