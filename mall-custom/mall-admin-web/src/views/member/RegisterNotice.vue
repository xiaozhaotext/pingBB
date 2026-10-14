<script setup lang="ts">
import { onMounted, ref } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'

const KEY = 'register_notice'
const loading = ref(false)
const saving = ref(false)
const content = ref('')
const updateTime = ref('')

async function load() {
  loading.value = true
  try {
    const res = await http<{ content_value: string | null; update_time: string | null }>({
      url: '/p0/system-settings/content/' + KEY,
      method: 'get',
    })
    content.value = res.data.content_value || ''
    updateTime.value = res.data.update_time || ''
  } catch {
    ElMessage.error('注册须知加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function save() {
  saving.value = true
  try {
    await http({ url: '/p0/system-settings/content', method: 'post', data: { key: KEY, value: content.value } })
    ElMessage.success('注册须知已保存')
    await load()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="register-notice" v-loading="loading">
    <header class="head">
      <div>
        <small>会员</small>
        <h1>注册须知</h1>
        <p>会员注册前展示的须知内容；保存后写入内容库并记录操作审计。</p>
      </div>
      <el-button @click="load">重新加载</el-button>
    </header>
    <el-card shadow="never">
      <el-alert
        :title="updateTime ? '上次更新：' + updateTime : '尚未设置注册须知内容'"
        type="info"
        :closable="false"
        class="note"
      />
      <el-input
        v-model="content"
        type="textarea"
        :rows="16"
        maxlength="20000"
        show-word-limit
        placeholder="请输入会员注册须知内容"
      />
      <div class="actions">
        <el-button type="primary" :loading="saving" @click="save">保存注册须知</el-button>
      </div>
    </el-card>
  </main>
</template>

<style scoped>
.register-notice { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.note { margin-bottom: 12px; }
.actions { margin-top: 14px; }
</style>
