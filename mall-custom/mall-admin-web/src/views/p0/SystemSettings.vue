<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getP0, postP0 } from '@/apis/p0'

type Field = { config_key: string; label: string; secret: boolean; config_value: string | null; configured: boolean }
type Groups = Record<string, Field[]>

const tabs = [
  { name: 'company', label: '公司信息' },
  { name: 'watermark', label: '水印设置' },
  { name: 'upload', label: '上传设置' },
  { name: 'distribution', label: '分销与分红' },
  { name: 'miniapp', label: '小程序' },
  { name: 'ip_access', label: 'IP 访问设置' },
  { name: 'content', label: '服务内容' },
  { name: 'cache', label: '缓存维护' },
]
const switches = ['watermark.enabled', 'ip.access.enabled', 'upload.image_optimize',
  'distribution.enabled', 'dividend.distribution.enabled', 'share.order.bonus.enabled']
const selects: Record<string, { value: string; label: string }[]> = {
  'watermark.position': [
    { value: 'bottomRight', label: '右下角' }, { value: 'bottomLeft', label: '左下角' },
    { value: 'topRight', label: '右上角' }, { value: 'topLeft', label: '左上角' },
    { value: 'tile', label: '平铺' },
  ],
  'ip.access.mode': [
    { value: 'allow', label: '仅允许列表内的 IP' }, { value: 'deny', label: '仅拒绝列表内的 IP' },
  ],
}
const multiline = ['ip.access.list']
const contentLabels: Record<string, string> = { privacy_policy: '隐私政策', user_agreement: '用户协议', registration_notice: '注册须知' }

const active = ref('company')
const loading = ref(false)
const saving = ref(false)
const groups = ref<Groups>({})
const drafts = ref<Record<string, string>>({})
const original = ref<Record<string, string>>({})
const currentIp = ref('')
const ipSummary = ref<{ enabled: boolean; mode: string; entries: number } | null>(null)
const contentKey = ref('privacy_policy')
const contentText = ref('')
const contentUpdated = ref<string | null>(null)
const migrationRequired = ref(false)
const cacheResult = ref<Record<string, unknown> | null>(null)

const fields = computed(() => groups.value[active.value] ?? [])
const dirtyKeys = computed(() => Object.keys(drafts.value).filter(key => drafts.value[key] !== original.value[key]))

function text(value: unknown, fallback = ''): string {
  return value === null || value === undefined ? fallback : String(value)
}

async function load() {
  loading.value = true
  try {
    const response = await getP0<Record<string, unknown>>('system-settings')
    const data = response.data ?? {}
    groups.value = (data.groups as Groups | undefined) ?? {}
    currentIp.value = text(data.currentIp, '未知')
    ipSummary.value = (data.ipAccess as typeof ipSummary.value) ?? null
    const snapshot: Record<string, string> = {}
    Object.values(groups.value).forEach(list => list.forEach(field => { snapshot[field.config_key] = text(field.config_value) }))
    // 开关类配置统一为 '1'/'0'，与开关组件的 active/inactive 取值保持一致（空值视为关闭）
    switches.forEach(key => { if (key in snapshot) snapshot[key] = snapshot[key] === '1' ? '1' : '0' })
    drafts.value = { ...snapshot }
    original.value = { ...snapshot }
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!dirtyKeys.value.length) return ElMessage.info('没有需要保存的修改')
  if (dirtyKeys.value.includes('ip.access.enabled') && drafts.value['ip.access.enabled'] === '1') {
    await ElMessageBox.confirm(
      `启用后，只有列表内的 IP 可以访问后台。当前 IP：${currentIp.value || '未知'}。请确认列表已包含你的 IP。`,
      '确认启用 IP 限制', { type: 'warning' })
  }
  saving.value = true
  try {
    for (const key of dirtyKeys.value) {
      await postP0('system-settings', { key, value: drafts.value[key] })
    }
    ElMessage.success(`已保存 ${dirtyKeys.value.length} 项设置`)
    await load()
  } finally {
    saving.value = false
  }
}

async function loadContent() {
  try {
    const response = await getP0<Record<string, unknown>>(`system-settings/content/${contentKey.value}`)
    contentText.value = text(response.data?.content_value)
    contentUpdated.value = response.data?.update_time ? text(response.data.update_time) : null
    migrationRequired.value = response.data?.migration_required === true
  } catch {
    contentText.value = ''
  }
}

async function saveContent() {
  if (!contentText.value.trim()) return ElMessage.warning('内容不能为空')
  saving.value = true
  try {
    await postP0('system-settings/content', { key: contentKey.value, value: contentText.value })
    ElMessage.success('已保存，移动端“我的-隐私政策”实时读取该内容')
    await loadContent()
  } finally {
    saving.value = false
  }
}

