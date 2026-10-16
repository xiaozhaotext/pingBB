<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
const sandboxMode = import.meta.env.VITE_SANDBOX_MODE === 'true'
</script>

<template>
  <!-- 通过ConfigProvider全局设置国际化 -->
  <el-config-provider :locale="zhCn">
    <div id="app">
      <el-alert v-if="sandboxMode" title="演示沙箱 · 独立测试数据库 · 支付不扣真实资金" type="warning" :closable="false" />
      <router-view />
    </div>
  </el-config-provider>
</template>

<style></style>
