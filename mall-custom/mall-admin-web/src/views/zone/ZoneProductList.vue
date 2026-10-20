<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productImageUrl } from '@/utils/productImage'
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
  tourRoute: boolean | number
  sortOrder: number
}

const route = useRoute()
const router = useRouter()
// 自选/复购/消费三个专区共用同一套商品管理页面，专区由当前路由名决定
const ZONES = {
  pickProduct: { code: 'PICK', label: '自选区', prefix: 'pick', rule: '自选区只销售3999元旅游路线；游客购买一条并支付成功后升级会员。' },
  rebuyProduct: { code: 'REBUY', label: '复购区', prefix: 'rebuy', rule: '复购区商品按 30% 复购积分抵扣，剩余 70% 优先使用账户余额；余额不足的差额走微信或支付宝支付。不能使用商品积分。' },
  consumeProduct: { code: 'CONSUME', label: '消费区', prefix: 'consume', rule: '消费区商品只能用商品积分购买，不能使用复购积分、余额或现金。' },
} as const
const currentZone = computed(() => ZONES[route.name as keyof typeof ZONES] ?? ZONES.pickProduct)
const zone = computed(() => currentZone.value.code)
const zoneLabel = computed(() => currentZone.value.label)
const zoneRule = computed(() => currentZone.value.rule)
const keyword = ref('')
const assignmentFilter = ref('all')
const page = ref(1)
const filteredRows = computed(() => rows.value.filter(row => assignmentFilter.value === 'all' || (assignmentFilter.value === 'assigned' ? isAssigned(row) : !isAssigned(row))))
const pagedRows = computed(() => filteredRows.value.slice((page.value - 1) * 10, page.value * 10))
watch(assignmentFilter, () => { page.value = 1 })
const loading = ref(false)
const rows = ref<ZoneProduct[]>([])
const busy = ref<number | null>(null)
let loadRevision = 0

function isAssigned(row: ZoneProduct) {
  return row.assigned === true || row.assigned === 1
}

async function load() {
  const revision = ++loadRevision
  loading.value = true
  try {
    const res = await http<ZoneProduct[]>({
      url: '/p0/zones/' + zone.value + '/products',
      params: { keyword: keyword.value, assignedOnly: false },
    })
    if (revision === loadRevision) { rows.value = res.data || []; page.value = 1 }
  } catch {
    if (revision === loadRevision) ElMessage.error('商品列表加载失败，请重试')
  } finally {
    if (revision === loadRevision) loading.value = false
  }
}

watch(zone, () => {
  keyword.value = ''
  assignmentFilter.value = 'all'
  load()
})
onMounted(load)

function goAdd() {
  router.push({ name: `${currentZone.value.prefix}AddProduct` })
}
function goEdit(row: ZoneProduct) {
  router.push({ name: `${currentZone.value.prefix}UpdateProduct`, query: { id: row.id } })
}
async function assign(row: ZoneProduct) {
  if (zone.value === 'PICK') {
    try {
      await ElMessageBox.confirm(`请确认「${row.name}」及所有规格均为3999元旅游路线。加入后将作为会员权益购买入口。`, '确认旅游路线类型', { type: 'warning' })
    } catch { return }
  }
  busy.value = row.id
  try {
    await http({
      url: '/p0/zones/' + zone.value + '/products',
      method: 'post',
      data: { productId: row.id, sortOrder: row.sortOrder || 0, tourRoute: zone.value === 'PICK' },
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
  const nextStatus = row.publishStatus === 1 ? 0 : 1
  const action = nextStatus === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确认${action}商品「${row.name}」（ID ${row.id}）？${nextStatus === 0 ? '下架后商城将不再展示该商品。' : '上架后商城将展示该商品。'}`, `确认${action}`, { type: 'warning', confirmButtonText: `确认${action}`, cancelButtonText: '取消' })
  } catch { return }
  busy.value = row.id
  try {
    await productUpdatePublishStatusAPI({ ids: String(row.id), publishStatus: nextStatus })
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
        <el-select v-model="assignmentFilter" style="width:200px" aria-label="专区归属筛选">
          <el-option label="全部未删除商品" value="all" />
          <el-option :label="'已加入' + zoneLabel" value="assigned" />
          <el-option :label="'未加入' + zoneLabel" value="unassigned" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>
      <el-alert type="info" :closable="false">
        <template #title>
          <span>{{ zoneRule }}</span>
        </template>
        <span>商品可以分别归属三个专区；下架商品仍可管理，已删除商品不在此列。当前查询返回 {{ rows.length }} 个未删除商品，其中 {{ rows.filter(isAssigned).length }} 个已加入本专区。最多返回100条，请用关键词缩小范围。</span>
      </el-alert>
      <el-table :data="pagedRows" v-loading="loading" empty-text="暂无符合条件的商品">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="图片" width="86">
          <template #default="{ row }">
            <span v-if="!row.pic">暂无图片</span>
            <el-image v-else :src="productImageUrl(row.pic)" fit="contain" style="width: 52px; height: 52px"><template #error>加载失败</template></el-image>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="price" label="价格" width="96" />
        <el-table-column v-if="zone === 'PICK'" label="旅游路线" width="100">
          <template #default="{ row }"><el-tag :type="row.tourRoute ? 'success' : 'info'">{{ row.tourRoute ? '已标记' : '未标记' }}</el-tag></template>
        </el-table-column>
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
      <el-pagination v-model:current-page="page" :page-size="10" :total="filteredRows.length" layout="total, prev, pager, next" />
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


