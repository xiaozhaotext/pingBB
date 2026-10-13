<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'
import http from '@/utils/http'

type Dept = {
  id: number
  parent_id: number
  name: string
  leader?: string
  phone?: string
  role_id?: number | null
  sort: number
  status: number
  remark?: string
  admin_count?: number
  children?: Dept[]
}

const loading = ref(false)
const error = ref('')
const flat = ref<Dept[]>([])
const roles = ref<{ id: number; name: string }[]>([])
const admins = ref<{ id: number; username: string; nick_name?: string }[]>([])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref<Partial<Dept>>({})

const assignVisible = ref(false)
const assignDept = ref<Dept | null>(null)
const assignIds = ref<number[]>([])

const tree = computed<Dept[]>(() => {
  const map = new Map<number, Dept>()
  flat.value.forEach(d => map.set(d.id, { ...d, children: [] }))
  const roots: Dept[] = []
  map.forEach(node => {
    const parent = node.parent_id ? map.get(node.parent_id) : undefined
    if (parent) parent.children!.push(node)
    else roots.push(node)
  })
  return roots
})

const roleName = computed(() => {
  const map = new Map<number, string>()
  roles.value.forEach(r => map.set(r.id, r.name))
  return (id?: number | null) => (id ? map.get(id) ?? `#${id}` : '未设置')
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getP0<P0Row[]>('departments')
    flat.value = (res.data as Dept[]) || []
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const res = await http<{ id: number; name: string }[]>({ url: '/role/listAll', method: 'get' })
    roles.value = res.data || []
  } catch {
    roles.value = []
  }
  try {
    const res = await http<{ list: { id: number; username: string; nick_name?: string }[] }>({
      url: '/admin/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 200 },
    })
    admins.value = res.data?.list || []
  } catch {
    admins.value = []
  }
}

function openCreate(parent?: Dept) {
  editingId.value = null
  form.value = { parent_id: parent?.id ?? 0, name: '', leader: '', phone: '', role_id: null, sort: 0, status: 1, remark: '' }
  dialogVisible.value = true
}

function openEdit(row: Dept) {
  editingId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.name || !String(form.value.name).trim()) {
    ElMessage.warning('请填写部门名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      parentId: form.value.parent_id ?? 0,
      name: String(form.value.name).trim(),
      leader: form.value.leader ?? '',
      phone: form.value.phone ?? '',
      roleId: form.value.role_id ?? null,
      sort: form.value.sort ?? 0,
      status: form.value.status ?? 1,
      remark: form.value.remark ?? '',
    }
    if (editingId.value) await postP0(`departments/${editingId.value}`, payload)
    else await postP0('departments', payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: Dept) {
  await ElMessageBox.confirm(`确认删除部门「${row.name}」？`, '删除确认', { type: 'warning' })
  try {
    await postP0(`departments/${row.id}/delete`, {})
    ElMessage.success('已删除')
    await load()
  } catch {
    ElMessage.error('删除失败：可能存在下级部门')
  }
}

async function openAssign(row: Dept) {
  assignDept.value = row
  assignIds.value = []
  try {
    const res = await getP0<P0Row[]>(`departments/${row.id}/admins`)
    assignIds.value = ((res.data as { id: number }[]) || []).map(a => a.id)
  } catch {
    assignIds.value = []
  }
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignDept.value) return
  try {
    await postP0(`departments/${assignDept.value.id}/admins`, { adminIds: assignIds.value })
    ElMessage.success('已保存部门成员')
    assignVisible.value = false
    await load()
  } catch {
    ElMessage.error('保存失败')
  }
}

onMounted(async () => {
  await Promise.all([load(), loadOptions()])
})
</script>
<template>
  <main class="department" v-loading="loading">
    <header>
      <div>
        <h1>部门管理</h1>
        <p>后台组织架构与部门权限：部门可绑定默认角色，并指派管理员归属</p>
      </div>
      <div>
        <el-button type="primary" @click="openCreate()">新建部门</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card shadow="never">
      <el-table :data="tree" row-key="id" default-expand-all stripe>
        <el-table-column prop="name" label="部门名称" min-width="220" />
        <el-table-column label="默认角色" width="160">
          <template #default="scope">{{ roleName(scope.row.role_id) }}</template>
        </el-table-column>
        <el-table-column prop="leader" label="负责人" width="140" />
        <el-table-column prop="phone" label="联系电话" width="150" />
        <el-table-column prop="admin_count" label="成员数" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openCreate(scope.row)">新增下级</el-button>
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="primary" @click="openAssign(scope.row)">成员</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!tree.length && !loading" description="暂无部门，点击“新建部门”创建" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑部门' : '新建部门'" width="520px">
      <el-form label-width="96px">
        <el-form-item label="上级部门">
          <el-select v-model="form.parent_id" style="width: 100%">
            <el-option :value="0" label="顶级部门" />
            <el-option v-for="d in flat.filter(x => x.id !== editingId)" :key="d.id" :value="d.id" :label="d.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="默认角色">
          <el-select v-model="form.role_id" clearable placeholder="不设置" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :value="r.id" :label="r.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.leader" maxlength="64" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" maxlength="32" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" :title="`部门成员 · ${assignDept?.name ?? ''}`" width="520px">
      <el-select v-model="assignIds" multiple filterable placeholder="选择管理员" style="width: 100%">
        <el-option v-for="a in admins" :key="a.id" :value="a.id" :label="`${a.username}${a.nick_name ? ' · ' + a.nick_name : ''}`" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>
<style scoped>
.department{padding:28px;background:#f2f7f7;min-height:100vh}
header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px;gap:16px}
h1{color:#285a62;margin:0 0 8px}
p{color:#698789;margin:0}
</style>
