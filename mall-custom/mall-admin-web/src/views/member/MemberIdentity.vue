<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'

const identityLabels: Record<number, string> = { 0: '普通会员', 1: '合伙人', 2: '联创' }
const identityOptions = [
  { value: 0, label: '普通会员' },
  { value: 1, label: '合伙人' },
  { value: 2, label: '联创' },
]

const loading = ref(false)
const membershipV2 = ref(false)
const v2Labels: Record<string,string> = { VISITOR: '游客', MEMBER: '会员', PARTNER: '合伙人', COFOUNDER: '联创' }
const displayedLabels = computed(() => membershipV2.value ? ['游客', '会员', '合伙人', '联创'] : ['普通会员', '合伙人', '联创'])
const saving = ref(false)
const members = ref<P0Row[]>([])
const keyword = ref('')
const identityForm = ref({ memberId: null as number | null, identityType: 0, reason: '' })
const referrerForm = ref({ memberId: null as number | null, referrerId: null as number | null, reason: '' })

const filtered = computed(() => {
  const key = keyword.value.trim()
  if (!key) return members.value
  return members.value.filter(row => String(row.username || '').includes(key) || String(row.phone || '').includes(key) || String(row.id) === key)
})
const counts = computed(() => {
  const result: Record<number, number> = { 0: 0, 1: 0, 2: 0 }
  members.value.forEach(row => {
    const type = membershipV2.value ? ['VISITOR','MEMBER','PARTNER','COFOUNDER'].indexOf(String(row.membership_tier)) : Number(row.identity_type ?? 0)
    result[type] = (result[type] || 0) + 1
  })
  return result
})

async function load() {
  loading.value = true
  try {
    membershipV2.value = !!(await getP0<{ enabled: boolean }>('tri-pools')).data.enabled
    members.value = (await getP0<P0Row[]>('members')).data || []
  } catch {
    members.value = []
    ElMessage.error('会员列表加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)

async function submitIdentity() {
  if (!identityForm.value.memberId) return ElMessage.warning('请输入会员 ID')
  if (identityForm.value.reason.trim().length < 5) return ElMessage.warning('请填写至少 5 个字的变更原因')
  await ElMessageBox.confirm('确认手动设置该会员身份并写入审计日志？', '设置会员身份', { type: 'warning' })
  saving.value = true
  try {
    await postP0('members/' + identityForm.value.memberId + '/identity', {
      identityType: identityForm.value.identityType,
      reason: identityForm.value.reason,
    })
    ElMessage.success('身份已更新')
    identityForm.value.reason = ''
    await load()
  } catch {
    ElMessage.error('身份设置失败')
  } finally {
    saving.value = false
  }
}

async function submitReferrer() {
  if (!referrerForm.value.memberId || !referrerForm.value.referrerId) return ElMessage.warning('请输入会员 ID 与推荐人 ID')
  if (referrerForm.value.reason.trim().length < 5) return ElMessage.warning('请填写至少 5 个字的变更原因')
  await ElMessageBox.confirm('确认调整该会员的直属推荐人及所有下级的推荐路径？', '绑定推荐关系', { type: 'warning' })
  saving.value = true
  try {
    await postP0('members/' + referrerForm.value.memberId + '/referrer', {
      referrerId: referrerForm.value.referrerId,
      reason: referrerForm.value.reason,
    })
    ElMessage.success('推荐关系已更新')
    referrerForm.value.reason = ''
    await load()
  } catch {
    ElMessage.error('推荐关系设置失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <main class="member-identity" v-loading="loading">
    <header class="head">
      <div>
        <small>会员</small>
        <h1>会员身份</h1>
        <p>{{ membershipV2 ? '游客 / 会员 / 合伙人 / 联创，身份按身份牌支付和有效直邀会员数自动晋升。' : '普通会员 / 合伙人 / 联创 可手动设置，并支持绑定会员推荐关系；所有变更写入审计。' }}</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <section class="cards">
      <el-card v-for="(label,i) in displayedLabels" :key="label" shadow="never" class="metric"><span>{{ label }}（当前页）</span><strong>{{ counts[i] || 0 }}</strong></el-card>
    </section>
    <section class="forms">
      <el-card v-if="!membershipV2" shadow="never">
        <template #header><strong>手动设置会员身份</strong></template>
        <el-form label-position="top">
          <el-form-item label="会员 ID"><el-input-number v-model="identityForm.memberId" :min="1" :controls="false" /></el-form-item>
          <el-form-item label="目标身份">
            <el-select v-model="identityForm.identityType" style="width: 100%">
              <el-option v-for="item in identityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="变更原因（至少 5 个字）"><el-input v-model="identityForm.reason" type="textarea" :rows="2" maxlength="200" /></el-form-item>
          <el-button type="primary" :loading="saving" @click="submitIdentity">保存身份</el-button>
        </el-form>
      </el-card>
      <el-card shadow="never">
        <template #header><strong>绑定会员推荐关系</strong></template>
        <el-form label-position="top">
          <el-form-item label="会员 ID"><el-input-number v-model="referrerForm.memberId" :min="1" :controls="false" /></el-form-item>
          <el-form-item label="推荐人会员 ID"><el-input-number v-model="referrerForm.referrerId" :min="1" :controls="false" /></el-form-item>
          <el-form-item label="变更原因（至少 5 个字）"><el-input v-model="referrerForm.reason" type="textarea" :rows="2" maxlength="200" /></el-form-item>
          <el-button type="primary" :loading="saving" @click="submitReferrer">保存推荐关系</el-button>
        </el-form>
      </el-card>
    </section>
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-head"><strong>会员身份一览</strong><el-input v-model="keyword" placeholder="搜索账号 / 手机号 / ID" clearable style="max-width: 260px" /></div>
      </template>
      <el-table :data="filtered" stripe empty-text="暂无会员">
        <el-table-column prop="id" label="会员 ID" width="90" />
        <el-table-column prop="username" label="会员名称" min-width="140" />
        <el-table-column prop="phone" label="手机号码" min-width="140" />
        <el-table-column label="身份" width="110">
          <template #default="{ row }">{{ membershipV2 ? v2Labels[String(row.membership_tier)] : identityLabels[Number(row.identity_type ?? 0)] || '普通会员' }}</template>
        </el-table-column>
        <el-table-column prop="inviter_id" label="推荐人 ID" width="110" />
        <el-table-column prop="create_time" label="注册时间" min-width="170" />
      </el-table>
    </el-card>
  </main>
</template>

<style scoped>
.member-identity { padding: 24px; background: #f6f7f4; min-height: 100vh; }
.head { display: flex; justify-content: space-between; align-items: flex-end; margin-bottom: 16px; }
.head small { color: #719680; letter-spacing: 2px; }
.head h1 { margin: 6px 0; color: #234a34; font-size: 26px; }
.head p { margin: 0; color: #6d8074; }
.cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 18px; }
.metric span { display: block; color: #75847b; font-size: 13px; }
.metric strong { display: block; margin-top: 10px; color: #214a34; font-size: 24px; }
.forms { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 18px; }
.table-head { display: flex; justify-content: space-between; align-items: center; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, 1fr); } .forms { grid-template-columns: 1fr; } }
</style>
