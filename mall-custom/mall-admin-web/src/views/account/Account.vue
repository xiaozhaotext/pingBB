<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAdminInfoAPI, adminLogoutAPI } from '@/apis/admin'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
const username = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const confirmation = ref('')
const loading = ref(false)
onMounted(async () => { username.value = (await getAdminInfoAPI()).data.username })
async function save() {
  if (!oldPassword.value || newPassword.value.length < 8 || newPassword.value !== confirmation.value) return ElMessage.warning('请填写原密码；新密码至少8位且两次一致')
  loading.value = true
  try {
    await http({ url: '/admin/updatePassword', method: 'post', data: {username: username.value, oldPassword:oldPassword.value, newPassword:newPassword.value} })
    ElMessage.success('密码已修改，请重新登录'); await adminLogoutAPI(); location.href = '#/login'; location.reload()
  } finally { loading.value = false }
}
</script>
<template><main class="account"><el-card shadow="never"><template #header><h2>账户安全</h2></template><p>当前平台管理员：{{ username }}</p><el-form label-position="top"><el-form-item label="原密码"><el-input v-model="oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item><el-form-item label="新密码"><el-input v-model="newPassword" type="password" show-password autocomplete="new-password" /></el-form-item><el-form-item label="再次输入新密码"><el-input v-model="confirmation" type="password" show-password autocomplete="new-password" /></el-form-item><el-button type="primary" :loading="loading" @click="save">修改密码并重新登录</el-button></el-form></el-card></main></template>
<style scoped>.account{padding:32px;max-width:660px;margin:auto}h2{color:#285a62;margin:0}p{color:#698789}</style>
