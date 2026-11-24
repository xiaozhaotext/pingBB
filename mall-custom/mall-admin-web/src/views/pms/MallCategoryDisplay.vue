<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'
type Row={id:number;parentId:number;name:string;sourceName:string;icon?:string;sort:number;visible:boolean;sourceVisible:boolean;zones:string[]}
type Snapshot={revision:number;categories:Row[]}
const rows=ref<Row[]>([]),revision=ref(0),loading=ref(false),saving=ref(false),keyword=ref(''),zone=ref('')
const editing=ref(false),draft=ref<Row>()
const zoneName=(value:string)=>value==='CONSUME'?'消费区':'复购区'
const filtered=computed(()=>rows.value.filter(row=>(!zone.value||row.zones.includes(zone.value))&&(!keyword.value.trim()||(row.name+' '+row.sourceName).includes(keyword.value.trim()))))
const parentName=(id:number)=>id===0?'顶级分类':rows.value.find(row=>row.id===id)?.name||'顶级分类'
const parentOptions=computed(()=>rows.value.filter(row=>{
  const seen=new Set<number>();let cursor:Row|undefined=row
  while(cursor){if(cursor.id===draft.value?.id||seen.has(cursor.id))return false;seen.add(cursor.id);cursor=rows.value.find(item=>item.id===cursor?.parentId)}
  return true
}))
function accept(data:Snapshot){rows.value=data.categories;revision.value=data.revision}
async function load(){loading.value=true;try{accept((await getP0<Snapshot>('mall-category-display')).data)}finally{loading.value=false}}
function edit(row:Row){draft.value={...row};editing.value=true}
async function save(){
  if(!draft.value || saving.value)return
  if(!draft.value.name.trim())return ElMessage.warning('请填写展示名称')
  saving.value=true
  try{accept((await postP0<Snapshot>('mall-category-display',{revision:revision.value,id:draft.value.id,name:draft.value.name.trim(),parentId:draft.value.parentId,sort:draft.value.sort,visible:draft.value.visible})).data);editing.value=false;ElMessage.success('商城分类筛选已更新，原专区分类不变')}
  finally{saving.value=false}
}
async function reset(row:Row){
  await ElMessageBox.confirm('恢复该分类在商城筛选中的原始名称、上级、排序和显示设置？原专区不受影响。','恢复原设置',{type:'warning'})
  saving.value=true
  try{accept((await postP0<Snapshot>('mall-category-display',{revision:revision.value,id:row.id,reset:true})).data);ElMessage.success('已恢复原设置')}
  finally{saving.value=false}
}
onMounted(load)
</script>
<template>
  <main class="catalog-page">
    <h1>商城分类筛选管理</h1>
    <el-alert title="自动汇总消费区、复购区的分类。本页只调整商城“商品分类”和全站搜索中的分类展示，不改变原专区分类、商品归属和专区筛选。新增分类自动同步。" type="info" :closable="false" />
    <div class="filters">
      <el-input v-model="keyword" placeholder="搜索展示名称或原分类名称" clearable style="width:280px" />
      <el-select v-model="zone" placeholder="全部来源" clearable style="width:160px"><el-option label="消费区" value="CONSUME"/><el-option label="复购区" value="REBUY"/></el-select>
      <el-button :loading="loading" :disabled="saving" @click="load">刷新同步</el-button>
      <span>共 {{ filtered.length }} 个分类</span>
    </div>
    <el-table v-loading="loading" :data="filtered" row-key="id" empty-text="暂无消费区、复购区分类">
      <el-table-column prop="id" label="分类ID" width="90" />
      <el-table-column prop="name" label="商城展示名称" min-width="150" />
      <el-table-column prop="sourceName" label="原分类名称" min-width="150" />
      <el-table-column label="来源专区" width="140"><template #default="{row}">{{ row.zones.map(zoneName).join('、') }}</template></el-table-column>
      <el-table-column label="商城上级分类" min-width="140"><template #default="{row}">{{ parentName(row.parentId) }}</template></el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="显示设置" min-width="180"><template #default="{row}">{{ row.visible ? '显示' : '隐藏' }}<span v-if="!row.sourceVisible" class="muted">（原专区已隐藏）</span></template></el-table-column>
      <el-table-column label="操作" width="210"><template #default="{row}"><el-button link type="primary" :disabled="saving" @click="edit(row)">编辑展示关系</el-button><el-button link :disabled="saving" @click="reset(row)">恢复原设置</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="editing" title="编辑商城分类筛选" width="500px" :close-on-click-modal="!saving" :show-close="!saving">
      <el-form v-if="draft" label-width="110px">
        <el-form-item label="原分类">{{ draft.sourceName }} · {{ draft.zones.map(zoneName).join('、') }}</el-form-item>
        <el-form-item label="展示名称"><el-input v-model="draft.name" maxlength="80" /></el-form-item>
        <el-form-item label="上级分类"><el-select v-model="draft.parentId" style="width:100%"><el-option label="顶级分类" :value="0"/><el-option v-for="row in parentOptions" :key="row.id" :label="row.name+'（ID '+row.id+'）'" :value="row.id"/></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="draft.sort" :min="-1000000" :max="1000000" :precision="0" /></el-form-item>
        <el-form-item label="商城显示"><el-switch v-model="draft.visible" /></el-form-item>
        <p class="muted">隐藏上级分类也会隐藏其下级。原专区已隐藏的分类需先在原专区开启显示。</p>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="editing=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存展示设置</el-button></template>
    </el-dialog>
  </main>
</template>
<style scoped>
.catalog-page{padding:24px}.catalog-page h1{font-size:22px;color:#285a62;margin:0 0 20px}.filters{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin:20px 0}.filters span,.muted{font-size:12px;color:#87948f}
</style>
