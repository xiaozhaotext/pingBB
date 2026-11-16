<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getP0 } from '@/apis/p0'
type Status = {
  configured: boolean; enabled: boolean; provider: string; sdkAppId: string; signId: string;
  signName: string; templateId: string; region: string; secretIdConfigured: boolean; secretKeyConfigured: boolean;
  sandboxTestMode: boolean; codeLifetimeSeconds: number; resendSeconds: number; phoneDailyLimit: number;
  ipMinuteLimit: number; ipDailyLimit: number;
}
const status = ref<Status>()
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try { status.value = (await getP0<Status>('system-settings/sms/config')).data }
  catch { error.value = '短信配置读取失败，请检查连接与权限后重试' }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <main class="sms-settings" v-loading="loading">
    <header><div><h1>短信设置</h1><p>当前运行服务使用服务器环境配置。</p></div><el-button @click="load">刷新配置状态</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-card v-if="status" shadow="never">
      <el-alert :title="status.configured ? '腾讯云短信配置完整' : '腾讯云短信配置尚未完整'" :type="status.configured ? 'success' : 'warning'" :closable="false" />
      <el-descriptions :column="1" border class="sms-details">
        <el-descriptions-item label="服务商">{{ status.provider }}</el-descriptions-item>
        <el-descriptions-item label="SDKAppID">{{ status.sdkAppId || '待配置' }}</el-descriptions-item>
        <el-descriptions-item label="短信签名">{{ status.signName || '待配置' }}</el-descriptions-item>
        <el-descriptions-item label="签名 ID">{{ status.signId || '待配置' }}</el-descriptions-item>
        <el-descriptions-item label="验证码模板 ID">{{ status.templateId || '待配置' }}</el-descriptions-item>
        <el-descriptions-item label="地域">{{ status.region }}</el-descriptions-item>
        <el-descriptions-item label="SecretId">{{ status.secretIdConfigured ? '已配置' : '待配置' }}</el-descriptions-item>
        <el-descriptions-item label="SecretKey">{{ status.secretKeyConfigured ? '已配置' : '待配置' }}</el-descriptions-item>
        <el-descriptions-item label="验证码有效期">{{ status.codeLifetimeSeconds }} 秒</el-descriptions-item>
        <el-descriptions-item label="发送限制">同号 {{ status.resendSeconds }} 秒一次、每日 {{ status.phoneDailyLimit }} 条；同 IP 每分钟 {{ status.ipMinuteLimit }} 条、每日 {{ status.ipDailyLimit }} 条</el-descriptions-item>
        <el-descriptions-item label="沙箱模拟开关">{{ status.sandboxTestMode ? '沙箱模拟已启用' : '关闭' }}</el-descriptions-item>
      </el-descriptions>
      <p>短信发送结果请在“发送记录”中查看服务商返回码、请求编号和短信流水号。</p>
    </el-card>
  </main>
</template>
<style scoped>.sms-settings{padding:28px}header{display:flex;justify-content:space-between;align-items:center;gap:20px;margin-bottom:24px}h1{margin:0 0 8px;font-size:24px}p{color:#707782;font-size:14px;line-height:1.7}.sms-details{margin-top:20px}@media(max-width:700px){.sms-settings{padding:16px}header{align-items:flex-start;flex-direction:column}}</style>
