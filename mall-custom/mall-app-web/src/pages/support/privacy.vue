<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '@/utils/http'
const policy=ref<{title:string;content:string;published_at:string}|null>(null)
const loading=ref(false),error=ref('')
async function load(){loading.value=true;error.value='';try{policy.value=(await http<{title:string;content:string;published_at:string}>({method:'GET',url:'/home/site-profile/privacy'})).data}catch{error.value='隐私政策尚未发布或暂时无法加载，请稍后重试'}finally{loading.value=false}}
onMounted(load)
</script>
<template><view class="policy"><view v-if="loading">正在加载…</view><view v-else-if="error"><text>{{error}}</text><button @click="load">重试</button></view><view v-else-if="policy"><view class="title">{{policy.title}}</view><view class="time">发布时间：{{policy.published_at}}</view><text class="content" selectable>{{policy.content}}</text></view></view></template>
<style scoped>.policy{max-width:960px;margin:0 auto;padding:32rpx;background:#fff;min-height:70vh}.title{font-size:40rpx;font-weight:600;color:#285a62}.time{font-size:24rpx;color:#7a8d91;margin:24rpx 0}.content{white-space:pre-wrap;overflow-wrap:anywhere;font-size:28rpx;line-height:1.9}</style>
