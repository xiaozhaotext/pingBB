<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'
import { formatDateTime } from '@/utils/datetime'
const route = useRoute()
const consultations = computed(() => route.meta.section === 'p0Consultations' || route.name === 'p0Consultations')
const title = computed(() => consultations.value ? '在线咨询' : '公告管理')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const records = ref<P0Row[]>([])
const notice = ref({ title: '', content: '', type: 'SYSTEM' })
async function load() {
  loading.value = true; error.value = ''; records.value = []
  try { records.value = (await getP0<P0Row[]>(consultations.value ? 'consultations' : 'notices')).data }
  catch { error.value = '加载失败，请检查网络或后台权限后重试' }
  finally { loading.value = false }
}
async function publish() {
  if (!notice.value.title.trim() || !notice.value.content.trim()) return ElMessage.warning('请填写标题和内容')
  saving.value = true
  try { await postP0('notices', notice.value); notice.value = { title: '', content: '', type: 'SYSTEM' }; ElMessage.success('已发布'); await load() }
  finally { saving.value = false }
}
async function reply(item: P0Row) {
  const { value } = await ElMessageBox.prompt(String(item.content), '回复：' + item.subject, { inputType: 'textarea', inputValue: String(item.reply || ''), inputValidator: value => value.trim().length > 0 && value.length <= 5000 || '回复为1至5000字' })
  await postP0(`consultations/${item.id}/reply`, { reply: value })
  ElMessage.success('已回复，会员可在我的咨询中查看'); await load()
}
watch(() => route.name, load, { immediate: true })
</script>
<template>
  <main class="operations" v-loading="loading">
    <header><div><h1>{{ title }}</h1><p>真实数据库记录 · 服务端授权 · 操作审计</p></div><el-button @click="load">刷新</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card v-if="!consultations" class="publish" shadow="never">
      <template #header>发布公告</template>
      <el-form label-position="top"><el-form-item label="标题"><el-input v-model="notice.title" maxlength="128" show-word-limit /></el-form-item><el-form-item label="内容"><el-input v-model="notice.content" type="textarea" :rows="4" maxlength="10000" show-word-limit /></el-form-item><el-button type="primary" :loading="saving" @click="publish">发布并记录审计</el-button></el-form>
    </el-card>
    <el-card shadow="never">
      <el-table :data="records" stripe max-height="650">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column :prop="consultations ? 'subject' : 'title'" label="标题" min-width="160" />
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column v-if="consultations" prop="member_name" label="会员" />
        <el-table-column v-if="consultations" prop="contact" label="联系方式" />
        <el-table-column v-if="consultations" prop="reply" label="客服回复" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100"><template #default="scope">{{ consultations ? (Number(scope.row.status) === 1 ? '已回复' : '待回复') : (Number(scope.row.status) === 1 ? '已发布' : '未发布') }}</template></el-table-column>
        <el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ formatDateTime(String(row.create_time || '')) }}</template></el-table-column>
        <el-table-column v-if="consultations" label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="reply(scope.row)">回复</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !error && !records.length" description="数据库暂无记录" />
    </el-card>
  </main>
</template>
<style scoped>
.operations{padding:28px;background:#f2f6f6;min-height:100vh}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}.publish{margin-bottom:24px}
</style>

