<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getP0 } from '@/apis/p0'

type Asset = {
  url: string; source: string; sourceLabel: string
  directory: string; fileName: string; extension: string; scheme: string
}
type Folder = { directory: string; count: number; sources: Record<string, number> }
type Stats = {
  total: number; bySource: Record<string, number>; byScheme: Record<string, number>
  byExtension: Record<string, number>; directoryCount: number; nonHttps: number
}

const modes = [
  { value: 'list', label: '列表模式' },
  { value: 'grid', label: '图文模式' },
  { value: 'folder', label: '文件夹模式' },
  { value: 'stats', label: '图片统计' },
]
const mode = ref('list')
const keyword = ref('')
const loading = ref(false)
const assets = ref<Asset[]>([])
const folders = ref<Folder[]>([])
const stats = ref<Stats | null>(null)
const note = ref('')
const page = ref(1)
const size = ref(60)
const total = ref(0)

const gridUrls = computed(() => assets.value.map(asset => displayUrl(asset.url)))
const statRows = computed(() => {
  if (!stats.value) return { bySource: [], byScheme: [], byExtension: [] }
  const rows = (record: Record<string, number>) => Object.entries(record)
    .map(([name, count]) => ({ name, count })).sort((left, right) => right.count - left.count)
  return {
    bySource: rows(stats.value.bySource),
    byScheme: rows(stats.value.byScheme),
    byExtension: rows(stats.value.byExtension),
  }
})
const statGroups = computed(() => [
  { title: '按来源', rows: statRows.value.bySource },
  { title: '按协议', rows: statRows.value.byScheme },
  { title: '按扩展名', rows: statRows.value.byExtension },
])

function displayUrl(url: string): string {
  return url.startsWith('/') ? `${location.origin}${url}` : url
}

async function load() {
  loading.value = true
  try {
    const response = await getP0<Record<string, unknown>>('system-settings/image-assets', {
      mode: mode.value, keyword: keyword.value, page: page.value, size: size.value,
    })
    const data = response.data ?? {}
    assets.value = (data.assets as Asset[] | undefined) ?? []
    folders.value = (data.folders as Folder[] | undefined) ?? []
    stats.value = (data.stats as Stats | undefined) ?? null
    total.value = Number(data.total ?? 0)
    note.value = String(data.note ?? '')
  } finally {
    loading.value = false
  }
}

async function copy(url: string) {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('已复制图片地址')
  } catch {
    ElMessage.warning('当前浏览器不允许自动复制，请手动选择地址')
  }
}

function search() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <main class="images">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <h2>图片管理</h2>
          <span class="hint">{{ note }}</span>
        </div>
      </template>
      <div class="toolbar">
        <el-radio-group v-model="mode" @change="search">
          <el-radio-button v-for="item in modes" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" class="search" placeholder="按地址或文件名筛选" clearable @keyup.enter="search" />
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>

      <div v-loading="loading" class="pane">
        <template v-if="mode === 'stats'">
          <div class="stat-cards">
            <el-card shadow="never"><b>{{ stats?.total ?? 0 }}</b><small>图片总数</small></el-card>
            <el-card shadow="never"><b>{{ stats?.directoryCount ?? 0 }}</b><small>目录数</small></el-card>
            <el-card shadow="never" :class="{ warn: (stats?.nonHttps ?? 0) > 0 }">
              <b>{{ stats?.nonHttps ?? 0 }}</b><small>非 HTTPS 地址（HTTPS 页面会被浏览器拦截）</small>
            </el-card>
          </div>
          <div class="stat-tables">
            <div v-for="group in statGroups" :key="group.title" class="stat-table">
              <h3>{{ group.title }}</h3>
              <el-table :data="group.rows" size="small" border>
                <el-table-column prop="name" label="分类" />
                <el-table-column prop="count" label="数量" width="90" />
              </el-table>
            </div>
          </div>
        </template>

        <template v-else-if="mode === 'folder'">
          <el-table :data="folders" size="small" border>
            <el-table-column prop="directory" label="目录" min-width="220" />
            <el-table-column prop="count" label="图片数" width="90" />
            <el-table-column label="来源" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="(count, name) in row.sources" :key="name" class="tag">{{ name }} ×{{ count }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-else-if="mode === 'grid'">
          <div class="grid">
            <div v-for="asset in assets" :key="asset.source + asset.url" class="grid-item">
              <el-image :src="displayUrl(asset.url)" fit="cover" :preview-src-list="gridUrls" :initial-index="assets.indexOf(asset)" lazy>
                <template #error><div class="broken">无法加载</div></template>
              </el-image>
              <el-tag size="small">{{ asset.sourceLabel }}</el-tag>
              <span class="path" :title="asset.url">{{ asset.fileName }}</span>
              <el-button link type="primary" @click="copy(asset.url)">复制地址</el-button>
            </div>
          </div>
        </template>

        <template v-else>
          <el-table :data="assets" size="small" border>
            <el-table-column label="预览" width="90">
              <template #default="{ row }">
                <el-image :src="displayUrl(row.url)" fit="cover" class="thumb" :preview-src-list="gridUrls" :initial-index="assets.indexOf(row)" lazy />
              </template>
            </el-table-column>
            <el-table-column prop="sourceLabel" label="来源" width="110" />
            <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="directory" label="目录" min-width="180" show-overflow-tooltip />
            <el-table-column prop="extension" label="格式" width="90" />
            <el-table-column label="协议" width="100">
              <template #default="{ row }">
                <el-tag :type="row.scheme === 'https' ? 'success' : row.scheme === 'http' ? 'danger' : 'info'" size="small">{{ row.scheme }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button link type="primary" @click="copy(row.url)">复制</el-button>
                <a :href="displayUrl(row.url)" target="_blank" rel="noreferrer">打开</a>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <el-pagination v-if="mode === 'list' || mode === 'grid'" background layout="total, prev, pager, next"
          :total="total" :current-page="page" :page-size="size"
          @current-change="value => { page = value; load() }" />
      </div>
    </el-card>
  </main>
</template>

<style scoped>
.images { padding: 24px; }
h2 { color: #285a62; margin: 0; }
h3 { color: #285a62; font-size: 14px; margin: 0 0 8px; }
.header { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.hint { color: #698789; font-size: 12px; }
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.search { max-width: 280px; }
.pane { display: flex; flex-direction: column; gap: 12px; }
.stat-cards { display: flex; gap: 12px; }
.stat-cards :deep(.el-card__body) { display: flex; flex-direction: column; gap: 4px; min-width: 200px; }
.stat-cards b { font-size: 22px; color: #285a62; }
.stat-cards small { color: #698789; }
.stat-cards .warn b { color: #c45656; }
.stat-tables { display: flex; gap: 16px; flex-wrap: wrap; }
.stat-table { flex: 1 1 240px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 12px; }
.grid-item { display: flex; flex-direction: column; gap: 6px; border: 1px solid #ebeef5; border-radius: 6px; padding: 8px; }
.grid-item .el-image { width: 100%; height: 120px; background: #f5f7fa; }
.path { font-size: 12px; color: #698789; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.thumb { width: 60px; height: 60px; background: #f5f7fa; }
.broken { display: flex; align-items: center; justify-content: center; height: 100%; font-size: 12px; color: #a8abb2; }
.tag { margin-right: 4px; }
</style>
