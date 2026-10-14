<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
const error=ref(false)
function open(){error.value=false;uni.redirectTo({url:'/pages/member/promotion',fail:()=>{error.value=true}})}
onLoad(open)
</script>
<template><view class="redirect"><text>{{error?'页面跳转失败，请点击重试':'正在打开推广中心…'}}</text><button v-if="error" @click="open">打开推广中心</button></view></template>
<style scoped>.redirect{padding:48px 24px;text-align:center;color:#285a62}</style>
