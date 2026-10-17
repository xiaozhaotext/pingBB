<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productImageUrl } from '@/utils/productImage'
type Zone = 'PICK' | 'REBUY' | 'CONSUME'
interface Product { id:number; name:string; pic:string; price:number; publishStatus:number; assigned:boolean|number; sortOrder:number }
const zone=ref<Zone>('PICK'),keyword=ref(''),assignedOnly=ref(false),loading=ref(false),saving=ref<number|null>(null)
const rows=ref<Product[]>([])
async function load(){
  rows.value=[]
  loading.value=true
  try { rows.value=(await http<Product[]>({url:`/p0/zones/${zone.value}/products`,params:{keyword:keyword.value,assignedOnly:assignedOnly.value}})).data }
  finally { loading.value=false }
}
async function save(row:Product){
  saving.value=row.id
  try { await http({url:`/p0/zones/${zone.value}/products`,method:'post',data:{productId:row.id,sortOrder:row.sortOrder}}); ElMessage.success('专区归属已保存'); await load() }
  finally { saving.value=null }
}
async function remove(row:Product){
  await ElMessageBox.confirm('仅移除此专区归属，保留商品本身及其他专区归属。','移出专区')
  saving.value=row.id
  try {await http({url:`/p0/zones/${zone.value}/products/${row.id}`,method:'delete'});ElMessage.success('已移出专区');await load()}
  finally {saving.value=null}
}
watch(zone,()=>{keyword.value='';load()})
onMounted(load)
</script>
<template>
  <main class="zones-admin">
    <header><small>商品管理 · 首页专区</small><h1>三个专区，清晰管理</h1><p>自选区用现金或余额，复购区用复购积分加现金，消费区用商品积分，各专区结算规则互不串用。</p></header>
    <el-card shadow="never">
      <el-tabs v-model="zone"><el-tab-pane label="品牌区（自选区）" name="PICK"/><el-tab-pane label="复购区" name="REBUY"/><el-tab-pane label="消费区" name="CONSUME"/></el-tabs>
      <template>
        <div class="filters"><el-input v-model="keyword" placeholder="搜索商品名称或货号" clearable @keyup.enter="load"/><el-checkbox v-model="assignedOnly" @change="load">仅看已分配商品</el-checkbox><el-button type="primary" @click="load">查询</el-button></div>
        <el-alert title="商品可以分别归属自选区、复购区、消费区；排序数字越大越靠前。下架商品可管理，但不会在商城显示。" type="info" :closable="false"/>
        <el-table :data="rows" v-loading="loading" empty-text="暂无符合条件的商品">
          <el-table-column prop="id" label="ID" width="75"/><el-table-column label="图片" width="90"><template #default="{row}"><span v-if="!row.pic">暂无图片</span><el-image v-else :src="productImageUrl(row.pic)" fit="contain" style="width:56px;height:56px"><template #error>加载失败</template></el-image></template></el-table-column>
          <el-table-column prop="name" label="商品名称" min-width="240"/><el-table-column prop="price" label="价格" width="100"/>
          <el-table-column label="归属" width="110"><template #default="{row}"><el-tag :type="row.assigned?'success':'info'">{{row.assigned?'已分配':'未分配'}}</el-tag></template></el-table-column>
          <el-table-column label="排序" width="170"><template #default="{row}"><el-input-number v-model="row.sortOrder" :min="-10000" :max="10000" size="small"/></template></el-table-column>
          <el-table-column label="操作" width="190"><template #default="{row}"><el-button type="primary" link :loading="saving===row.id" @click="save(row)">{{row.assigned?'保存排序':'加入专区'}}</el-button><el-button v-if="row.assigned" type="danger" link :disabled="saving!==null" @click="remove(row)">移出</el-button></template></el-table-column>
        </el-table><p class="note">最多显示 100 条，请用搜索缩小范围。</p>
      </template>
    </el-card>
  </main>
</template>
<style scoped>
.zones-admin{padding:28px;background:#f7f3eb;min-height:100vh}.zones-admin header{margin-bottom:24px;padding:28px;border-radius:20px;background:linear-gradient(110deg,#f5e5b9,#e2ecdf)}h1{font-size:28px;color:#304f45;margin:12px 0}p,small{color:#7b806d}.filters{display:flex;gap:18px;align-items:center;margin:20px 0}.filters .el-input{width:320px}.note{font-size:12px;margin-top:20px}
</style>