async function clearCache() {
  await ElMessageBox.confirm('将清除后台权限缓存（不会强制下线，也不影响交易幂等键）。', '清除缓存', { type: 'warning' })
  cacheResult.value = (await postP0<Record<string, unknown>>('system-settings/cache/clear', {})).data ?? null
  ElMessage.success('缓存已清理')
}

onMounted(async () => { await load(); await loadContent() })
</script>

<template>
  <main class="settings">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <h2>平台设置</h2>
          <span class="hint">
            当前访问 IP：{{ currentIp }}
            <template v-if="ipSummary">
              ；IP 限制{{ ipSummary.enabled ? '已启用' : '未启用' }}（{{ ipSummary.mode === 'deny' ? '拒绝' : '允许' }}模式，共 {{ ipSummary.entries }} 条）
            </template>
          </span>
        </div>
      </template>
      <el-tabs v-model="active">
        <el-tab-pane v-for="tab in tabs" :key="tab.name" :label="tab.label" :name="tab.name">
          <div v-loading="loading" class="pane">
            <template v-if="tab.name === 'content'">
              <el-alert v-if="migrationRequired" type="warning" show-icon :closable="false"
                title="未检测到 sys_content 表，请先在数据库执行 22_admin_settings_center.sql" />
              <el-radio-group v-model="contentKey" @change="loadContent">
                <el-radio-button v-for="(label, key) in contentLabels" :key="key" :value="key">{{ label }}</el-radio-button>
              </el-radio-group>
              <el-alert v-if="contentKey === 'privacy_policy'" title="隐私政策已采用草稿与审核发布流程，请前往公司信息与隐私政策。" type="info" :closable="false" />
              <router-link v-if="contentKey === 'privacy_policy'" to="/p0/site-profile">前往隐私政策审核发布</router-link>
              <el-input v-else v-model="contentText" type="textarea" :rows="16" maxlength="60000" show-word-limit
                placeholder="填写隐私政策正文，支持纯文本；移动端“我的 - 隐私政策”将展示该内容" />
              <div class="actions">
                <el-button v-if="contentKey !== 'privacy_policy'" type="primary" :loading="saving" @click="saveContent">保存内容</el-button>
                <span v-if="contentUpdated" class="hint">最近更新：{{ contentUpdated }}</span>
              </div>
            </template>
            <template v-else-if="tab.name === 'cache'">
              <el-alert type="info" show-icon :closable="false"
                title="清除后台权限缓存（ums:admin、ums:resourceList）" description="登录令牌为无状态 JWT，不会被强制下线；交易幂等键不受影响。" />
              <div class="actions">
                <el-button type="warning" @click="clearCache">清除缓存</el-button>
                <span v-if="cacheResult" class="hint">已清理 {{ cacheResult.deleted }} 个键：{{ (cacheResult.prefixes as string[] | undefined)?.join('、') }}</span>
              </div>
            </template>
            <template v-else>
              <el-alert v-if="tab.name === 'ip_access'" type="warning" show-icon :closable="false"
                title="启用后仅列表内的 IP 可访问后台（本机回环地址始终允许）"
                description="支持单个 IP 或 CIDR，例如 203.0.113.7, 10.0.0.0/24。请先确认列表包含当前访问 IP。" />
              <el-form label-position="top" class="form">
                <el-form-item v-for="field in fields" :key="field.config_key" :label="field.label">
                  <el-switch v-if="switches.includes(field.config_key)" :model-value="drafts[field.config_key] === '1' ? '1' : '0'"
                    active-value="1" inactive-value="0"
                    @update:model-value="value => drafts[field.config_key] = String(value)" />
                  <el-select v-else-if="selects[field.config_key]" v-model="drafts[field.config_key]" class="wide">
                    <el-option v-for="option in selects[field.config_key]" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                  <el-input v-else-if="multiline.includes(field.config_key)" v-model="drafts[field.config_key]" type="textarea" :rows="4"
                    placeholder="203.0.113.7, 10.0.0.0/24" />
                  <el-input v-else v-model="drafts[field.config_key]" :type="field.secret ? 'password' : 'text'"
                    :show-password="field.secret" :placeholder="field.configured ? '已配置，留空保存表示清除' : '未配置'" />
                  <span v-if="field.secret" class="hint">敏感项：列表仅显示脱敏值，未修改时不会覆盖原值</span>
                </el-form-item>
              </el-form>
              <div class="actions">
                <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
                <span class="hint">{{ dirtyKeys.length }} 项待保存</span>
              </div>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </main>
</template>

<style scoped>
.settings { padding: 24px; max-width: 900px; margin: auto; }
h2 { color: #285a62; margin: 0; }
.header { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.hint { color: #698789; font-size: 12px; }
.pane { padding-top: 8px; display: flex; flex-direction: column; gap: 12px; }
.form { max-width: 560px; }
.wide { width: 100%; }
.actions { display: flex; align-items: center; gap: 12px; }
</style>
