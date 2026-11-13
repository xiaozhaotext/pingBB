<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import { uploadProductImage } from '@/apis/productImage'

type Gift = { id: number; name: string; imageUrl: string; enabled: boolean }
type CatalogProduct = { id: number; name: string; pic?: string }
const gifts = ref<Gift[]>(Array.from({ length: 4 }, (_, index) => ({ id: index + 1, name: '', imageUrl: '', enabled: false })))
const products = ref<CatalogProduct[]>([])
const selectedProduct = ref<Record<number, number>>({})
const busy = ref<number | null>(null)
function catalogImage(pic?: string) {
  const value = String(pic || '').trim()
  if (/^https?:\/\/macro-oss\.oss-cn-shenzhen\.aliyuncs\.com\//i.test(value))
    return value.replace(/^https?:\/\/macro-oss\.oss-cn-shenzhen\.aliyuncs\.com\//i, '/oss/')
  if (/^https?:\/\/zhiyoumei\.com\.cn\/product-images\//i.test(value))
    return new URL(value).pathname
  return value
}
async function searchProducts(keyword: string) {
  if (!keyword.trim()) { products.value = []; return }
  const result = await http.get('/product/list', { params: { keyword, pageNum: 1, pageSize: 20 } }) as unknown as { data: { list: CatalogProduct[] } }
  products.value = result.data?.list || []
}
function matchProduct(gift: Gift, productId: number) {
  const product = products.value.find(item => item.id === productId)
  if (!product) return
  const image = catalogImage(product.pic)
  if (!image.startsWith('/product-images/') && !image.startsWith('/oss/')) {
    ElMessage.warning('这个商品的图片不能直接复用，请手动上传赠品图片'); return
  }
  gift.name = product.name
  gift.imageUrl = image
  ElMessage.success('已带入商品名称和图片，记得保存')
}
async function refresh() {
  const result = await http.get('/p0/gift-options') as unknown as { data: Gift[] }
  const byId = new Map((result.data || []).map(gift => [Number(gift.id), gift]))
  gifts.value = gifts.value.map(gift => byId.get(gift.id) || gift)
}
async function upload(gift: Gift, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.type.startsWith('image/') || file.size > 100 * 1024 * 1024) {
    ElMessage.warning('请选择不超过100MB的图片'); return
  }
  busy.value = gift.id
  try { gift.imageUrl = await uploadProductImage(file); ElMessage.success('图片已上传，记得保存赠品') }
  catch { ElMessage.error('图片上传失败') }
  finally { busy.value = null }
}
async function save(gift: Gift) {
  if (!gift.name.trim() || !gift.imageUrl) { ElMessage.warning('请填写名称并上传图片'); return }
  try { await ElMessageBox.confirm(`确认保存赠品${gift.id}？`, '保存赠品') }
  catch { return }
  busy.value = gift.id
  try { await http.post('/p0/gift-options', gift); ElMessage.success('已保存'); await refresh() }
  catch { ElMessage.error('保存失败') }
  finally { busy.value = null }
}
onMounted(refresh)
</script>

<template>
  <div class="gift-page"><h2>赠品商品</h2>
    <p>每笔3999元自选区路线订单可从四件赠品中选一件。四个赠品位全部启用后，结算页才会要求选择。已下单的赠品记录保留当时的名称和图片。</p>
    <div class="gift-table"><div v-for="gift in gifts" :key="gift.id" class="gift-row">
      <strong>赠品 {{ gift.id }}</strong>
      <img v-if="gift.imageUrl" :src="gift.imageUrl" :alt="gift.name" />
      <div v-else class="placeholder">暂无图片</div>
      <div class="fields">
        <el-select v-model="selectedProduct[gift.id]" filterable remote clearable :remote-method="searchProducts" placeholder="搜索已有商品，自动带入名称和图片" @change="matchProduct(gift, Number($event))">
          <el-option v-for="product in products" :key="product.id" :label="product.name" :value="product.id" />
        </el-select>
        <el-input v-model="gift.name" maxlength="120" placeholder="赠品名称" />
      </div>
      <label class="upload">上传图片<input type="file" accept="image/*" :disabled="busy !== null" @change="upload(gift, $event)" /></label>
      <el-switch v-model="gift.enabled" active-text="启用" inactive-text="停用" />
      <el-button type="primary" :loading="busy === gift.id" @click="save(gift)">保存</el-button>
    </div></div>
  </div>
</template>

<style scoped>
.gift-page{padding:24px}.gift-page p{color:#667085;line-height:1.6}.gift-table{max-width:1200px;background:#fff;border:1px solid #e5e7eb;border-radius:12px}.gift-row{display:grid;grid-template-columns:72px 96px minmax(240px,1fr) 90px 90px 66px;gap:16px;align-items:center;padding:14px;border-bottom:1px solid #eee}.gift-row:last-child{border-bottom:0}img,.placeholder{width:96px;height:72px;object-fit:cover;border-radius:6px;background:#f5f6f7}.placeholder{display:grid;place-items:center;color:#888;font-size:12px}.fields{display:grid;gap:8px;min-width:0}.upload{display:inline-block;padding:8px 10px;background:#a87b35;color:white;border-radius:6px;cursor:pointer;text-align:center;white-space:nowrap}.upload input{display:none}@media(max-width:800px){.gift-row{grid-template-columns:72px 96px 1fr}.fields{grid-column:1/-1}.upload{grid-column:1}.gift-row .el-switch{grid-column:2}.gift-row .el-button{grid-column:3;justify-self:end}}
</style>
