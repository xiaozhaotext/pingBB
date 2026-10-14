<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const message = ref('正在处理…')

onMounted(async () => {
  const action = (route.meta as Record<string, unknown>).desktopAction
  if (action === 'lock') {
    appStore.lock()
    await router.replace({ name: 'home' })
    return
  }
  if (action === 'logout') {
    message.value = '正在退出系统…'
    try {
      await userStore.userLogout()
    } catch {
      userStore.fedLogout()
    } finally {
      await router.replace('/login')
    }
  }
})
</script>

<template><main class="desktop-action">{{ message }}</main></template>

<style scoped>
.desktop-action { padding: 32px; color: #66736b; }
</style>
