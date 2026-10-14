<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  productUpdatePublishStatusAPI,
  productUpdateNewStatusAPI,
  productUpdateRecommendStatusAPI,
} from '@/apis/product'

interface ZoneProduct {
  id: number
  name: string
  pic: string
  price: number
  publishStatus: number
  assigned: boolean | number
  sortOrder: number
}

const route = useRoute()
const router = useRouter()
const zone = computed(() => (route.name === 'rebuyProduct' ? 'REBUY' : 'PICK'))
const zoneLabel = computed(() => (zone.value === 'PICK' ? '自选区' : '复购区'))
const keyword = ref('')
const assignedOnly = ref(false)
const loading = ref(false)
const rows = ref<ZoneProduct[]>([])
const busy = ref<number | null>(null)

function isAssigned(row: ZoneProduct) {
  return row.assigned === true || row.assigned === 1
}

async function load() {
  loading.value = true
  try {
    const res = await http<ZoneProduct[]>({
      url: '/p0/zones/' + zone.value + '/products',
      params: { keyword: keyword.value, assignedOnly: assignedOnly.value },
    })
    rows.value = res.data || []
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

watch(zone, () => {
  keyword.value = ''
  assignedOnly.value = false
  load()
})
onMounted(load)

function goAdd() {
  router.push({ name: zone.value === 'PICK' ? 'pickAddProduct' : 'rebuyAddProduct' })
}
function goEdit(row: ZoneProduct) {
  router.push({ name: zone.value === 'PICK' ? 'pickUpdateProduct' : 'rebuyUpdateProduct', query: { id: row.id } })
}
async function assign(row: ZoneProduct) {
  busy.value = row.id
  try {
    await http({
      url: '/p0/zones/' + zone.value + '/products',
      method: 'post',
      data: { productId: row.id, sortOrder: row.sortOrder || 0 },
    })
    ElMessage.success('已加入' + zoneLabel.value)
    await load()
  } catch {
    ElMessage.error('加入失败')
  } finally {
    busy.value = null
  }
}
async function remove(row: ZoneProduct) {
  await ElMessageBox.confirm('仅将商品移出' + zoneLabel.value + '，商品本身与其他专区归属不变。', '移出专区')
  busy.value = row.id
  try {
    await http({ url: '/p0/zones/' + zone.value + '/products/' + row.id, method: 'delete' })
    ElMessage.success('已移出')
    await load()
  } catch {
    ElMessage.error('移出失败')
  } finally {
    busy.value = null
  }
}
async function togglePublish(row: ZoneProduct) {
  busy.value = row.id
  try {
    await productUpdatePublishStatusAPI({ ids: String(row.id), publishStatus: row.publishStatus === 1 ? 0 : 1 })
    ElMessage.success('操作成功')
    await load()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    busy.value = null
  }
}
async function setFlag(row: ZoneProduct, kind: 'new' | 'recommend') {
  busy.value = row.id
  try {
    if (kind === 'new') {
      await productUpdateNewStatusAPI({ ids: String(row.id), newStatus: 1 })
    } else {
      await productUpdateRecommendStatusAPI({ ids: String(row.id), recommendStatus: 1 })
    }
    ElMessage.success('已设置')
    await load()
  } catch {
    ElMessage.error('设置失败')
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <main class="zone-products">
    <header class="head">
      <div>
        <small>商品管理</small>
        <h1>{{ zoneLabel }}商品管理</h1>
        <p>商品上下架、上首页（新品/推荐）与专区归属统一在此维护。</p>
      </div>
      <div class="ops">
        <el-button type="primary" @click="goAdd">添加商品</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>
    <el-card shadow="never">
      <div class="filters">
        <el-input v-model="keyword" placeholder="搜索商品名称或货号" clearable style="max-width: 280px" @keyup.enter="load" />
        <el-checkbox v-model="assignedOnly" @change="load">仅看已加入{{ zoneLabel }}</el-checkbox>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-alert
        title="商品可以分别归属自选区与复购区；下架商品仍可管理，但不会在商城显示。"
        type="info"
        :closable="false"
      />
      <el-table :data="rows" v-loading="loading" empty-text="暂无符合条件的商品">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="图片" width="86">
          <template #default="{ row }">
            <el-image :src="row.pic" fit="contain" style="width: 52px; height: 52px" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="price" label="价格" width="96" />
        <el-table-column label="上架状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.publishStatus === 1 ? 'success' : 'info'">
              {{ row.publishStatus === 1 ? '已上架' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="专区归属" width="104">
          <template #default="{ row }">
            <el-tag v-if="isAssigned(row)" type="warning">已加入</el-tag>
            <span v-else>未加入</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="busy === row.id" @click="goEdit(row)">修改</el-button>
            <el-button link type="primary" :disabled="busy === row.id" @click="togglePublish(row)">
              {{ row.publishStatus === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="primary" :disabled="busy === row.id" @click="setFlag(row, 'new')">设为新品</el-button>
            <el-button link type="primary" :disabled="busy === row.id" @click="setFlag(row, 'recommend')">设为推荐</el-button>
            <el-button v-if="isAssigned(row)" link type="danger" :disabled="busy === row.id" @click="remove(row)">移出</el-button>
            <el-button v-else link type="success" :disabled="busy === row.id" @click="assign(row)">加入专区</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </main>
</template>

<style scoped>
.zone-products { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.filters { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
</style>
