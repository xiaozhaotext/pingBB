<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import { uploadProductImage } from '@/apis/productImage'
import { productImageUrl } from '@/utils/productImage'

type Theme = { id?: number; name: string; imageUrl: string; description: string; enabled: boolean | number; sortOrder: number }
type Destination = { id?: number; themeId: number; name: string; imageUrl: string; description: string; enabled: boolean | number; sortOrder: number }
type Route = { id?: number; destinationId: number; name: string; imageUrl: string; description: string; productId: number; productName?: string; productPic?: string; price?: number; enabled: boolean | number; sortOrder: number }
type Product = { id: number; name: string; pic: string; price: number; publishStatus: number }

const endpoint = '/productCategory/consumeRoute'
const themes = ref<Theme[]>([])
const destinations = ref<Destination[]>([])
const routes = ref<Route[]>([])
const loading = ref(false)
const busy = ref(false)
const uploading = ref(false)
const activeTheme = ref<number>(0)
const visibleDestinations = computed(() => destinations.value.filter(d => !activeTheme.value || d.themeId === activeTheme.value))

const themeDialog = ref(false)
const themeBlank = (): Theme => ({ name: '', imageUrl: '', description: '', enabled: true, sortOrder: 0 })
const themeForm = ref<Theme>(themeBlank())
const destDialog = ref(false)
const destBlank = (): Destination => ({ themeId: 0, name: '', imageUrl: '', description: '', enabled: true, sortOrder: 0 })
const destForm = ref<Destination>(destBlank())
const routeDialog = ref(false)
const routeBlank = (): Route => ({ destinationId: 0, name: '', imageUrl: '', description: '', productId: 0 as number, enabled: true, sortOrder: 0 })
const routeForm = ref<Route>(routeBlank())
const options = ref<Product[]>([])
const searching = ref(false)
let searchVersion = 0

async function loadThemes() {
  try { themes.value = (await http.get<Theme[]>(`${endpoint}/themes`)).data || []; if (!activeTheme.value && themes.value.length) activeTheme.value = themes.value[0]!.id || 0 }
  catch { ElMessage.error('主题加载失败') }
}
async function loadDestinations() {
  try { destinations.value = (await http.get<Destination[]>(`${endpoint}/destinations`)).data || [] }
  catch { ElMessage.error('目的地加载失败') }
}
async function loadRoutes() {
  try { routes.value = (await http.get<Route[]>(`${endpoint}/routes`)).data || [] }
  catch { ElMessage.error('线路加载失败') }
}
async function refresh() { loading.value = true; await Promise.all([loadThemes(), loadDestinations(), loadRoutes()]); loading.value = false }

