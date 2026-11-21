<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

type Layout = {
  showHero: boolean; showAnnouncement: boolean; showQuickLinks: boolean
  showPick: boolean; showRebuy: boolean; showConsume: boolean
  zoneOrder: string[]
}
const defaults: Layout = { showHero:true, showAnnouncement:true, showQuickLinks:true,
  showPick:true, showRebuy:true, showConsume:true, zoneOrder:['pick','rebuy','consume'] }
const layout = reactive<Layout>({ ...defaults, zoneOrder:[...defaults.zoneOrder] })
const labels: Record<string,string> = { pick:'自选区', rebuy:'复购区', consume:'消费区' }
const saved = ref<Layout>({ ...defaults, zoneOrder:[...defaults.zoneOrder] })
const busy = ref(false)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const response = await http.get<Partial<Layout>>('/p0/column-images/mini-home-layout')
    const data = response.data || {}
    const order = Array.isArray(data.zoneOrder) && data.zoneOrder.length === 3 &&
      new Set(data.zoneOrder).size === 3 && data.zoneOrder.every(key => key in labels)
      ? data.zoneOrder : defaults.zoneOrder
    Object.assign(layout, defaults, data, { zoneOrder:[...order] })
    saved.value = { ...layout, zoneOrder:[...layout.zoneOrder] }
  } catch { ElMessage.error('读取板块设置失败') }
  finally { loading.value = false }
}
function move(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= layout.zoneOrder.length) return
  const next = [...layout.zoneOrder]
  ;[next[index], next[target]] = [next[target]!, next[index]!]
  layout.zoneOrder = next
}
async function publish() {
  busy.value = true
  try {
    await http.put('/p0/column-images/mini-home-layout', { ...layout, zoneOrder:[...layout.zoneOrder] })
    saved.value = { ...layout, zoneOrder:[...layout.zoneOrder] }
    ElMessage.success('板块设置已发布，重新进入小程序首页即可看到')
  } catch { ElMessage.error('发布失败，原有设置未变更') }
  finally { busy.value = false }
}
function restore() { Object.assign(layout, saved.value, { zoneOrder:[...saved.value.zoneOrder] }) }
onMounted(load)
</script>

<template>
  <div class="sections-page"><h2>小程序首页板块</h2><p>关闭后仅隐藏首页展示，商品、订单和专区页面仍保留。下方顺序只调整三张专区入口图片。</p>
    <div class="card"><h3>首页内容</h3>
      <label><span>主宣传图</span><el-switch v-model="layout.showHero" /></label>
      <label><span>公告</span><el-switch v-model="layout.showAnnouncement" /></label>
      <label><span>商品分类与购物车快捷入口</span><el-switch v-model="layout.showQuickLinks" /></label>
      <label><span>自选区入口及商品</span><el-switch v-model="layout.showPick" /></label>
      <label><span>复购区入口及商品</span><el-switch v-model="layout.showRebuy" /></label>
      <label><span>消费区入口及商品</span><el-switch v-model="layout.showConsume" /></label>
    </div>
    <div class="card"><h3>专区入口图片顺序</h3><div v-for="(key,index) in layout.zoneOrder" :key="key" class="zone-row">
      <span>{{ index + 1 }}. {{ labels[key] }}</span><div><el-button size="small" :disabled="index === 0" @click="move(index,-1)">上移</el-button><el-button size="small" :disabled="index === 2" @click="move(index,1)">下移</el-button></div>
    </div></div>
    <div class="actions"><el-button :loading="loading" @click="load">刷新</el-button><el-button @click="restore">撤销未发布修改</el-button><el-button type="primary" :loading="busy" @click="publish">发布设置</el-button></div>
  </div>
</template>

<style scoped>
.sections-page{padding:24px;max-width:900px;color:#243e39}.sections-page p{color:#667085}.card{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:20px;margin:16px 0}.card h3{margin:0 0 16px}.card label,.zone-row{display:flex;justify-content:space-between;align-items:center;gap:16px;min-height:48px;border-top:1px solid #f0f1f1}.zone-row .el-button{margin-left:8px}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:22px}.actions .el-button{margin-left:0}
</style>
