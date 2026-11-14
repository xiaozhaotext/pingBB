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
const tourismLabel = ref('旅游正品')
const labelSaving = ref(false)
async function loadTourismLabel() {
  try {
    const response = await fetch(`/static/festive-home/tourism-label.json?t=${Date.now()}`, { cache:'no-store' })
    if (response.ok) tourismLabel.value = (await response.json()).label || '旅游正品'
  } catch { /* No saved label yet. */ }
}
async function saveTourismLabel() {
  const label = tourismLabel.value.trim()
  if (!label || [...label].length > 12) { ElMessage.warning('请输入1至12个字'); return }
  labelSaving.value = true
  try {
    await http.put('/p0/column-images/tourism-label', { label })
    tourismLabel.value = label
    ElMessage.success('旅游活动商品名称已更新')
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || error?.message || '保存失败') }
  finally { labelSaving.value = false }
}
const busy = ref('')
type Caption = { text: string; visible: boolean; bold: boolean; color: string; size: number; x: number; y: number; font?: string; spacing?: number; effect?: string }
const fonts: Record<string,string> = { song:'"Songti SC","Noto Serif SC",serif', hei:'"PingFang SC","Microsoft YaHei",sans-serif', kai:'"STKaiti","KaiTi",serif', classic:'Georgia,"Songti SC",serif' }
const effects: Record<string,string> = { shadow:'0 2px 8px #172d3b99', glow:'0 0 8px #fff, 0 2px 14px #fff9', outline:'1px 1px 0 #24372f,-1px -1px 0 #24372f,1px -1px 0 #24372f,-1px 1px 0 #24372f', none:'none' }
const presets = [
  { name:'山水宋韵', font:'song', color:'#fff7e5', spacing:3, effect:'shadow', bold:false },
  { name:'现代简约', font:'hei', color:'#ffffff', spacing:1, effect:'shadow', bold:true },
  { name:'雅致金字', font:'classic', color:'#f6d78b', spacing:3, effect:'glow', bold:true },
  { name:'书法意境', font:'kai', color:'#fff9ed', spacing:4, effect:'outline', bold:false },
]
function usePreset(item: Caption, preset: typeof presets[number]) { Object.assign(item, { font:preset.font, color:preset.color, spacing:preset.spacing, effect:preset.effect, bold:preset.bold }) }
const defaults: Record<string,string[]> = { hero:['山水有约，好物相伴','品质好物 · 悦享生活'], pick:['精选品牌 · 品质之选','自选区','自选好物，发现心仪之选'], rebuy:['复购区','品质好物 · 再次心选'], consume:['消费区','商品积分购买 · 兑换好物'] }
const editing = ref('')
const captions = ref<Caption[]>([])
const saving = ref(false)
async function editCaptions(key: string) {
  editing.value = key
  try {
    const response = await fetch(`/static/festive-home/${key}-captions.json?t=${Date.now()}`, { cache:'no-store' })
    if (!response.ok && response.status !== 404) throw new Error('读取文字设置失败')
    captions.value = response.ok ? await response.json() : (defaults[key] || []).map((text, i) => ({ text, visible:true, bold:false, color:'#fff7e5', size:i === (key === 'pick' ? 1 : 0) ? 26 : 13, x:5, y:25+i*22, font:'song', spacing:2, effect:'shadow' }))
  } catch (error: any) { ElMessage.error(error.message); editing.value = '' }
}
async function saveCaptions() {
  saving.value = true
  try {
    await http.put(`/p0/column-images/captions/${editing.value}`, captions.value)
    ElMessage.success('叠加文字已保存')
    editing.value = ''
  } catch (error: any) { ElMessage.error(error?.message || '保存失败') }
  finally { saving.value = false }
}
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
onMounted(() => { void refresh(); void loadTourismLabel() })
</script>

