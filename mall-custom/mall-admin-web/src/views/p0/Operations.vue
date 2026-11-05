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
const editing = ref(false)
const editingId = ref<number>()
const editForm = ref({ title: '', content: '' })
const editingSaving = ref(false)
const activeNoticeId = ref<number>()
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
function editNotice(item: P0Row) {
  editingId.value = Number(item.id)
  editForm.value = { title: String(item.title || ''), content: String(item.content || '') }
  editing.value = true
}
async function saveNotice() {
  if (editingSaving.value || !editingId.value) return
  if (!editForm.value.title.trim() || !editForm.value.content.trim()) return ElMessage.warning('请填写标题和内容')
  editingSaving.value = true
  try {
    await postP0(`notices/${editingId.value}/update`, editForm.value)
    editing.value = false
    ElMessage.success('公告已保存')
    await load()
  } finally { editingSaving.value = false }
}
async function changeNoticeStatus(item: P0Row) {
  if (activeNoticeId.value !== undefined) return
  const status = Number(item.status) === 1 ? 0 : 1
  const confirmed = await ElMessageBox.confirm(
    status === 1 ? `上线“${item.title}”后，用户可在首页和消息页查看。` : `确认下线“${item.title}”？下线后应用端将隐藏这条公告。`,
    status === 1 ? '上线公告' : '下线公告', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
  ).then(() => true, () => false)
  if (!confirmed) return
  activeNoticeId.value = Number(item.id)
  try {
    await postP0(`notices/${item.id}/status`, { status })
    ElMessage.success(status === 1 ? '公告已上线' : '公告已下线')
    await load()
  } finally { activeNoticeId.value = undefined }
}
async function deleteNotice(item: P0Row) {
  if (activeNoticeId.value !== undefined) return
  const confirmed = await ElMessageBox.confirm(
    `确认永久删除“${item.title}”？删除后无法恢复，可选择下线来暂时隐藏公告。`,
    '删除公告', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
  ).then(() => true, () => false)
  if (!confirmed) return
  activeNoticeId.value = Number(item.id)
  try {
    await postP0(`notices/${item.id}/delete`, {})
    ElMessage.success('公告已删除')
    await load()
  } finally { activeNoticeId.value = undefined }
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
        <el-table-column prop="status" label="状态" width="100"><template #default="scope">{{ consultations ? (Number(scope.row.status) === 1 ? '已回复' : '待回复') : (Number(scope.row.status) === 1 ? '已发布' : '已下线') }}</template></el-table-column>
        <el-table-column label="创建时间" min-width="180"><template #default="{ row }">{{ formatDateTime(String(row.create_time || '')) }}</template></el-table-column>
        <el-table-column v-if="consultations" label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="reply(scope.row)">回复</el-button></template></el-table-column>
        <el-table-column v-else label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="activeNoticeId !== undefined" @click="editNotice(row)">编辑</el-button>
            <el-button link :type="Number(row.status) === 1 ? 'warning' : 'success'" :disabled="activeNoticeId !== undefined" @click="changeNoticeStatus(row)">{{ Number(row.status) === 1 ? '下线' : '上线' }}</el-button>
            <el-button link type="danger" :disabled="activeNoticeId !== undefined" @click="deleteNotice(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !error && !records.length" description="数据库暂无记录" />
    </el-card>
    <el-dialog v-model="editing" title="编辑公告" width="min(600px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!editingSaving" :show-close="!editingSaving">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="editForm.title" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="内容"><el-input v-model="editForm.content" type="textarea" :rows="8" maxlength="10000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="editingSaving" @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="editingSaving" @click="saveNotice">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>
<style scoped>
.operations{padding:28px;background:#f2f6f6;min-height:100vh}header{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}.publish{margin-bottom:24px}
</style>

