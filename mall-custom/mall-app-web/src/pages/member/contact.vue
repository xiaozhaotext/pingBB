<template><view class="contact"><text class="title">联系资料</text><text>以下为本人填写资料，不代表实名认证。</text><input v-model="form.real_name" :maxlength="64" placeholder="真实姓名（选填）"/><input v-model="form.email" :maxlength="128" placeholder="邮箱（选填）"/><input v-model="form.wechat_account" :maxlength="64" placeholder="微信账号（选填）"/><button :disabled="busy || !loaded" @click="save">保存</button><text v-if="error" @click="load">{{ error }}，点击重试</text></view></template>
<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { http } from '@/utils/http'
const form = ref({ real_name: '', email: '', wechat_account: '' }), busy = ref(false), loaded = ref(false), error = ref('')
async function load() { error.value = ''; try { form.value = (await http<typeof form.value>({ method: 'GET', url: '/member/contact' })).data; loaded.value = true } catch { error.value = '资料加载失败'; loaded.value = false } }
async function save() { if (busy.value || !loaded.value) return; busy.value = true; try { await http({ method: 'POST', url: '/member/contact', data: form.value }); uni.showToast({ title: '已保存', icon: 'success' }) } finally { busy.value = false } }
onShow(load)
</script>
<style scoped>.contact{padding:24px;display:flex;flex-direction:column;gap:20px}.title{font-size:24px;font-weight:bold}input{padding:16px;background:white;border-radius:8px}text{color:#51625b;font-size:14px}</style>
