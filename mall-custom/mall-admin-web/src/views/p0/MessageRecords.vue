<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getP0, type P0Row } from '@/apis/p0'

const loading = ref(false)
const error = ref('')
const list = ref<P0Row[]>([])
const summary = ref<P0Row[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const channel = ref('')
const status = ref('')
const statusText: Record<string, string> = { SENT: '已发送', FAILED: '发送失败', SKIPPED: '未发送' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getP0<{ total: number; list: P0Row[] }>('ops/messages', {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      channel: channel.value,
      status: status.value,
    })
    total.value = Number(res.data.total || 0)
    list.value = res.data.list || []
    summary.value = (await getP0<P0Row[]>('ops/messages/summary', { days: 30 })).data as P0Row[]
  } catch {
    error.value = '加载失败，请检查权限或连接后重试'
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  load()
}

onMounted(load)
</script>
<template>
  <main class="message-records" v-loading="loading">
    <header>
      <div><h1>发送记录</h1><p>短信、站内信等消息发送结果 · 近 30 天汇总</p></div>
      <el-button @click="load">刷新</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card shadow="never" class="summary">
      <template #header>近 30 天汇总</template>
      <el-table :data="summary" stripe>
        <el-table-column prop="channel" label="渠道" width="140" />
        <el-table-column label="状态" width="140"><template #default="scope">{{ statusText[String(scope.row.status)] || String(scope.row.status) }}</template></el-table-column>
        <el-table-column prop="amount" label="数量" width="120" />
      </el-table>
      <el-empty v-if="!summary.length" description="近 30 天暂无发送记录" />
    </el-card>
    <el-card shadow="never">
      <el-form :inline="true">
        <el-form-item label="渠道">
          <el-select v-model="channel" clearable placeholder="全部" style="width: 140px">
            <el-option label="短信" value="SMS" />
            <el-option label="站内信" value="站内信" />
            <el-option label="推送" value="推送" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" clearable placeholder="全部" style="width: 140px">
            <el-option label="已发送" value="SENT" />
            <el-option label="发送失败" value="FAILED" />
            <el-option label="未发送" value="SKIPPED" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="list" stripe>
        <el-table-column prop="id" label="编号" width="90" />
        <el-table-column prop="channel" label="渠道" width="100" />
        <el-table-column prop="target" label="接收方" min-width="140" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="biz_type" label="业务类型" width="120" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'SENT' ? 'success' : scope.row.status === 'FAILED' ? 'danger' : 'info'" size="small">{{ statusText[String(scope.row.status)] || String(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="error_msg" label="失败原因" min-width="180" show-overflow-tooltip />
        <el-table-column prop="create_time" label="时间" min-width="170" />
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="load"
        @size-change="search"
      />
      <el-empty v-if="!loading && !error && !list.length" description="数据库暂无发送记录" />
    </el-card>
  </main>
</template>
<style scoped>
.message-records{padding:28px;background:#f2f7f7;min-height:100vh}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789;margin:0}.summary{margin-bottom:20px}.pager{margin-top:16px;justify-content:flex-end}
</style>
