<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

type Department = { id: number; name: string; status: number; role_id: number | null }
type Account = { id: number; username: string; nick_name: string; email: string; status: number; dept_id: number; dept_name: string }
const rows = ref<Account[]>([])
const departments = ref<Department[]>([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref<number | null>(null)
const saving = ref(false)
const form = ref({ username: '', password: '', nickName: '', email: '', status: 1, departmentId: null as number | null })

async function load() {
  loading.value = true
  try {
    const [accounts, depts] = await Promise.all([getP0<Account[]>('subaccounts'), getP0<Department[]>('departments')])
    rows.value = accounts.data || []
    departments.value = depts.data || []
  } catch { ElMessage.error('子账号或部门加载失败') }
  finally { loading.value = false }
}
function add() {
  editing.value = null
  form.value = { username: '', password: '', nickName: '', email: '', status: 1, departmentId: null }
  dialog.value = true
}
function edit(row: Account) {
  editing.value = row.id
  form.value = { username: row.username, password: '', nickName: row.nick_name || '', email: row.email || '', status: row.status, departmentId: row.dept_id }
  dialog.value = true
}
async function save() {
  if (!form.value.departmentId || (!editing.value && form.value.password.length < 8)) {
    ElMessage.warning('请选择已配置角色的部门，并填写至少 8 位密码')
    return
  }
  saving.value = true
  try {
    await postP0(editing.value ? `subaccounts/${editing.value}` : 'subaccounts', form.value as unknown as Record<string, unknown>)
    ElMessage.success('已保存；部门权限将在下次请求时生效')
    dialog.value = false
    await load()
  } catch { ElMessage.error('保存失败，请检查部门角色、账号或密码') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <main class="subaccounts" v-loading="loading">
    <header>
      <div><h1>子账号管理</h1><p>每个子账号归属一个部门，只能看到该部门角色授权的后台板块。</p></div>
      <div><el-button @click="$router.push('/systemSettings/admins/roles')">配置角色板块</el-button><el-button type="primary" @click="add">新建子账号</el-button></div>
    </header>
    <el-alert title="先在部门管理中绑定角色，再在角色配置中分配菜单和接口资源。部门停用或角色未配置时，子账号无后台板块权限。" type="info" :closable="false" show-icon />
    <el-card shadow="never">
      <el-table :data="rows" stripe>
        <el-table-column prop="username" label="账号" min-width="160" />
        <el-table-column prop="nick_name" label="姓名" min-width="140" />
        <el-table-column prop="dept_name" label="部门" min-width="160" />
        <el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="edit(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialog" :title="editing ? '编辑子账号' : '新建子账号'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="账号"><el-input v-model="form.username" :disabled="!!editing" maxlength="40" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.nickName" maxlength="80" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="100" /></el-form-item>
        <el-form-item :label="editing ? '重设密码' : '密码'"><el-input v-model="form.password" type="password" autocomplete="new-password" :placeholder="editing ? '留空则不修改' : '至少 8 位'" /></el-form-item>
        <el-form-item label="所属部门"><el-select v-model="form.departmentId" placeholder="请选择部门" style="width:100%"><el-option v-for="d in departments.filter(x => x.status === 1 && x.role_id)" :key="d.id" :label="d.name" :value="d.id" /></el-select></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
</template>
<style scoped>
.subaccounts{padding:28px;background:#f2f7f7;min-height:100vh}
header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px;gap:16px}
h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}
.el-card{margin-top:16px}
</style>