function editTheme(row?: Theme) { themeForm.value = { ...(row || themeBlank()), enabled: Boolean(row ? row.enabled : true) }; themeDialog.value = true }
function editDest(row?: Destination) {
  if (!themes.value.length) { ElMessage.warning('请先新增主题'); return }
  destForm.value = { ...(row || { ...destBlank(), themeId: activeTheme.value || themes.value[0]!.id || 0 }), enabled: Boolean(row ? row.enabled : true) }
  destDialog.value = true
}
function editRoute(row?: Route, presetDest?: number) {
  if (!destinations.value.length) { ElMessage.warning('请先新增目的地'); return }
  routeForm.value = { ...(row || { ...routeBlank(), destinationId: presetDest || visibleDestinations.value[0]?.id || destinations.value[0]!.id || 0 }), enabled: Boolean(row ? row.enabled : true) }
  routeDialog.value = true
  options.value = []
  if (routeForm.value.productId) {
    const known = routes.value.find(r => r.productId === routeForm.value.productId)
    if (known) options.value = [{ id: known.productId, name: known.productName || String(known.productId), pic: known.productPic || '', price: Number(known.price) || 0, publishStatus: 1 }]
  }
  search('')
}
async function search(keyword: string) {
  const version = ++searchVersion
  searching.value = true
  try {
    const result = await http.get<Product[]>(`${endpoint}/products`, { params: { keyword } })
    if (version === searchVersion) {
      const fetched = result.data || []
      options.value = [...options.value, ...fetched.filter(p => !options.value.some(k => k.id === p.id))]
    }
  } catch { if (version === searchVersion) ElMessage.error('商品搜索失败') }
  finally { if (version === searchVersion) searching.value = false }
}
async function upload(event: Event, form: { imageUrl: string }) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) { ElMessage.warning('请上传10MB以内的 JPG/PNG/WebP 图片'); input.value = ''; return }
  uploading.value = true
  try { form.imageUrl = await uploadProductImage(file); ElMessage.success('封面已上传，请保存') }
  catch { ElMessage.error('图片上传失败') }
  finally { uploading.value = false; input.value = '' }
}
async function saveTheme() {
  if (busy.value || uploading.value) return
  if (!themeForm.value.name.trim()) { ElMessage.warning('请填写主题名称'); return }
  busy.value = true
  try { await http.post(`${endpoint}/theme`, { ...themeForm.value, enabled: Boolean(themeForm.value.enabled) }); ElMessage.success('主题已保存'); themeDialog.value = false; await refresh() }
  catch { ElMessage.error('主题保存失败') }
  finally { busy.value = false }
}
async function saveDest() {
  if (busy.value || uploading.value) return
  if (!destForm.value.name.trim() || !destForm.value.themeId) { ElMessage.warning('请填写目的地名称并选择主题'); return }
  busy.value = true
  try { await http.post(`${endpoint}/destination`, { ...destForm.value, enabled: Boolean(destForm.value.enabled) }); ElMessage.success('目的地已保存'); destDialog.value = false; await refresh() }
  catch { ElMessage.error('目的地保存失败') }
  finally { busy.value = false }
}
async function saveRoute() {
  if (busy.value || uploading.value) return
  const f = routeForm.value
  if (!f.destinationId || !f.productId) { ElMessage.warning('请选择归属目的地与关联商品'); return }
  busy.value = true
  try { await http.post(`${endpoint}/route`, { ...f, enabled: Boolean(f.enabled) }); ElMessage.success('路线已保存'); routeDialog.value = false; await refresh() }
  catch { ElMessage.error('路线保存失败') }
  finally { busy.value = false }
}
async function remove(kind: 'theme' | 'destination' | 'route', row: any) {
  if (!row?.id) return
  try { await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除确认', { type: 'warning' }) } catch { return }
  try { await http.delete(`${endpoint}/${kind}/${row.id}`); ElMessage.success('已删除'); await refresh() }
  catch (e: any) { ElMessage.error(e?.response?.data?.message || e?.message || '删除失败') }
}
function themeName(id: number) { return themes.value.find(t => t.id === id)?.name || '-' }
function destName(id: number) { return destinations.value.find(d => d.id === id)?.name || '-' }
onMounted(refresh)
</script>
<template>
  <div class="page">
    <div class="heading">
      <div><h2>消费区 · 旅游路线配置</h2><p>三级展示：主题（海南旅游）→ 目的地（三亚）→ 路线商品（点进商品详情购买）。封面、归属、排序、启停均可配置；不展示价格。</p></div>
      <div class="actions"><el-button :loading="loading" @click="refresh">刷新</el-button><el-button type="primary" @click="editTheme()">新增主题</el-button></div>
    </div>

    <el-card shadow="never" class="block">
      <template #header>主题（消费区首页第一层）</template>
      <el-table v-loading="loading" :data="themes" border size="small">
        <el-table-column label="封面" width="120"><template #default="{ row }"><img class="cover" :src="productImageUrl(row.imageUrl)" alt="" /></template></el-table-column>
        <el-table-column prop="name" label="主题名称" min-width="140" />
        <el-table-column prop="description" label="简介" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序(大值优先)" width="140" />
        <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button @click="editTheme(row)">编辑</el-button>
            <el-button type="primary" plain @click="editDest(undefined); destForm.themeId = row.id!">加目的地</el-button>
            <el-button type="danger" plain @click="remove('theme', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-header">目的地（第二层）
          <el-select v-model="activeTheme" placeholder="全部主题" clearable style="width:180px"><el-option v-for="t in themes" :key="t.id" :label="t.name" :value="t.id!" /></el-select>
          <el-button type="primary" plain @click="editDest()">新增目的地</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="visibleDestinations" border size="small">
        <el-table-column label="封面" width="120"><template #default="{ row }"><img class="cover" :src="productImageUrl(row.imageUrl)" alt="" /></template></el-table-column>
        <el-table-column prop="name" label="目的地" min-width="130" />
        <el-table-column label="归属主题" width="120"><template #default="{ row }">{{ themeName(row.themeId) }}</template></el-table-column>
        <el-table-column prop="description" label="简介" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="80"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button @click="editDest(row)">编辑</el-button>
            <el-button type="primary" plain @click="editRoute(undefined, row.id)">加路线</el-button>
            <el-button type="danger" plain @click="remove('destination', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>路线商品（第三层 · 点进商品详情购买，不展示价格）</template>
      <el-table v-loading="loading" :data="routes" border size="small">
        <el-table-column label="封面" width="120"><template #default="{ row }"><img class="cover" :src="productImageUrl(row.imageUrl || row.productPic)" alt="" /></template></el-table-column>
        <el-table-column label="路线名称" min-width="160"><template #default="{ row }"><span>{{ row.name || row.productName }}</span></template></el-table-column>
        <el-table-column label="归属目的地" width="120"><template #default="{ row }">{{ destName(row.destinationId) }}</template></el-table-column>
        <el-table-column label="关联商品" min-width="180"><template #default="{ row }"><span>{{ row.productName || row.productId }}</span></template></el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column label="状态" width="80"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }"><el-button @click="editRoute(row)">编辑</el-button><el-button type="danger" plain @click="remove('route', row)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="themeDialog" title="主题" width="min(640px, 96vw)" :close-on-click-modal="false">
      <el-form label-position="top" :disabled="busy || uploading">
        <el-form-item label="主题名称" required><el-input v-model="themeForm.name" maxlength="120" show-word-limit placeholder="例如：海南旅游" /></el-form-item>
        <el-form-item label="封面"><img v-if="themeForm.imageUrl" class="preview" :src="productImageUrl(themeForm.imageUrl)" /><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy || uploading" @change="upload($event, themeForm)" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="themeForm.description" maxlength="500" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="themeForm.sortOrder" :min="0" :max="999999" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="themeForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy || uploading" @click="themeDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveTheme">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="destDialog" title="目的地" width="min(640px, 96vw)" :close-on-click-modal="false">
      <el-form label-position="top" :disabled="busy || uploading">
        <el-form-item label="归属主题" required><el-select v-model="destForm.themeId"><el-option v-for="t in themes" :key="t.id" :label="t.name" :value="t.id!" /></el-select></el-form-item>
        <el-form-item label="目的地名称" required><el-input v-model="destForm.name" maxlength="120" show-word-limit placeholder="例如：三亚" /></el-form-item>
        <el-form-item label="封面"><img v-if="destForm.imageUrl" class="preview" :src="productImageUrl(destForm.imageUrl)" /><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy || uploading" @change="upload($event, destForm)" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="destForm.description" maxlength="500" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="destForm.sortOrder" :min="0" :max="999999" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="destForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy || uploading" @click="destDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveDest">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="routeDialog" title="路线商品" width="min(720px, 96vw)" :close-on-click-modal="false">
      <el-form label-position="top" :disabled="busy || uploading">
        <el-form-item label="归属目的地" required><el-select v-model="routeForm.destinationId"><el-option v-for="d in destinations" :key="d.id" :label="d.name" :value="d.id!" /></el-select></el-form-item>
        <el-form-item label="路线名称（可选，默认用商品名）"><el-input v-model="routeForm.name" maxlength="120" show-word-limit placeholder="例如：海南环岛5天4夜" /></el-form-item>
        <el-form-item label="封面（可选，默认用商品图）"><img v-if="routeForm.imageUrl" class="preview" :src="productImageUrl(routeForm.imageUrl)" /><input type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy || uploading" @change="upload($event, routeForm)" /></el-form-item>
        <el-form-item label="简介/行程亮点"><el-input v-model="routeForm.description" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        <el-form-item label="关联商品（点路线进此商品详情）" required><el-select v-model="routeForm.productId" filterable remote :remote-method="search" :loading="searching" placeholder="搜索商品"><el-option v-for="p in options" :key="p.id" :label="`${p.name}（${p.id}）`" :value="p.id" /></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="routeForm.sortOrder" :min="0" :max="999999" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="routeForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy || uploading" @click="routeDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveRoute">保存</el-button></template>
    </el-dialog>
  </div>
</template>
<style scoped>
.page{padding:24px}.heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.heading p{color:#667085;font-size:13px}.actions{display:flex;gap:10px}.block{margin-bottom:20px}.card-header{display:flex;align-items:center;gap:12px}.cover{width:90px;height:52px;object-fit:cover}.preview{display:block;width:100%;max-width:420px;max-height:240px;object-fit:contain;margin-bottom:12px}@media(max-width:650px){.heading{align-items:flex-start;flex-direction:column}}
</style>
