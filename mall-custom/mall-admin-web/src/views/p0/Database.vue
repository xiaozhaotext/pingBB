<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'
import { useUserStore } from '@/stores/user'

const baseURL = (import.meta.env.VITE_BASE_SERVER_URL as string | undefined) || '/admin-api'
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const tables = ref<P0Row[]>([])
const selected = ref<P0Row[]>([])
const action = ref('OPTIMIZE')
const results = ref<P0Row[]>([])
const backups = ref<P0Row[]>([])
const createSql = ref('')
const structColumns = ref<P0Row[]>([])
const structIndexes = ref<P0Row[]>([])
const structTitle = ref('')
const structDialog = ref(false)
const route = useRoute()
const activeTab = ref('tables')

function raw<T = unknown>(config: AxiosRequestConfig) {
  const userStore = useUserStore()
  return axios.request<T>({
    baseURL,
    timeout: 300000,
    ...config,
    headers: { Authorization: userStore.userInfo.token, ...(config.headers || {}) },
  })
}

function messageOf(e: unknown) {
  return e instanceof Error ? e.message : String(e)
}

async function loadTables() {
  loading.value = true
  error.value = ''
  try {
    tables.value = (await getP0<P0Row[]>('ops/database/tables', { keyword: keyword.value })).data as P0Row[]
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

async function loadBackups() {
  try {
    backups.value = (await getP0<P0Row[]>('ops/database/backups')).data as P0Row[]
  } catch {
    backups.value = []
  }
}

function onSelection(rows: P0Row[]) {
  selected.value = rows
}

async function showStructure(row: P0Row) {
  const res = await getP0<Record<string, unknown>>('ops/database/table', { table: String(row.tableName) })
  structTitle.value = String(res.data.tableName)
  createSql.value = String(res.data.createSql || '')
  structColumns.value = (res.data.columns as P0Row[]) || []
  structIndexes.value = (res.data.indexes as P0Row[]) || []
  structDialog.value = true
}

async function runCommand() {
  if (!selected.value.length) return ElMessage.warning('请先勾选要操作的表')
  loading.value = true
  try {
    results.value = (await postP0<P0Row[]>('ops/database/command', {
      action: action.value,
      tables: selected.value.map(row => String(row.tableName)),
    })).data as P0Row[]
    ElMessage.success('命令已执行并记录审计')
  } finally {
    loading.value = false
  }
}

async function createBackup() {
  loading.value = true
  try {
    const res = await raw<{ code: number; message: string; data: { name: string; dir: string } }>({
      url: '/p0/ops/database/backup',
      method: 'post',
    })
    if (res.data.code !== 200) return ElMessage.error(res.data.message || '备份失败')
    ElMessage.success('备份完成：' + res.data.data.name)
    await loadBackups()
  } catch (e) {
    ElMessage.error('备份失败：' + messageOf(e))
  } finally {
    loading.value = false
  }
}

async function downloadBackup(row: P0Row) {
  const name = String(row.name)
  try {
    const res = await raw<Blob>({
      url: '/p0/ops/database/backup/download',
      method: 'get',
      params: { name },
      responseType: 'blob',
    })
    const blob = res.data
    if (blob.type && blob.type.includes('json')) {
      const text = await blob.text()
      return ElMessage.error('下载失败：' + (JSON.parse(text).message || '未知错误'))
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = name
    link.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败：' + messageOf(e))
  }
}

async function removeBackup(row: P0Row) {
  const name = String(row.name)
  await ElMessageBox.confirm('确认删除备份 ' + name + '？', '删除备份', { type: 'warning' })
  await postP0('ops/database/backup/delete', { name })
  ElMessage.success('已删除并记录审计')
  await loadBackups()
}

async function restoreBackup(row: P0Row) {
  const name = String(row.name)
  await ElMessageBox.confirm('确认用 ' + name + ' 还原数据库？系统会先自动做一次还原前备份。', '还原数据库', {
    type: 'warning',
    confirmButtonText: '确认还原',
  })
  loading.value = true
  try {
    const res = await raw<{ code: number; message: string; data: { statements: number; safetyBackup: string } }>({
      url: '/p0/ops/database/backup/restore',
      method: 'post',
      data: { name },
    })
    if (res.data.code !== 200) return ElMessage.error(res.data.message || '还原失败')
    ElMessage.success('还原完成，执行 ' + res.data.data.statements + ' 条语句，还原前备份：' + res.data.data.safetyBackup)
    await loadTables()
    await loadBackups()
  } catch (e) {
    ElMessage.error('还原失败：' + messageOf(e))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTables()
  loadBackups()
})
watch(() => route.meta.section, section => {
  if (section === 'tables' || section === 'backups') activeTab.value = section
}, { immediate: true })
</script>
<template>
  <main class="database" v-loading="loading">
    <header>
      <div><h1>数据库管理</h1><p>表结构查看、维护命令与备份还原 · 全部操作记录审计</p></div>
      <el-button @click="loadTables">刷新</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="数据表" name="tables">
        <el-form :inline="true">
          <el-form-item label="关键字"><el-input v-model="keyword" placeholder="表名包含" clearable @keyup.enter="loadTables" /></el-form-item>
          <el-form-item><el-button type="primary" @click="loadTables">查询</el-button></el-form-item>
          <el-form-item>
            <el-select v-model="action" style="width: 150px">
              <el-option label="优化 OPTIMIZE" value="OPTIMIZE" />
              <el-option label="修复 REPAIR" value="REPAIR" />
              <el-option label="分析 ANALYZE" value="ANALYZE" />
              <el-option label="检查 CHECK" value="CHECK" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button type="primary" @click="runCommand">对勾选表执行</el-button></el-form-item>
        </el-form>
        <el-table :data="tables" stripe @selection-change="onSelection">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="tableName" label="表名" min-width="200" />
          <el-table-column prop="engine" label="引擎" width="100" />
          <el-table-column prop="tableRows" label="行数" width="110" />
          <el-table-column prop="sizeMb" label="大小(MB)" width="110" />
          <el-table-column prop="tableComment" label="注释" min-width="160" show-overflow-tooltip />
          <el-table-column prop="updateTime" label="更新时间" min-width="170" />
          <el-table-column label="操作" width="100">
            <template #default="scope"><el-button link type="primary" @click="showStructure(scope.row)">结构</el-button></template>
          </el-table-column>
        </el-table>
        <div v-if="results.length" class="results">
          <h3>命令执行结果</h3>
          <el-table :data="results" stripe>
            <el-table-column prop="tableName" label="表名" min-width="200" />
            <el-table-column prop="command" label="命令" width="120" />
            <el-table-column label="结果" width="100"><template #default="scope"><el-tag :type="scope.row.ok ? 'success' : 'danger'" size="small">{{ scope.row.ok ? '成功' : '失败' }}</el-tag></template></el-table-column>
            <el-table-column label="返回" min-width="260"><template #default="scope">{{ scope.row.ok ? '执行完成' : String(scope.row.error || '') }}</template></el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
      <el-tab-pane label="备份管理" name="backups">
        <div class="toolbar">
          <el-button type="primary" @click="createBackup">立即备份</el-button>
          <el-button @click="loadBackups">刷新列表</el-button>
          <span class="tip">备份保存在服务器备份目录，还原前会自动再做一次安全备份。</span>
        </div>
        <el-table :data="backups" stripe>
          <el-table-column prop="name" label="文件名" min-width="300" show-overflow-tooltip />
          <el-table-column label="大小" width="120"><template #default="scope">{{ (Number(scope.row.sizeBytes || 0) / 1024).toFixed(1) }} KB</template></el-table-column>
          <el-table-column prop="modifiedAt" label="生成时间" min-width="220" />
          <el-table-column label="操作" width="220">
            <template #default="scope">
              <el-button link type="primary" @click="downloadBackup(scope.row)">下载</el-button>
              <el-button link type="primary" @click="restoreBackup(scope.row)">还原</el-button>
              <el-button link type="danger" @click="removeBackup(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!backups.length" description="暂无备份文件" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="structDialog" :title="'表结构：' + structTitle" width="880px">
      <el-input :model-value="createSql" type="textarea" :rows="6" readonly />
      <h3>字段</h3>
      <el-table :data="structColumns" stripe max-height="300">
        <el-table-column prop="columnName" label="字段" min-width="160" />
        <el-table-column prop="columnType" label="类型" min-width="150" />
        <el-table-column prop="nullable" label="可空" width="80" />
        <el-table-column prop="columnKey" label="键" width="80" />
        <el-table-column prop="columnDefault" label="默认值" min-width="110" show-overflow-tooltip />
        <el-table-column prop="columnComment" label="注释" min-width="140" show-overflow-tooltip />
      </el-table>
      <h3>索引</h3>
      <el-table :data="structIndexes" stripe max-height="220">
        <el-table-column prop="indexName" label="索引" min-width="160" />
        <el-table-column label="唯一" width="90"><template #default="scope">{{ Number(scope.row.nonUnique) === 0 ? '是' : '否' }}</template></el-table-column>
        <el-table-column prop="seqInIndex" label="顺序" width="80" />
        <el-table-column prop="columnName" label="字段" min-width="160" />
      </el-table>
    </el-dialog>
  </main>
</template>
<style scoped>
.database{padding:28px;background:#f2f7f7;min-height:100vh}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}.results{margin-top:20px}.toolbar{display:flex;align-items:center;gap:12px;margin-bottom:16px}.tip{color:#698789;font-size:13px}
</style>
