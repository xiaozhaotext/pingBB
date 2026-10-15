<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0, type P0Row } from '@/apis/p0'

const identityLabels: Record<number, string> = { 0: '游客', 1: '会员', 2: '合伙人', 3: '联创' }

const loading = ref(false)
const saving = ref(false)
const members = ref<P0Row[]>([])
const travelRequests = ref<P0Row[]>([])
const identityStats = ref<P0Row[]>([])
const keyword = ref('')
const referrerForm = ref({ memberId: null as number | null, referrerId: null as number | null, reason: '' })

const filtered = computed(() => {
  const key = keyword.value.trim()
  if (!key) return members.value
  return members.value.filter(row => String(row.username || '').includes(key) || String(row.phone || '').includes(key) || String(row.id) === key)
})
const counts = computed(() => {
  const result: Record<number, number> = { 0: 0, 1: 0, 2: 0, 3: 0 }
  identityStats.value.forEach(row => {
    const type = Number(row.identityType ?? 0)
    result[type] = Number(row.members || 0)
  })
  return result
})

async function load() {
  loading.value = true
  try {
    const [memberResult, travelResult, statsResult] = await Promise.all([
      getP0<P0Row[]>('members'), getP0<P0Row[]>('travel/custom-requests'),
      getP0<{ identityStats: P0Row[] }>('members/stats'),
    ])
    members.value = memberResult.data || []
    travelRequests.value = travelResult.data || []
    identityStats.value = statsResult.data?.identityStats || []
  } catch {
    members.value = []
    travelRequests.value = []
    identityStats.value = []
    ElMessage.error('会员或路线需求加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(load)

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

async function updateTravel(id: number, status: 'IN_REVIEW' | 'CONFIRMED' | 'CLOSED') {
  const { value: note } = await ElMessageBox.prompt('填写处理备注（至少2字）', '处理路线需求', {
    inputValidator: (value: string) => value.trim().length >= 2 || '至少填写2字',
  })
  await postP0(`travel/custom-requests/${id}/status`, { status, note })
  ElMessage.success('路线需求状态已更新')
  await load()
}
</script>

<template>
  <main class="member-identity" v-loading="loading">
    <header class="head">
      <div>
        <small>会员</small>
        <h1>会员身份</h1>
        <p>游客注册，购买 3999 元身份牌成为会员；直邀满 3 人成为合伙人，满 9 人成为联创。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </header>
    <section class="cards">
      <el-card shadow="never" class="metric"><span>游客</span><strong>{{ counts[0] || 0 }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>会员</span><strong>{{ counts[1] || 0 }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>合伙人</span><strong>{{ counts[2] || 0 }}</strong></el-card>
      <el-card shadow="never" class="metric"><span>联创</span><strong>{{ counts[3] || 0 }}</strong></el-card>
    </section>
    <section class="forms">
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
          <template #default="{ row }">{{ identityLabels[Number(row.identity_type ?? 0)] || '游客' }}</template>
        </el-table-column>
        <el-table-column prop="paid_referrals" label="有效直邀会员" width="120" />
        <el-table-column prop="inviter_id" label="推荐人 ID" width="110" />
        <el-table-column prop="create_time" label="注册时间" min-width="170" />
      </el-table>
    </el-card>
    <el-card shadow="never" class="table-card travel-card">
      <template #header><strong>会员自定义路线需求</strong></template>
      <el-table :data="travelRequests" stripe empty-text="暂无路线需求">
        <el-table-column prop="member_id" label="会员 ID" width="100" />
        <el-table-column prop="username" label="会员" width="130" />
        <el-table-column prop="destination" label="目的地" min-width="140" />
        <el-table-column prop="departure_date" label="计划出发" width="120" />
        <el-table-column prop="traveler_count" label="人数" width="80" />
        <el-table-column prop="requirements" label="需求" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="admin_note" label="处理备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="处理" width="225">
          <template #default="{ row }">
            <el-button link type="primary" @click="updateTravel(Number(row.id), 'IN_REVIEW')">处理中</el-button>
            <el-button link type="success" @click="updateTravel(Number(row.id), 'CONFIRMED')">确认</el-button>
            <el-button link @click="updateTravel(Number(row.id), 'CLOSED')">关闭</el-button>
          </template>
        </el-table-column>
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
.travel-card { margin-top: 16px; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, 1fr); } .forms { grid-template-columns: 1fr; } }
</style>
