<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

const rate = ref('0.006')
const savedRate = ref('0.006')
const thresholdMode = ref('HUNDRED')
const minimum = ref('100')
function selectThreshold(event: Event) { thresholdMode.value = (event.target as HTMLSelectElement).value }
const savedThreshold = ref('HUNDRED')
const thresholdValue = computed(() => thresholdMode.value === 'CUSTOM' ? 'CUSTOM:' + minimum.value.trim() : thresholdMode.value)
const saving = ref(false)
const percent = computed(() => {
  const value = Number(rate.value || 0)
  return Number.isFinite(value) ? Number((value * 100).toFixed(4)) : 0
})
const savedPercent = computed(() => Number((Number(savedRate.value || 0) * 100).toFixed(4)))
const dirty = computed(() => rate.value.trim() !== savedRate.value || thresholdValue.value !== savedThreshold.value)

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
  savedThreshold.value = response.data?.groups?.distribution?.find(item => item.config_key === 'withdraw.threshold_rule')?.config_value || 'HUNDRED'
  thresholdMode.value = savedThreshold.value.startsWith('CUSTOM:') ? 'CUSTOM' : savedThreshold.value
  minimum.value = thresholdMode.value === 'CUSTOM' ? savedThreshold.value.substring(7) : '100'
}

async function save() {
  const value = rate.value.trim()
  if (!/^(?:0(?:\.\d{1,6})?|1(?:\.0{1,6})?)$/.test(value)) {
    return ElMessage.warning('请输入 0 至 1 之间、最多六位小数，例如 0.006')
  }
  saving.value = true
  try {
    if (thresholdMode.value === 'CUSTOM' && (!/^(?:0|[1-9]\d{0,4})(?:\.\d{1,2})?$/.test(minimum.value.trim()) || Number(minimum.value) <= 0 || Number(minimum.value) > 50000)) {
      return ElMessage.warning('自定义最低金额为0.01至50000元，最多两位小数')
    }
    await postP0('system-settings', { key: 'withdraw.threshold_rule', value: thresholdValue.value })
    await postP0('system-settings', { key: 'withdraw.fee_rate', value })
    ElMessage.success('提现费率与门槛已保存，两种提现方式共用门槛')
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
      <p class="tip">费率适用于账户余额提现；收益转入消费余额仍无手续费。提现门槛由两种方式共用。已提交的申请保留原费率，调整只影响新申请。</p>
    </el-card>

    <el-card shadow="never" class="block">
      <el-form label-position="top">
        <div class="setting-row"><el-form-item label="手续费率（小数）">
          <el-input v-model="rate" placeholder="例如 0.006" style="max-width:320px">
            <template #append>{{ percent }}%</template>
          </el-input>
        </el-form-item>
        <el-form-item label="提现门槛（提现到余额 / 余额提现共用）">
          <select :value="thresholdMode" class="threshold-select" :disabled="saving" @change="selectThreshold">
            <option value="HUNDRED">100的倍数</option>
            <option value="NONE">无门槛</option>
            <option value="CUSTOM">自定义最低金额</option>
          </select>
          <el-input v-if="thresholdMode === 'CUSTOM'" v-model="minimum" placeholder="最低提现金额，最多两位小数" style="width:260px;margin-top:12px"><template #append>元</template></el-input>
        </el-form-item></div>
        <p class="tip">无门槛允许从0.01元提现；自定义仅限制最低金额，不要求100元的倍数。单笔金额上限为50000元。新规则同步网页与小程序，已提交申请不受影响。</p>
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
        <el-button v-if="dirty" @click="load">撤销修改</el-button>
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
.setting-row { display:flex;gap:24px;flex-wrap:wrap;align-items:flex-start }.setting-row .el-form-item{flex:1;min-width:260px}
.threshold-select{width:260px;height:34px;border:1px solid #dcdfe6;border-radius:4px;padding:0 10px;background:white;color:#606266}
</style>
