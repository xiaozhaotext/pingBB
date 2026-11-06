<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

const rate = ref('0.006')
const saving = ref(false)
const percent = computed(() => Number(rate.value || 0) * 100)
async function load() {
  const response = await getP0<{groups: Record<string, {config_key: string; config_value: string | null}[]>}>('system-settings')
  const field = response.data?.groups?.distribution?.find(item => item.config_key === 'withdraw.fee_rate')
  rate.value = field?.config_value || '0.006'
}
async function save() {
  const value = rate.value.trim()
  if (!/^(?:0(?:\.\d{1,6})?|1(?:\.0{1,6})?)$/.test(value)) return ElMessage.warning('请输入0至1、最多六位小数，例如0.006')
  saving.value = true
  try {
    await postP0('system-settings', {key: 'withdraw.fee_rate', value})
    ElMessage.success('提现手续费率已保存，新申请按新费率计算')
    await load()
  } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <main class="page">
    <h1>提现手续费</h1>
    <el-card shadow="never">
      <p>填写费率小数，末尾显示对应百分比。已提交申请的手续费按提交时费率保留。</p>
      <el-form label-position="top">
        <el-form-item label="手续费率">
          <el-input v-model="rate" placeholder="例如 0.006" style="max-width:320px"><template #append>{{ percent }}%</template></el-input>
        </el-form-item>
        <p>例如填 0.006，即 0.6%；提现 1000 元扣 6 元，实际打款 994 元。</p>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </el-form>
    </el-card>
  </main>
</template>
<style scoped>.page{padding:24px;max-width:760px}.page h1{color:#285a62}.page p{color:#667b7b}</style>
