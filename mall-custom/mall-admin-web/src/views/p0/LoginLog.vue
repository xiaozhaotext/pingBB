<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getP0, type P0Row } from '@/apis/p0'

const loading = ref(false)
const error = ref('')
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<P0Row[]>([])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getP0<Record<string, unknown>>('login-logs', {
      keyword: keyword.value.trim(),
      page: page.value,
      size: size.value,
    })
    const data = res.data ?? {}
    rows.value = (data.list as P0Row[]) || []
    total.value = Number(data.total || 0)
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(current: number) {
  page.value = current
  load()
}

onMounted(load)
</script>
<template>
  <main class="login-log" v-loading="loading">
    <header>
      <div>
        <h1>登录日志</h1>
        <p>后台管理员登录记录（ums_admin_login_log）· 每次登录写入一条，支持按管理员或 IP 检索</p>
      </div>
      <div class="tools">
        <el-input v-model="keyword" placeholder="按管理员或 IP 搜索" style="width: 220px" clearable @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card shadow="never">
      <el-table :data="rows" stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="username" label="管理员" width="150" />
        <el-table-column prop="admin_id" label="管理员ID" width="110" />
        <el-table-column prop="ip" label="登录IP" min-width="150" />
        <el-table-column prop="address" label="归属地" min-width="140" />
        <el-table-column prop="user_agent" label="User-Agent" min-width="220" show-overflow-tooltip />
        <el-table-column prop="create_time" label="登录时间" min-width="180" />
      </el-table>
      <el-empty v-if="!rows.length && !loading" description="暂无登录记录" />
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </el-card>
  </main>
</template>
<style scoped>
.login-log{padding:28px;background:#f2f7f7;min-height:100vh}
header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px;gap:16px}
h1{color:#285a62;margin:0 0 8px}
p{color:#698789;margin:0}
.tools{display:flex;gap:10px;align-items:center}
.pager{margin-top:16px;justify-content:flex-end}
</style>
