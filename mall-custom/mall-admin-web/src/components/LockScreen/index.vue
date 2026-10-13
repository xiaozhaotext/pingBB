<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const appStore = useAppStore()
const userStore = useUserStore()
const password = ref('')
const busy = ref(false)

async function unlock() {
  if (!password.value) return ElMessage.warning('请输入当前账号的登录密码')
  busy.value = true
  try {
    await userStore.userLogin({ username: userStore.userInfo.username, password: password.value })
    password.value = ''
    appStore.unlock()
    ElMessage.success('已解锁')
  } catch {
    // http.ts 已弹出后端返回的真实原因（例如“密码不正确”），保持锁定状态
  } finally {
    busy.value = false
  }
}

async function logout() {
  await userStore.userLogout()
  location.reload()
}
</script>

<template>
  <div class="lock-screen">
    <div class="panel">
      <h2>系统已锁定</h2>
      <p>当前账号：{{ userStore.userInfo.username || '未知' }}</p>
      <el-input v-model="password" type="password" show-password placeholder="输入登录密码解锁"
        autocomplete="current-password" @keyup.enter="unlock" />
      <div class="actions">
        <el-button type="primary" :loading="busy" @click="unlock">解锁</el-button>
        <el-button link @click="logout">退出登录</el-button>
      </div>
      <small>解锁会向后端重新校验密码；验证失败时页面保持锁定。</small>
    </div>
  </div>
</template>

<style scoped>
.lock-screen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(24, 39, 45, 0.92);
  backdrop-filter: blur(4px);
}
.panel {
  width: 340px;
  padding: 28px;
  border-radius: 10px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
h2 { margin: 0; color: #285a62; font-size: 20px; }
p { margin: 0; color: #698789; font-size: 13px; }
small { color: #9aa8a8; font-size: 12px; }
.actions { display: flex; align-items: center; gap: 8px; }
</style>
