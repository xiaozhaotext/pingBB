<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
const error=ref(false)
function redirect(){error.value=false;uni.switchTab({url:'/pages/user/user',fail:()=>{error.value=true}})}
onLoad(redirect)
</script>
<template><view class="redirect"><text>{{error?'页面跳转失败，请点击重试':'正在打开我的账户…'}}</text><button v-if="error" @click="redirect">打开我的账户</button></view></template>
<style scoped>.redirect{padding:48px 24px;text-align:center;color:#285a62}</style>
