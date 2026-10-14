<script setup lang="ts">
import { onMounted } from 'vue'
onMounted(()=>uni.hideTabBar({animation:false}))
defineProps<{ active: string }>()
const items=[
 {key:'home',label:'首页',url:'/pages/index/index',icon:'home'},
 {key:'cart',label:'购物车',url:'/pages/cart/cart',icon:'cart'},
 {key:'promotion',label:'推广中心',url:'/pages/member/promotion',icon:'network'},
 {key:'user',label:'我的',url:'/pages/user/user',icon:'user'},
]
const tabs=new Set(['/pages/index/index','/pages/zone/pick','/pages/zone/rebuy','/pages/user/user'])
function go(url:string){
 const pages=getCurrentPages();if(pages[pages.length-1]?.route===url.slice(1))return
 if(tabs.has(url)) uni.switchTab({url})
 else uni.navigateTo({url})
}
</script>
<template>
 <view class="main-navigation-space" />
 <view class="main-navigation" role="navigation" aria-label="商城主导航">
  <view v-for="item in items" :key="item.key" class="main-nav-item" :class="{selected:active===item.key || (item.key==='home' && ['pick','rebuy'].includes(active))}" role="button" tabindex="0" :aria-label="item.label" :aria-current="active===item.key || (item.key==='home' && ['pick','rebuy'].includes(active))?'page':undefined" @keydown.enter="go(item.url)" @click="go(item.url)">
   <image :src="`/static/main-nav/${item.icon}.svg`" class="nav-icon" mode="aspectFit" />
   <text>{{item.label}}</text>
  </view>
 </view>
</template>
<style scoped>
.main-navigation-space{height:calc(76px + env(safe-area-inset-bottom))}.main-navigation{position:fixed;bottom:0;left:0;right:0;z-index:99;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));padding:9px 6px calc(9px + env(safe-area-inset-bottom));background:#fffffff5;backdrop-filter:blur(14px);border-top:1px solid #dce6e6;box-shadow:0 -4px 20px #284f5909}.main-nav-item{min-height:48px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;color:#819497;font-size:11px;cursor:pointer;border-radius:12px}.nav-icon{width:23px;height:23px;opacity:.55}.selected{color:#167a80;background:#edf6f5;font-weight:600}.selected .nav-icon{opacity:1}@media(min-width:900px){.main-navigation{padding-left:max(20px,calc((100% - 1200px)/2));padding-right:max(20px,calc((100% - 1200px)/2))}.main-nav-item{font-size:13px}}
</style>
