<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'
import { uploadProductImage } from '@/apis/productImage'
import { productImageUrl } from '@/utils/productImage'

type Product = { id: number; name: string; pic: string; publishStatus: number }
type Activity = { id?: number; title: string; imageUrl: string; rules: string; enabled: boolean | number; sortOrder: number; products?: Product[] }
const endpoint = '/productCategory/pickActivity'
const rows = ref<Activity[]>([])
const loading = ref(false)
const loadError = ref(false)
const dialog = ref(false)
const busy = ref(false)
const uploading = ref(false)
const searching = ref(false)
const options = ref<Product[]>([])
const selected = ref<Product[]>([])
const productId = ref<number>()
const blank = (): Activity => ({ title: '', imageUrl: '', rules: '', enabled: false, sortOrder: 0 })
const form = ref<Activity>(blank())
let searchVersion = 0
async function refresh() {
  loading.value = true
  loadError.value = false
  try { rows.value = (await http.get<Activity[]>(endpoint)).data || [] }
  catch { loadError.value = true }
  finally { loading.value = false }
}
async function edit(row?: Activity) {
  busy.value = true
  try {
    const value = row ? (await http.get<Activity>(`${endpoint}/${row.id}`)).data : blank()
    form.value = { ...value, enabled: Boolean(value.enabled) }
    selected.value = [...(value.products || [])]
    options.value = []
    productId.value = undefined
    dialog.value = true
    await search('')
  } catch { ElMessage.error('活动详情加载失败，请重试') }
  finally { busy.value = false }
}
async function search(keyword: string) {
  const version = ++searchVersion
  searching.value = true
  try {
    const result = await http.get<Product[]>(`${endpoint}/products`, { params: { keyword } })
    if (version === searchVersion) options.value = result.data || []
  } catch { if (version === searchVersion) ElMessage.error('商品搜索失败，请重试') }
  finally { if (version === searchVersion) searching.value = false }
}
function addProduct() {
  const product = options.value.find(item => item.id === productId.value)
  if (!product) return
  if (selected.value.some(item => item.id === product.id)) { ElMessage.warning('该商品已经添加'); return }
  if (selected.value.length >= 50) { ElMessage.warning('最多关联50件正品'); return }
  selected.value.push(product)
  productId.value = undefined
}
function move(index: number, step: number) {
  const next = index + step
  if (next < 0 || next >= selected.value.length) return
  const item = selected.value.splice(index, 1)[0]!
  selected.value.splice(next, 0, item)
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
    ElMessage.warning('请上传10MB以内的 JPG、PNG 或 WebP 图片'); input.value = ''; return
  }
  uploading.value = true
  try { form.value.imageUrl = await uploadProductImage(file); ElMessage.success('图片已上传，请保存活动') }
  catch { ElMessage.error('图片上传失败，请重试') }
  finally { uploading.value = false; input.value = '' }
}
async function save() {
  if (busy.value || uploading.value) return
  if (!form.value.title.trim() || !form.value.imageUrl || !form.value.rules.trim() || !selected.value.length) {
    ElMessage.warning('请填写活动名称、图片、规则，并至少选择一件正品'); return
  }
  busy.value = true
  try {
    await http.post(endpoint, { ...form.value, products: undefined, enabled: Boolean(form.value.enabled), productIds: selected.value.map(item => item.id) })
    ElMessage.success('活动已保存'); dialog.value = false; await refresh()
  } catch { ElMessage.error('活动保存失败，请检查商品归属后重试') }
  finally { busy.value = false }
}
onMounted(refresh)
</script>
<template>
  <div class="activity-admin">
    <div class="heading"><div><h2>分类管理 · 旅游活动</h2><p>配置自选区活动标签页、活动规则和可选旅游正品。商品图文、价格、库存仍在商品列表维护。</p></div><el-button type="primary" :disabled="busy" @click="edit()">新增活动</el-button></div>
    <el-alert v-if="loadError" title="活动加载失败，请重试" type="error" :closable="false"><el-button @click="refresh">重试</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="title" label="活动标签名称" />
      <el-table-column label="活动图片" width="150"><template #default="{ row }"><img class="cover" :src="productImageUrl(row.imageUrl)" :alt="row.title" /></template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.enabled ? '已发布' : '已停用' }}</template></el-table-column>
      <el-table-column prop="sortOrder" label="排序（大值优先）" width="150" />
      <el-table-column label="操作" width="120"><template #default="{ row }"><el-button :disabled="busy" @click="edit(row)">编辑</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="form.id ? '编辑旅游活动' : '新增旅游活动'" width="min(900px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy && !uploading" :show-close="!busy && !uploading">
      <el-form label-position="top" :disabled="busy || uploading">
        <el-form-item label="活动标签名称" required><el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：旅游活动详情1" /></el-form-item>
        <el-form-item label="活动顶部图片" required><img v-if="form.imageUrl" class="preview" :src="productImageUrl(form.imageUrl)" /><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy || uploading" @change="upload" /><span v-if="uploading">上传中…</span></el-form-item>
        <el-form-item label="区域1：活动规则详情" required><el-input v-model="form.rules" type="textarea" :rows="6" maxlength="20000" show-word-limit /></el-form-item>
        <el-form-item label="区域2：旅游正品选择（按下方顺序展示，默认首件可售商品）" required>
          <div class="product-search"><el-select v-model="productId" filterable remote :remote-method="search" :loading="searching" placeholder="搜索自选区已有商品"><el-option v-for="item in options" :key="item.id" :value="item.id" :label="`${item.name}（${item.id}）`" /></el-select><el-button @click="addProduct">添加正品</el-button></div>
          <div class="selected-products"><div v-for="(item, index) in selected" :key="item.id" class="product-row"><span>{{ index + 1 }}. {{ item.name }} <small v-if="item.publishStatus !== 1">（未上架）</small></span><el-button :disabled="index === 0" @click="move(index, -1)">上移</el-button><el-button :disabled="index === selected.length - 1" @click="move(index, 1)">下移</el-button><el-button type="danger" plain @click="selected.splice(index, 1)">移除关联</el-button></div></div>
        </el-form-item>
        <el-form-item label="活动排序"><el-input-number v-model="form.sortOrder" :min="0" :max="999999" :precision="0" /></el-form-item>
        <el-form-item label="发布状态"><el-switch v-model="form.enabled" active-text="发布" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy || uploading" @click="dialog = false">取消</el-button><el-button type="primary" :loading="busy" :disabled="uploading" @click="save">保存活动</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>
.activity-admin{padding:24px}.heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.heading p{color:#667085}.cover{width:110px;height:65px;object-fit:cover}.preview{display:block;width:100%;max-width:480px;max-height:280px;object-fit:contain;margin-bottom:12px}.product-search{display:flex;gap:10px;width:100%}.product-search .el-select{flex:1}.selected-products{width:100%;margin-top:14px}.product-row{display:flex;align-items:center;gap:8px;padding:10px 0;border-bottom:1px solid #eee}.product-row span{flex:1}.product-row small{color:#b46a1e}@media(max-width:650px){.heading{align-items:start;flex-direction:column}.product-row{flex-wrap:wrap}.product-row span{flex-basis:100%}}
</style>