<template>
  <div class="column-images"><h2>栏目图片</h2><p>上传后替换正式站对应的宣传图，页面会按图片原始比例完整展示。支持常见图片格式，系统会转换为 JPG；不超过 100MB、3000 万像素。</p>
    <section class="tourism-label"><h3>旅游活动商品名称</h3><p>控制商品详情页“选择旅游正品”和四个选项中的名称。</p><div class="label-editor"><el-input v-model="tourismLabel" maxlength="12" show-word-limit /><el-button type="primary" :loading="labelSaving" @click="saveTourismLabel">保存名称</el-button></div></section>
    <div class="grid"><section v-for="slot in slots" :key="slot.key"><h3>{{ slot.label }}</h3><p>{{ slot.hint }}</p><img :src="imageUrl(slot.key)" :alt="slot.label" /><label class="upload">{{ busy === slot.key ? '上传中…' : '上传并替换' }}<input type="file" accept="image/*" :disabled="!!busy" @change="choose(slot.key, $event)" /></label><el-button v-if="defaults[slot.key]" class="caption-button" @click="editCaptions(slot.key)">编辑叠加文字</el-button></section></div>
    <el-dialog :model-value="!!editing" title="编辑叠加文字" width="min(680px, 94vw)" @update:model-value="value => { if (!value) editing = '' }">
      <p>图片自带文字时，可以关闭对应的叠加文字。位置按图片宽高百分比计算。</p>
      <div class="caption-preview" :style="{ backgroundImage: `url(${imageUrl(editing)})` }"><span v-for="(item, i) in captions" :key="i" :style="{ left:item.x+'%', top:item.y+'%', color:item.color, fontSize:item.size+'px', fontFamily:fonts[item.font || 'song'], letterSpacing:(item.spacing ?? 0)+'px', textShadow:effects[item.effect || 'shadow'], fontWeight:item.bold?'700':'400', display:item.visible?'block':'none' }">{{ item.text }}</span></div>
      <fieldset v-for="(item, i) in captions" :key="i"><legend>文字 {{ i+1 }}</legend><el-input v-model="item.text" maxlength="100" /><div class="preset-list"><el-button v-for="preset in presets" :key="preset.name" size="small" @click="usePreset(item,preset)">{{ preset.name }}</el-button></div><label><el-checkbox v-model="item.visible">显示</el-checkbox></label><label><el-checkbox v-model="item.bold">加粗</el-checkbox></label><label>字体 <el-select v-model="item.font" placeholder="宋体" style="width:130px"><el-option label="宋韵" value="song"/><el-option label="现代黑体" value="hei"/><el-option label="楷体" value="kai"/><el-option label="经典衬线" value="classic"/></el-select></label><label>效果 <el-select v-model="item.effect" placeholder="投影" style="width:110px"><el-option label="柔和投影" value="shadow"/><el-option label="浅色光晕" value="glow"/><el-option label="细描边" value="outline"/><el-option label="无" value="none"/></el-select></label><label>颜色 <input v-model="item.color" type="color" /></label><label>字号 <el-input-number v-model="item.size" :min="10" :max="72" /></label><label>字距 <el-input-number v-model="item.spacing" :min="0" :max="12" /></label><label>左侧 % <el-input-number v-model="item.x" :min="0" :max="90" /></label><label>顶部 % <el-input-number v-model="item.y" :min="0" :max="90" /></label></fieldset>
      <template #footer><el-button @click="editing = ''">取消</el-button><el-button type="primary" :loading="saving" @click="saveCaptions">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.column-images{padding:24px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px;max-width:1100px}section{padding:20px;border:1px solid #e5e7eb;border-radius:12px;background:white;min-width:0}section p{color:#667085}img{display:block;width:100%;height:auto;max-height:420px;object-fit:contain;border-radius:8px}.upload{display:inline-block;margin-top:14px;padding:9px 18px;background:#a87b35;color:white;border-radius:6px;cursor:pointer}.upload input{display:none}@media(max-width:700px){.grid{grid-template-columns:1fr}}
.caption-button{margin-left:12px}.caption-preview{height:190px;background-position:center;background-size:cover;position:relative;margin:14px 0;overflow:hidden}.caption-preview span{position:absolute;max-width:90%;line-height:1.2}fieldset{border:1px solid #ddd;margin:12px 0;padding:12px}fieldset label{display:inline-flex;gap:5px;align-items:center;margin:8px}
.preset-list{display:flex;flex-wrap:wrap;gap:6px;margin:10px 0}.preset-list .el-button{margin-left:0}
.tourism-label{max-width:650px;margin:16px 0 20px}.label-editor{display:flex;gap:12px}.label-editor .el-input{max-width:300px}
</style>
