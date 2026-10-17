<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
function target() {
  return typeof route.query.redirect === 'string' && /^\/(?!\/)/.test(route.query.redirect)
    && !/^\/(?:login|connection-error)(?:[/?#]|$)/.test(route.query.redirect)
    ? route.query.redirect : '/home'
}
function retry() { router.replace(target()) }
function relogin() {
  userStore.fedLogout()
  router.replace({ path: '/login', query: { redirect: target() } })
}
</script>

<template>
  <main class="connection-error">
    <h1>暂时无法连接后台</h1>
    <p>加载权限信息超时或网络异常。登录状态已保留，请检查网络后重试。</p>
    <el-button type="primary" @click="retry">重试加载</el-button>
    <el-button @click="relogin">清理旧会话，重新登录</el-button>
  </main>
</template>

<style scoped>
.connection-error { margin: 15vh auto; max-width: 440px; padding: 32px; text-align: center; }
.connection-error p { color: #667085; line-height: 1.7; }
</style>

