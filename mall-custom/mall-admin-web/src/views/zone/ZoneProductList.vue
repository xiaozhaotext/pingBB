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
  productUpdateDeleteStatusAPI,
} from '@/apis/product'

interface ZoneProduct {
  id: number
  name: string
  pic: string
  price: number
  publishStatus: number
  deleteStatus: number
  assigned: boolean | number
  sortOrder: number
}

const route = useRoute()
const router = useRouter()
// 自选/复购/消费三个专区共用同一套商品管理页面，专区由当前路由名决定
const ZONES = {
  pickProduct: { code: 'PICK', label: '自选区', prefix: 'pick', rule: '自选区商品用现金或余额（充值本金）购买，不能使用产品积分或复购积分。' },
  rebuyProduct: { code: 'REBUY', label: '复购区', prefix: 'rebuy', rule: '复购区商品按 30% 复购积分抵扣，剩余 70% 优先使用账户余额；余额不足的差额走微信或支付宝支付。不能使用商品积分。' },
  consumeProduct: { code: 'CONSUME', label: '消费区', prefix: 'consume', rule: '消费区商品只能用商品积分购买，不能使用复购积分、余额或现金。' },
} as const
const currentZone = computed(() => ZONES[route.name as keyof typeof ZONES] ?? ZONES.pickProduct)
const zone = computed(() => currentZone.value.code)
const zoneLabel = computed(() => currentZone.value.label)
const zoneRule = computed(() => currentZone.value.rule)
const keyword = ref('')
const assignedOnly = ref(false)
const archivedOnly = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
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
    const params = { keyword: keyword.value, assignedOnly: assignedOnly.value, archivedOnly: archivedOnly.value }
    const [res, count] = await Promise.all([http<ZoneProduct[]>({
      url: '/p0/zones/' + zone.value + '/products',
      params: { ...params, page: page.value, size: size.value },
    }), http<{ total: number }>({ url: '/p0/zones/' + zone.value + '/products/count', params })])
    if (revision === loadRevision) {
      rows.value = res.data || []
      total.value = Number(count.data?.total || 0)
    }
  } catch {
    if (revision === loadRevision) ElMessage.error('商品列表加载失败，请重试')
  } finally {
    if (revision === loadRevision) loading.value = false
  }
}

watch(zone, () => {
  keyword.value = ''
  assignedOnly.value = false
  archivedOnly.value = false
  page.value = 1
  load()
})
onMounted(load)

function search() { page.value = 1; load() }

async function restore(row: ZoneProduct) {
  try {
    await ElMessageBox.confirm(`确认恢复商品「${row.name}」（ID ${row.id}）？恢复后仍保持原上下架状态。`, '恢复归档商品', { type: 'warning' })
  } catch { return }
  busy.value = row.id
  try {
    await productUpdateDeleteStatusAPI({ ids: String(row.id), deleteStatus: 0 })
    ElMessage.success('商品已恢复，请切回可管理商品列表')
    await load()
  } catch { ElMessage.error('恢复失败') }
  finally { busy.value = null }
}

function goAdd() {
  router.push({ name: `${currentZone.value.prefix}AddProduct` })
}
function goEdit(row: ZoneProduct) {
  router.push({ name: `${currentZone.value.prefix}UpdateProduct`, query: { id: row.id } })
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
        <el-input v-model="keyword" placeholder="搜索商品名称或货号" clearable style="max-width: 280px" @keyup.enter="search" />
        <el-checkbox v-model="assignedOnly" @change="search">仅看已加入{{ zoneLabel }}</el-checkbox>
        <el-checkbox v-model="archivedOnly" @change="search">查看已归档商品</el-checkbox>
        <el-button type="primary" @click="search">查询</el-button>
      </div>
      <el-alert type="info" :closable="false">
        <template #title>
          <span>{{ zoneRule }}</span>
        </template>
        <span>商品可以分别归属自选区、复购区、消费区，各专区结算规则互不串用；下架商品仍可管理，但不会在商城显示。</span>
      </el-alert>
      <el-table :data="rows" v-loading="loading" empty-text="暂无符合条件的商品">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="图片" width="86">
          <template #default="{ row }">
            <span v-if="!row.pic">暂无图片</span>
            <el-image v-else :src="productImageUrl(row.pic)" fit="contain" style="width: 52px; height: 52px"><template #error>加载失败</template></el-image>
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
            <el-tag v-if="isAssigned(row)" type="warning">{{ row.publishStatus === 1 && !row.deleteStatus ? '已加入' : '已加入（商城不展示）' }}</el-tag>
            <span v-else>未加入</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.deleteStatus === 1" link type="primary" :disabled="busy === row.id" @click="restore(row)">恢复商品</el-button>
            <template v-else>
            <el-button link type="primary" :disabled="busy === row.id" @click="goEdit(row)">修改</el-button>
            <el-button link type="primary" :disabled="busy === row.id" @click="togglePublish(row)">
              {{ row.publishStatus === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="primary" :disabled="busy === row.id" @click="setFlag(row, 'new')">设为新品</el-button>
            <el-button link type="primary" :disabled="busy === row.id" @click="setFlag(row, 'recommend')">设为推荐</el-button>
            <el-button v-if="isAssigned(row)" link type="danger" :disabled="busy === row.id" @click="remove(row)">移出</el-button>
            <el-button v-else link type="success" :disabled="busy === row.id" @click="assign(row)">加入专区</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" style="justify-content:flex-end;margin-top:16px" @current-change="load" @size-change="() => { page = 1; load() }" />
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

