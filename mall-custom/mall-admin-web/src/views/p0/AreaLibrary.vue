<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'

const loading = ref(false)
const error = ref('')
const rows = ref<P0Row[]>([])
const path = ref<P0Row[]>([])
const dialog = ref(false)
const saving = ref(false)
const form = ref({ id: 0, name: '', code: '', sort: 0, enabled: true })

const current = computed(() => (path.value.length ? path.value[path.value.length - 1] : undefined))
const parentId = computed(() => (current.value ? Number(current.value.id) : 0))
const childLevel = computed(() => (current.value ? Number(current.value.level) + 1 : 1))
const parentName = computed(() => (current.value ? String(current.value.name) : ''))
const levelName = (level: unknown) => ['', '省级', '市级', '区县', '街道'][Number(level)] || '地址'
const canDrill = (level: unknown) => Number(level) < 4

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = (await getP0<P0Row[]>('ops/areas', { parentId: parentId.value })).data as P0Row[]
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

function enter(row: P0Row) {
  path.value = [...path.value, row]
  load()
}

function jump(index: number) {
  path.value = index < 0 ? [] : path.value.slice(0, index + 1)
  load()
}

function openCreate() {
  form.value = { id: 0, name: '', code: '', sort: 0, enabled: true }
  dialog.value = true
}

function openEdit(row: P0Row) {
  form.value = {
    id: Number(row.id),
    name: String(row.name),
    code: row.code == null ? '' : String(row.code),
    sort: Number(row.sort || 0),
    enabled: Number(row.enabled) !== 0,
  }
  dialog.value = true
}

async function submit() {
  if (!form.value.name.trim()) return ElMessage.warning('请填写地址名称')
  saving.value = true
  try {
    await postP0('ops/areas', { ...form.value, parentId: parentId.value })
    ElMessage.success('已保存并记录审计')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: P0Row) {
  await ElMessageBox.confirm('确认删除地址「' + String(row.name) + '」？仅当没有下级地址时才能删除。', '删除地址', { type: 'warning' })
  await postP0('ops/areas/delete', { id: Number(row.id) })
  ElMessage.success('已删除并记录审计')
  await load()
}

onMounted(load)
</script>
<template>
  <main class="area-library" v-loading="loading">
    <header>
      <div><h1>省市区街道地址库</h1><p>四级地址 · 服务端白名单校验 · 操作审计</p></div>
      <div>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" :disabled="childLevel > 4" @click="openCreate">新增{{ levelName(childLevel) }}</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card shadow="never">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item><el-link @click="jump(-1)">顶级</el-link></el-breadcrumb-item>
        <el-breadcrumb-item v-for="(node, i) in path" :key="Number(node.id)">
          <el-link @click="jump(i)">{{ String(node.name) }}</el-link>
        </el-breadcrumb-item>
      </el-breadcrumb>
      <el-table :data="rows" stripe class="table">
        <el-table-column prop="id" label="编号" width="90" />
        <el-table-column label="名称" min-width="180">
          <template #default="scope">
            <el-link v-if="canDrill(scope.row.level)" type="primary" @click="enter(scope.row)">{{ String(scope.row.name) }}</el-link>
            <span v-else>{{ String(scope.row.name) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90"><template #default="scope">{{ levelName(scope.row.level) }}</template></el-table-column>
        <el-table-column prop="code" label="行政区划代码" width="150" />
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="scope"><el-tag :type="Number(scope.row.enabled) === 0 ? 'info' : 'success'" size="small">{{ Number(scope.row.enabled) === 0 ? '停用' : '启用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button v-if="canDrill(scope.row.level)" link type="primary" @click="enter(scope.row)">下级</el-button>
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !error && !rows.length" description="当前层级暂无地址，可新增" />
    </el-card>
    <el-dialog v-model="dialog" :title="form.id ? '编辑' + levelName(childLevel) : '新增' + levelName(childLevel)" width="460px">
      <el-form label-position="top">
        <el-form-item label="上级地址">{{ parentName || '顶级' }}</el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="64" show-word-limit /></el-form-item>
        <el-form-item label="行政区划代码（可选）"><el-input v-model="form.code" maxlength="16" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="100000" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>
<style scoped>
.area-library{padding:28px;background:#f2f7f7;min-height:100vh}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}.table{margin-top:16px}
</style>
