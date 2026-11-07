<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

const rate = ref('0.006')
const savedRate = ref('0.006')
const saving = ref(false)
const percent = computed(() => {
  const value = Number(rate.value || 0)
  return Number.isFinite(value) ? Number((value * 100).toFixed(4)) : 0
})
const savedPercent = computed(() => Number((Number(savedRate.value || 0) * 100).toFixed(4)))
const dirty = computed(() => rate.value.trim() !== savedRate.value)

const previewAmounts = [100, 500, 1000, 5000]
const previewRows = computed(() => previewAmounts.map(amount => {
  const value = Number(rate.value || 0)
  const fee = Number.isFinite(value) ? amount * value : 0
  return { amount, fee: fee.toFixed(2), net: (amount - fee).toFixed(2) }
}))

const presets = [
  { label: '免手续费', value: '0' },
  { label: '0.6%', value: '0.006' },
  { label: '1%', value: '0.01' },
]

async function load() {
  const response = await getP0<{ groups: Record<string, { config_key: string; config_value: string | null }[]> }>('system-settings')
  const field = response.data?.groups?.distribution?.find(item => item.config_key === 'withdraw.fee_rate')
  const value = field?.config_value || '0.006'
  rate.value = value
  savedRate.value = value
}

async function save() {
  const value = rate.value.trim()
  if (!/^(?:0(?:\.\d{1,6})?|1(?:\.0{1,6})?)$/.test(value)) {
    return ElMessage.warning('请输入 0 至 1 之间、最多六位小数，例如 0.006')
  }
  saving.value = true
  try {
    await postP0('system-settings', { key: 'withdraw.fee_rate', value })
    ElMessage.success('提现手续费率已保存，新提交的提现申请按下单时的费率计算')
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="page">
    <h1>提现手续费调整</h1>
    <el-card shadow="never" class="block">
      <div class="current">
        <span class="label">当前生效费率</span>
        <strong class="value">{{ savedPercent }}%</strong>
        <span class="hint">（{{ savedRate }}）</span>
      </div>
      <p class="tip">该费率同时作用于「账户余额提现」与「佣金提现」。已提交的申请按提交时的费率保留，调整后只影响新提交的申请。</p>
    </el-card>

    <el-card shadow="never" class="block">
      <el-form label-position="top">
        <el-form-item label="手续费率（小数）">
          <el-input v-model="rate" placeholder="例如 0.006" style="max-width:320px">
            <template #append>{{ percent }}%</template>
          </el-input>
        </el-form-item>
        <div class="presets">
          <span class="label">快捷选择：</span>
          <el-button v-for="item in presets" :key="item.value" size="small" @click="rate = item.value">{{ item.label }}</el-button>
        </div>
        <el-alert type="info" :closable="false" show-icon
          title="填 0.006 即 0.6%：提现 1000 元扣 6 元手续费，实际打款 994 元；填 0 表示提现不收手续费。" />
      </el-form>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>按当前填写的费率试算</template>
      <el-table :data="previewRows" size="small">
        <el-table-column prop="amount" label="提现金额（元）" width="140" />
        <el-table-column prop="fee" label="手续费（元）" width="140" />
        <el-table-column prop="net" label="实际打款（元）" />
      </el-table>
      <div class="actions">
        <el-button type="primary" :loading="saving" :disabled="!dirty" @click="save">保存设置</el-button>
        <el-button v-if="dirty" @click="rate = savedRate">撤销修改</el-button>
      </div>
    </el-card>
  </main>
</template>

<style scoped>
.page { padding: 24px; max-width: 820px; }
.page h1 { color: #285a62; margin-bottom: 16px; }
.block { margin-bottom: 16px; }
.current { display: flex; align-items: baseline; gap: 10px; }
.current .label { color: #667b7b; }
.current .value { color: #285a62; font-size: 28px; }
.current .hint { color: #9aa9a9; font-size: 12px; }
.tip { color: #667b7b; margin: 12px 0 0; }
.preview { margin-left: 12px; color: #285a62; font-weight: 600; }
.presets { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; }
.presets .label { color: #667b7b; }
.actions { margin-top: 16px; }
</style>
