<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElTable } from 'element-plus'
import http from '@/utils/http'
import { productImageUrl } from '@/utils/productImage'
interface Item { id: number; name: string; price: number; pic: string; productSn: string; deletedAt: string }
const route = useRoute()
const zone = computed(() => String(route.name).startsWith('rebuy') ? 'REBUY' : String(route.name).startsWith('consume') ? 'CONSUME' : 'PICK')
const label = computed(() => ({ PICK: '自选区', REBUY: '复购区', CONSUME: '消费区' })[zone.value])
const rows = ref<Item[]>([])
const selected = ref<Item[]>([])
const days = ref(30)
const loading = ref(false)
const busy = ref(false)
const table = ref<InstanceType<typeof ElTable>>()
let revision = 0
async function load() {
  const current = ++revision
  const currentZone = zone.value
  loading.value = true
  selected.value = []
  try {
    const [list, policy] = await Promise.all([
      http<Item[]>({ url: `/p0/zones/${currentZone}/trash` }),
      http<{ days: number }>({ url: `/p0/zones/${currentZone}/trash/policy` }),
    ])
    if (current === revision) { rows.value = list.data; days.value = policy.data.days }
  } finally { if (current === revision) loading.value = false }
}
watch(zone, () => { rows.value = []; load() })
onMounted(load)
async function saveDays() {
  if (!Number.isInteger(days.value) || days.value < 1 || days.value > 3650) { ElMessage.warning('请输入1至3650的整数天数'); return }
  try { await ElMessageBox.confirm(`将${label.value}回收站保留期改为${days.value}天，已超过新期限的商品会在下次自动清理时彻底删除。`, '修改自动清理期限', { type: 'warning' }) } catch { return }
  busy.value = true
  try {
    await http({ url: `/p0/zones/${zone.value}/trash/policy`, method: 'post', data: { days: days.value } })
    ElMessage.success('已保存自动清理期限')
  } finally { busy.value = false }
}
async function restore(item: Item) {
  try { await ElMessageBox.confirm(`恢复「${item.name}」到${label.value}商品列表？恢复后保持下架，可检查后重新上架。`, '恢复商品') } catch { return }
  busy.value = true
  try {
    await http({ url: `/p0/zones/${zone.value}/trash/${item.id}/restore`, method: 'post' })
    ElMessage.success('已恢复，商品保持下架')
    await load()
  } finally { busy.value = false }
}
async function purge(all = false, item?: Item) {
  const ids = item ? [item.id] : selected.value.map(row => row.id)
  if (!item && ids.length === rows.value.length && ids.length > 0) all = true
  const count = all ? rows.value.length : ids.length
  if (!count) return
  try {
    await ElMessageBox.confirm(`将彻底删除${label.value}回收站${all ? '全部' : '选中的'} ${count} 个商品及规格、库存等商品资料。操作不可撤销，历史订单和资金记录保留。`, '确认彻底删除', { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' })
  } catch { return }
  busy.value = true
  try {
    const result = await http<number>({ url: `/p0/zones/${zone.value}/trash/purge`, method: 'post', data: { ids: all ? [] : ids, all } })
    ElMessage.success(`已彻底删除 ${result.data} 个商品`)
    await load()
  } finally { busy.value = false }
}
function selectAll() { table.value?.clearSelection(); rows.value.forEach(row => table.value?.toggleRowSelection(row, true)) }
</script>

<template>
  <main class="trash-page">
    <header><div><h1>{{ label }}商品回收站</h1><p>仅展示本专区删除的商品。恢复后保持下架，彻底删除不可撤销。</p></div><el-button :disabled="busy" @click="load">刷新</el-button></header>
    <el-card shadow="never">
      <div class="policy"><strong>自动清理</strong><span>删除后保留</span><el-input-number v-model="days" :min="1" :max="3650" :precision="0" :disabled="busy || loading" /><span>天</span><el-button :loading="busy" :disabled="loading" @click="saveDays">保存期限</el-button></div>
      <el-alert title="默认保留30天，各专区独立设置；仅平台管理员可修改。系统每小时检查到期商品并彻底删除，每次最多处理500个。缩短期限会影响已在回收站的商品。" type="info" :closable="false" />
      <div class="actions">
        <el-button :disabled="busy || loading || !rows.length" @click="selectAll">全选全部商品</el-button>
        <el-button type="danger" :disabled="busy || loading || !selected.length" @click="purge()">彻底删除所选（{{ selected.length }}）</el-button>
        <el-button type="danger" plain :disabled="busy || loading || !rows.length" @click="purge(true)">清空本专区回收站（{{ rows.length }}）</el-button>
      </div>
      <el-table ref="table" :data="rows" row-key="id" v-loading="loading" max-height="620" empty-text="本专区回收站为空" @selection-change="selected = $event">
        <el-table-column type="selection" width="50" :selectable="() => !busy" />
        <el-table-column prop="id" label="ID" width="75" />
        <el-table-column label="图片" width="85"><template #default="{ row }"><el-image v-if="row.pic" :src="productImageUrl(row.pic)" fit="contain" style="width:52px;height:52px" /></template></el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="230" />
        <el-table-column prop="productSn" label="货号" min-width="130" />
        <el-table-column prop="price" label="价格" width="95" />
        <el-table-column prop="deletedAt" label="删除时间" min-width="185" />
        <el-table-column label="操作" width="185"><template #default="{ row }"><el-button link type="primary" :disabled="busy" @click="restore(row)">恢复</el-button><el-button link type="danger" :disabled="busy" @click="purge(false, row)">彻底删除</el-button></template></el-table-column>
      </el-table>
      <p>共 {{ rows.length }} 个商品，已选 {{ selected.length }} 个</p>
    </el-card>
  </main>
</template>
<style scoped>
.trash-page { padding:24px; background:#f6f7f4; min-height:100vh; }
header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; }
h1 { margin:0 0 10px; font-size:24px; }
header p { color:#66736b; }
.policy,.actions { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin-bottom:18px; }
.actions { margin-top:18px; }
</style>
