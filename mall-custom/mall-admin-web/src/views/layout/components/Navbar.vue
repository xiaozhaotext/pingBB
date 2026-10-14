<script lang="ts" setup>
import { computed, ref } from 'vue'
import Breadcrumb from '@/components/Breadcrumb/index.vue'
import Hamburger from '@/components/Hamburger/index.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { ArrowDown } from '@element-plus/icons-vue'
import defaultAvatar from '@/assets/default-avatar.svg'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postP0 } from '@/apis/p0'

// 定义组件名称
defineOptions({
  name: 'Navbar'
})

const appStore = useAppStore()
const userStore = useUserStore()

const sidebar = computed(() => appStore.sidebar)
const avatar = computed(() => userStore.userInfo.avatar || defaultAvatar)
const handleAvatarError = (event: Event) => {
  const image = event.target as HTMLImageElement
  if (image.dataset.fallback) return
  image.dataset.fallback = '1'
  image.src = defaultAvatar
}

// 处理开关侧边栏操作
const handleToggleSideBar = () => {
  appStore.toggleSideBar()
}

// 处理用户登出
const handleLogout = async () => {
  await userStore.userLogout()
  // 为了重新实例化vue-router对象 避免bug
  location.reload()
}

// 系统锁屏：锁定后需重新校验登录密码
const handleLock = () => {
  appStore.lock()
}

// 锁屏设置：空闲自动锁屏超时（分钟，5~120，默认 30）
const lockSettingDialog = ref(false)
const idleMinutesInput = ref(30)
const openLockSetting = () => {
  idleMinutesInput.value = appStore.idleTimeoutMinutes
  lockSettingDialog.value = true
}
const saveLockSetting = () => {
  const v = Math.round(Number(idleMinutesInput.value))
  if (!Number.isFinite(v) || v < 5 || v > 120) {
    ElMessage.warning('请输入 5~120 分钟之间的数值')
    return
  }
  appStore.setIdleTimeout(v)
  lockSettingDialog.value = false
  ElMessage.success('空闲 ' + v + ' 分钟后自动锁屏，已保存')
}

// 清除后台权限缓存
const handleClearCache = async () => {
  await ElMessageBox.confirm('将清除后台权限缓存（ums:admin、ums:resourceList）；登录状态与交易幂等键不受影响。', '清除缓存', { type: 'warning' })
  const response = await postP0<Record<string, unknown>>('system-settings/cache/clear', {})
  ElMessage.success(`已清理 ${response.data?.deleted ?? 0} 个缓存键`)
}
</script>

<template>
  <el-menu class="navbar" mode="horizontal">
    <hamburger class="hamburger-container" :toggle-click="handleToggleSideBar" :is-active="sidebar.opened"></hamburger>
    <breadcrumb></breadcrumb>
    <el-dropdown class="avatar-container" trigger="click">
      <div class="avatar-wrapper">
        <img class="user-avatar" :src="avatar" @error="handleAvatarError">
        <el-icon class="el-icon-caret-bottom">
          <arrow-down />
        </el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu class="user-dropdown">
          <router-link class="inlineBlock" to="/">
            <el-dropdown-item>
              首页
            </el-dropdown-item>
          </router-link>
          <router-link class="inlineBlock" to="/account"><el-dropdown-item>账户安全</el-dropdown-item></router-link>
          <el-dropdown-item @click="handleLock">系统锁屏</el-dropdown-item>
          <el-dropdown-item @click="openLockSetting">锁屏设置</el-dropdown-item>
          <el-dropdown-item @click="handleClearCache">清除缓存</el-dropdown-item>
          <el-dropdown-item divided>
            <span @click="handleLogout" style="display:block;">退出</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </el-menu>
  <el-dialog v-model="lockSettingDialog" title="锁屏设置" width="400px">
    <el-form label-position="top">
      <el-form-item label="空闲自动锁屏（分钟）">
        <el-input-number v-model="idleMinutesInput" :min="5" :max="120" :step="5" />
      </el-form-item>
      <p style="color: #909399; font-size: 12px; margin: 0;">无操作超过该时长后自动锁屏，需输入登录密码解锁。默认 30 分钟，可设 5~120 分钟。</p>
    </el-form>
    <template #footer>
      <el-button @click="lockSettingDialog = false">取消</el-button>
      <el-button type="primary" @click="saveLockSetting">保存</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.navbar {
  height: 50px;
  line-height: 50px;
  border-radius: 0px !important;
  position: sticky;
  top: 0;
  z-index: 1002;
  background: #fff;

  .hamburger-container {
    line-height: 58px;
    height: 50px;
    float: left;
    padding: 0 10px;
    min-width: 44px;
    cursor: pointer;
  }

  .screenfull {
    position: absolute;
    right: 90px;
    top: 16px;
    color: red;
  }

  .avatar-container {
    height: 50px;
    display: inline-block;
    position: absolute;
    right: 35px;

    .avatar-wrapper {
      cursor: pointer;
      margin-top: 5px;
      position: relative;

      .user-avatar {
        width: 40px;
        height: 40px;
        border-radius: 10px;
      }

      .el-icon-caret-bottom {
        position: absolute;
        right: -20px;
        top: 25px;
        font-size: 12px;
      }
    }
  }
}
</style>
