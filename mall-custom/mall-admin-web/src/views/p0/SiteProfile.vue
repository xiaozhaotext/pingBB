<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'
const loading=ref(false),error=ref(''),confirmed=ref(false)
const company=ref({revision:0,companyName:'',address:'',telephone:'',mobile:'',email:'',qq:'',wechatQrUrl:''})
const privacy=ref({revision:0,title:'',content:''})
const savedDraft=ref(''),published=ref('尚未发布')
const privacyDirty=computed(()=>JSON.stringify(privacy.value)!==savedDraft.value)
async function load() {
  loading.value=true;error.value='';confirmed.value=false
  try {
    const {data:r}=await getP0<Record<string,unknown>>('site-profile')
    company.value={revision:Number(r.company_revision),companyName:String(r.company_name),address:String(r.address),telephone:String(r.telephone),mobile:String(r.mobile),email:String(r.email),qq:String(r.qq),wechatQrUrl:String(r.wechat_qr_url)}
    privacy.value={revision:Number(r.privacy_revision),title:String(r.privacy_title),content:String(r.privacy_content)}
    savedDraft.value=JSON.stringify(privacy.value)
    published.value=r.published_revision==null?'尚未发布':`已发布版本 ${r.published_revision} · ${r.published_at}`
  } catch {error.value='加载失败，请检查权限或连接后重试'}
  finally {loading.value=false}
}
async function save(path:string,body:Record<string,unknown>) {
  loading.value=true
  try {await postP0(path,body);ElMessage.success('已保存并记录审计');await load()}
  finally {loading.value=false}
}
onMounted(load)
</script>
<template>
  <main class="site-profile" v-loading="loading">
    <header><div><h1>公司信息与隐私政策</h1><p>资料来自数据库 · 草稿与公开内容独立保存</p></div><el-button @click="load">重新加载</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tabs>
      <el-tab-pane label="公司信息">
        <el-card shadow="never"><el-form label-position="top">
          <el-form-item label="公司名称"><el-input v-model="company.companyName" maxlength="128" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="company.address" maxlength="500" /></el-form-item>
          <div class="pair"><el-form-item label="电话"><el-input v-model="company.telephone" maxlength="64" /></el-form-item><el-form-item label="手机"><el-input v-model="company.mobile" maxlength="32" /></el-form-item></div>
          <div class="pair"><el-form-item label="邮箱"><el-input v-model="company.email" maxlength="128" /></el-form-item><el-form-item label="QQ"><el-input v-model="company.qq" maxlength="32" /></el-form-item></div>
          <el-form-item label="微信二维码图片地址（HTTPS）"><el-input v-model="company.wechatQrUrl" maxlength="1000" placeholder="填写已上传图片的HTTPS地址" /></el-form-item>
          <el-button type="primary" @click="save('site-profile/company',company)">保存公司信息</el-button>
        </el-form></el-card>
      </el-tab-pane>
      <el-tab-pane label="隐私政策">
        <el-alert :title="published" type="info" :closable="false" class="status" />
        <el-card shadow="never"><el-form label-position="top">
          <el-form-item label="政策标题"><el-input v-model="privacy.title" maxlength="128" /></el-form-item>
          <el-form-item label="正文（纯文本，不执行HTML）"><el-input v-model="privacy.content" type="textarea" :rows="16" maxlength="50000" show-word-limit /></el-form-item>
          <el-button @click="save('site-profile/privacy/draft',privacy)">保存草稿</el-button>
          <p class="tip">保存草稿不会修改已发布政策。发布前请保存当前编辑内容，再确认已审核。</p>
          <el-checkbox v-model="confirmed">我确认已保存当前内容，且政策已审核</el-checkbox>
          <el-button type="primary" :disabled="!confirmed || privacyDirty" @click="save('site-profile/privacy/publish',{revision:privacy.revision,confirmed})">发布已保存版本</el-button>
        </el-form></el-card>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>
<style scoped>
.site-profile{padding:28px;background:#f2f7f7;min-height:100vh}header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}h1{color:#285a62;margin:0 0 8px}p{color:#698789}.pair{display:grid;grid-template-columns:1fr 1fr;gap:20px}.tip{font-size:13px}.status{margin-bottom:16px}@media(max-width:600px){.pair{grid-template-columns:1fr}}
</style>
