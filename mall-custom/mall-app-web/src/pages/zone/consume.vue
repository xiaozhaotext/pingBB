<template><DesktopHeader/><view class="reserved"><text class="reserved-tag">专区预留</text><text class="reserved-title">消费区</text><text class="reserved-note">这里为消费区预留展示空间，暂未开放商品与交易。</text><button @click="uniApi.switchTab({url:'/pages/index/index'})">返回首页</button></view></template>
<script setup lang="ts">
const uniApi = uni
import DesktopHeader from '@/components/DesktopHeader.vue';
</script>
<style scoped>.reserved{min-height:70vh;margin:24px;padding:50px 24px;border:1px dashed #dacaab;border-radius:24px;background:linear-gradient(140deg,#fff6df,#eef1e5);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;color:#586b54}.reserved-title{font-size:34px;font-weight:700}.reserved-tag{font-size:12px;border:1px solid #dacaab;border-radius:16px;padding:5px 12px}.reserved-note{font-size:14px;text-align:center;line-height:1.8}.reserved button{background:#365d50;color:white;border-radius:24px;padding:0 28px;font-size:14px}</style>

<style scoped>.reserved{background:linear-gradient(130deg,#203a48aa,#203a481c),url('/static/travel-photo/ocean-terrace.jpg') center/cover;color:#fff;border:0;box-shadow:0 14px 30px #213b4922}.reserved-tag{border-color:#fff8}.reserved button{background:#fff8e3;color:#344b3d}.reserved-note{text-shadow:0 1px 6px #13283090}</style>