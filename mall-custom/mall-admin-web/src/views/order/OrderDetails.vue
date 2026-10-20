<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getOrderListAPI } from '@/apis/order'
import { formatDateTime } from '@/utils/datetime'
import { orderFundsAmount } from '@/utils/orderDisplay'
import type { OmsOrder, OrderQueryParam } from '@/types/order'

const router = useRouter()
const loading = ref(false)
const rows = ref<OmsOrder[]>([])
const total = ref(0)
const query = ref<OrderQueryParam>({ pageNum: 1, pageSize: 10, orderSn: '' })

const statusLabel = (status: number) => ({ 0: '待付款', 1: '待发货', 2: '待收货', 3: '已完成', 4: '已关闭', 5: '无效订单' } as Record<number, string>)[status] || '未知'

async function load() {
  loading.value = true
  try {
    const response = await getOrderListAPI(query.value)
    rows.value = response.data.list
    total.value = response.data.total
  } finally {
    loading.value = false
  }
}

function openOrder(row: OmsOrder) {
  router.push({ path: '/orderMgr/orderDetail', query: { id: row.id } })
}

onMounted(load)
</script>

<template>
  <main class="order-details" v-loading="loading">
    <header><div><span>订单管理</span><h1>订单详情</h1><p>选择订单查看商品信息、买家信息和订单处理记录。</p></div></header>
    <el-card shadow="never" class="search-card">
      <el-input v-model="query.orderSn" clearable placeholder="输入订单编号" @keyup.enter="query.pageNum = 1; load()" />
      <el-button type="primary" @click="query.pageNum = 1; load()">查询订单</el-button>
    </el-card>
    <el-table :data="rows" border stripe>
      <el-table-column prop="orderSn" label="订单编号" min-width="190" />
      <el-table-column prop="memberUsername" label="买家账号" min-width="140" />
      <el-table-column label="商品总额" min-width="130"><template #default="scope">￥{{ Number(scope.row.totalAmount).toFixed(2) }}</template></el-table-column>
      <el-table-column label="资金应付（含余额）" min-width="165"><template #default="scope">￥{{ orderFundsAmount(scope.row).toFixed(2) }}</template></el-table-column>
      <el-table-column label="下单时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column>
      <el-table-column label="详情" width="120" fixed="right"><template #default="scope"><el-button link type="primary" @click="openOrder(scope.row)">商品 / 买家信息</el-button></template></el-table-column>
    </el-table>
    <el-pagination class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-size="query.pageSize" :current-page="query.pageNum" :page-sizes="[10, 20, 50]" @current-change="page => { query.pageNum = page; load() }" @size-change="size => { query.pageSize = size; query.pageNum = 1; load() }" />
  </main>
</template>

<style scoped>
.order-details{min-height:100vh;padding:28px;background:#f5f7f5}.order-details header{margin-bottom:18px}.order-details header span{color:#56826a;letter-spacing:.12em}.order-details h1{margin:6px 0;color:#203a32}.order-details p{margin:0;color:#718078}.search-card{margin-bottom:16px}.search-card :deep(.el-card__body){display:flex;gap:12px}.search-card :deep(.el-input){max-width:360px}.pagination{justify-content:flex-end;margin-top:18px}
</style>
