<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import { getP0 } from '@/apis/p0'

const slots = [
  { key: 'hero', label: '首页主宣传图', hint: '按上传图片的原始比例展示' },
  { key: 'pick', label: '自选区图片', hint: '首页入口与自选区顶部共用' },
  { key: 'rebuy', label: '复购区图片', hint: '首页入口与复购区顶部共用' },
  { key: 'consume', label: '消费区图片', hint: '首页入口与消费区顶部共用' },
  { key: 'gift1', label: '旅游正品1介绍图', hint: '自选区旅游活动详情页展示' },
  { key: 'gift2', label: '旅游正品2介绍图', hint: '自选区旅游活动详情页展示' },
  { key: 'gift3', label: '旅游正品3介绍图', hint: '自选区旅游活动详情页展示' },
  { key: 'gift4', label: '旅游正品4介绍图', hint: '自选区旅游活动详情页展示' },
]
const urls = ref<Record<string, string>>({})
const busy = ref('')
const refresh = async () => { urls.value = (await getP0<Record<string,string>>('column-images')).data || {} }
const imageUrl = (key: string) => `${urls.value[key] || ''}?v=${Date.now()}`
async function asJpeg(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  try {
    if (bitmap.width < 1 || bitmap.height < 1 || bitmap.width * bitmap.height > 30_000_000)
      throw new Error('图片尺寸无效，不得超过3000万像素')
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('浏览器无法处理这张图片')
    context.fillStyle = '#fff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(bitmap, 0, 0)
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片转换失败')), 'image/jpeg', 0.92))
  } finally { bitmap.close() }
}
async function choose(key: string, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 100 * 1024 * 1024) { ElMessage.warning('请选择不超过100MB的图片'); return }
  try { await ElMessageBox.confirm(`确认替换${slots.find(s => s.key === key)?.label}？更新后前台立即显示新图片。`, '确认替换', { type: 'warning' }) }
  catch { return }
  busy.value = key
  try {
    const jpeg = await asJpeg(file)
    const form = new FormData(); form.append('file', jpeg, `${key}.jpg`)
    await http.post(`/p0/column-images/${key}`, form, { timeout: 300000 })
    ElMessage.success('图片已更新')
    await refresh()
    urls.value = { ...urls.value }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '图片处理失败，请换一张图片重试')
  }
  finally { busy.value = '' }
}
onMounted(refresh)
</script>

<template>
  <div class="column-images"><h2>栏目图片</h2><p>上传后替换正式站对应的宣传图，页面会按图片原始比例完整展示。支持常见图片格式，系统会转换为 JPG；不超过 100MB、3000 万像素。</p>
    <div class="grid"><section v-for="slot in slots" :key="slot.key"><h3>{{ slot.label }}</h3><p>{{ slot.hint }}</p><img :src="imageUrl(slot.key)" :alt="slot.label" /><label class="upload">{{ busy === slot.key ? '上传中…' : '上传并替换' }}<input type="file" accept="image/*" :disabled="!!busy" @change="choose(slot.key, $event)" /></label></section></div>
  </div>
</template>

<style scoped>
.column-images{padding:24px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px;max-width:1100px}section{padding:20px;border:1px solid #e5e7eb;border-radius:12px;background:white;min-width:0}section p{color:#667085}img{display:block;width:100%;height:auto;max-height:420px;object-fit:contain;border-radius:8px}.upload{display:inline-block;margin-top:14px;padding:9px 18px;background:#a87b35;color:white;border-radius:6px;cursor:pointer}.upload input{display:none}@media(max-width:700px){.grid{grid-template-columns:1fr}}
</style>
