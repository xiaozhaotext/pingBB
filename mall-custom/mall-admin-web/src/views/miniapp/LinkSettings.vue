<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/http'

const choices = [
  { label:'首页', value:'/pages/index/index' }, { label:'商品分类', value:'/pages/category/category' },
  { label:'购物车', value:'/pages/cart/cart' }, { label:'自选区', value:'/pages/zone/pick' },
  { label:'复购区', value:'/pages/zone/rebuy' }, { label:'消费区', value:'/pages/zone/consume' },
  { label:'公告列表', value:'/pages/notice/notice' },
]
const fields = [
  { key:'brand', label:'店铺名称' }, { key:'topCart', label:'顶部购物车' },
  { key:'quickCategory', label:'商品分类快捷入口' }, { key:'quickCart', label:'购物车快捷入口' },
  { key:'pick', label:'自选区图片与“更多”' }, { key:'rebuy', label:'复购区图片与“更多”' },
  { key:'consume', label:'消费区图片与“更多”' },
]
const defaults: Record<string,string> = {
  brand:'/pages/index/index', topCart:'/pages/cart/cart', quickCategory:'/pages/category/category',
  quickCart:'/pages/cart/cart', pick:'/pages/zone/pick', rebuy:'/pages/zone/rebuy', consume:'/pages/zone/consume',
}
const links = reactive<Record<string,string>>({ ...defaults })
const saved = ref<Record<string,string>>({ ...defaults })
const busy = ref(false)
async function load() {
  try {
    const response = await http.get<Record<string,string>>('/p0/column-images/mini-home-links')
    const next = { ...defaults, ...response.data }
    Object.assign(links, next)
    saved.value = next
  } catch { ElMessage.error('读取跳转设置失败') }
}
async function publish() {
  busy.value = true
  try {
    await http.put('/p0/column-images/mini-home-links', { ...links })
    saved.value = { ...links }
    ElMessage.success('首页跳转已发布')
  } catch { ElMessage.error('发布失败，原有设置未变更') }
  finally { busy.value = false }
}
function restore() { Object.assign(links, saved.value) }
onMounted(load)
</script>

<template>
  <div class="link-settings"><h2>小程序首页跳转</h2><p>为现有入口选择目标页面。商品卡片仍进入对应商品详情，支付和订单跳转保持原样。</p>
    <section><label v-for="field in fields" :key="field.key"><span>{{ field.label }}</span><el-select v-model="links[field.key]" style="width:min(100%,320px)"><el-option v-for="choice in choices" :key="choice.value" :label="choice.label" :value="choice.value" /></el-select></label></section>
    <div class="actions"><el-button @click="load">刷新</el-button><el-button @click="restore">撤销未发布修改</el-button><el-button type="primary" :loading="busy" @click="publish">发布跳转</el-button></div>
  </div>
</template>

<style scoped>
.link-settings{padding:24px;max-width:850px;color:#243e39}.link-settings p{color:#667085}section{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:20px}label{display:grid;grid-template-columns:180px 1fr;align-items:center;gap:12px;min-height:54px;border-bottom:1px solid #f1f2f2}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:20px}.actions .el-button{margin-left:0}@media(max-width:700px){label{grid-template-columns:1fr;gap:4px;padding:10px 0}}
</style>
