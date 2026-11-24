<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const router = useRouter()
const groups = [
  { title: '顶部与公告', fields: [
    ['brand', '店铺名称', '智游美'], ['searchHint', '搜索框提示', '⌕ 搜索商品名称'],
    ['cartEntry', '顶部购物车入口', '购物车 →'], ['announcementLabel', '公告标签', '公告'],
    ['announcementMore', '公告查看提示', '查看 ›'],
  ] },
  { title: '首页快捷入口', fields: [
    ['quickCategoryTitle', '商品分类标题', '商品分类'], ['quickCategorySub', '商品分类说明', '按品类找好物 →'],
    ['quickCartTitle', '购物车标题', '购物车'], ['quickCartSub', '购物车说明', '查看已选商品 →'],
  ] },
  { title: '自选区', fields: [
    ['pickHeading', '商品区标题', '自选区旅游活动'], ['pickHeadingSub', '商品区说明', '精选好物'],
    ['pickItemSub', '活动商品说明', '查看活动规则与旅游正品'], ['pickBadge', '商品标记', '自选区活动'],
  ] },
  { title: '复购区', fields: [
    ['rebuyHeading', '商品区标题', '复购区推荐商品'], ['rebuyHeadingSub', '商品区说明', '精选好物'],
    ['rebuyBadge', '商品标记', '复购区'],
  ] },
  { title: '消费区', fields: [
    ['consumeHeading', '商品区标题', '消费区推荐商品'], ['consumeHeadingSub', '商品区说明', '智豆优先 · 挑选好物'],
    ['consumeBadge', '商品标记', '消费区'],
  ] },
  { title: '通用按钮', fields: [
    ['more', '查看更多', '更多 →'], ['addCart', '加入购物车', '加入购物车'],
  ] },
] as const
const defaults: Record<string, string> = Object.fromEntries(groups.flatMap(group => group.fields.map(([key, , value]) => [key, value])))
const copy = reactive<Record<string, string>>({ ...defaults })
const saved = ref<Record<string, string>>({ ...defaults })
const existingExtras = ref<Record<string, string>>({})
const busy = ref(false)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await http.get<Record<string, string>>('/p0/column-images/mini-home-copy')
    existingExtras.value = Object.fromEntries(Object.entries(result.data || {}).filter(([key]) => !(key in defaults)))
    const next = { ...defaults, ...result.data }
    Object.assign(copy, next)
    saved.value = next
  } catch { ElMessage.error('读取首页文字失败，请稍后重试') }
  finally { loading.value = false }
}
async function publish() {
  const next: Record<string, string> = { ...existingExtras.value }
  for (const key of Object.keys(defaults)) {
    const value = copy[key]?.trim() || ''
    if (!value || [...value].length > 80) {
      ElMessage.warning('每项文字须填写 1 至 80 字')
      return
    }
    if (value !== defaults[key]) next[key] = value
  }
  busy.value = true
  try {
    await http.put('/p0/column-images/mini-home-copy', next)
    Object.assign(copy, next)
    saved.value = { ...defaults, ...next }
    ElMessage.success('已发布，重新进入小程序首页即可看到新文字')
  } catch { ElMessage.error('发布失败，原有设置未变更') }
  finally { busy.value = false }
}
function restore() { Object.assign(copy, saved.value) }
onMounted(load)
</script>

<template>
  <div class="mini-home-settings">
    <div class="heading"><div><h2>小程序首页文字</h2><p>修改首页文字后点击“发布”。图片、公告与商品仍由各自的后台入口管理。</p></div>
      <div class="actions"><el-button @click="load" :loading="loading">刷新</el-button><el-button @click="restore">撤销未发布修改</el-button><el-button type="primary" :loading="busy" @click="publish">发布文字</el-button></div></div>
    <div class="shortcuts">
      <el-button @click="router.push({ name: 'miniappImages' })">栏目图片与叠加文字</el-button>
      <el-button @click="router.push({ name: 'miniappSections' })">板块显示与顺序</el-button>
      <el-button @click="router.push({ name: 'miniappLinks' })">首页点击跳转</el-button>
      <el-button @click="router.push({ name: 'memberMgrNotices' })">公告管理</el-button>
      <el-button @click="router.push({ name: 'pickProduct' })">自选区商品</el-button>
      <el-button @click="router.push({ name: 'rebuyProduct' })">复购区商品</el-button>
      <el-button @click="router.push({ name: 'consumeProduct' })">消费区商品</el-button>
      <el-button @click="router.push({ name: 'serviceAdvertise' })">轮播广告与链接</el-button>
    </div>
    <div class="groups"><section v-for="group in groups" :key="group.title"><h3>{{ group.title }}</h3>
      <label v-for="[key, label] in group.fields" :key="key"><span>{{ label }}</span><el-input v-model="copy[key]" maxlength="80" show-word-limit /></label>
    </section></div>
  </div>
</template>

<style scoped>
.mini-home-settings{padding:24px;color:#243e39}.heading{display:flex;align-items:start;justify-content:space-between;gap:20px;flex-wrap:wrap}.heading h2{margin:0 0 8px}.heading p{margin:0;color:#667085}.actions,.shortcuts{display:flex;gap:8px;flex-wrap:wrap}.shortcuts{margin:22px 0}.shortcuts .el-button,.actions .el-button{margin-left:0}.groups{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;max-width:1300px}section{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:20px}section h3{margin:0 0 18px}label{display:grid;grid-template-columns:140px 1fr;align-items:center;gap:12px;margin:12px 0}label span{font-size:14px}@media(max-width:850px){.groups{grid-template-columns:1fr}label{grid-template-columns:1fr;gap:6px}}
</style>
