<script setup lang="ts">
import { computed,ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useMemberStore } from '@/stores/member'
import { getMyIdentityAPI,getMyInviteesAPI,getEarningsAccountAPI } from '@/apis/member'
import MainNavigation from '@/components/MainNavigation.vue'
import DesktopHeader from '@/components/DesktopHeader.vue'
import { getTriPoolsAPI, tierNames } from '@/apis/triPools'
const member=useMemberStore(),loggedIn=computed(()=>!!member.memberInfo)
const identity=ref('—'),inviteCount=ref<string|number>('—'),available=ref<string|number>('—'),total=ref<string|number>('—'),error=ref('')
const membershipV2=ref<boolean|null>(null)
async function load(){
 identity.value='—';inviteCount.value='—';available.value='—';total.value='—';error.value='';membershipV2.value=null
 if(!loggedIn.value){ membershipV2.value=true; return }
 const results=await Promise.allSettled([getMyIdentityAPI(),getMyInviteesAPI(),getEarningsAccountAPI()])
 if(!loggedIn.value)return
 results.forEach((r,i)=>{if(r.status==='rejected'){error.value='部分推广数据暂时无法加载，请重试';return}const d=r.value.data;if(i===0)identity.value=({0:'普通会员',1:'合伙人',2:'联创'} as Record<number,string>)[d?.identityType??0]||'未知身份';if(i===1)inviteCount.value=Array.isArray(d)?d.length:'—';if(i===2){available.value=d?.availableEarnings??'未开户';total.value=d?.totalEarnings??'未开户'}})
 try { const tri = (await getTriPoolsAPI()).data; membershipV2.value=!!tri.enabled; if (tri.enabled) { identity.value = tierNames[tri.tier || 'VISITOR'] } } catch { error.value = '当前身份加载失败，请重试' }
}
function go(url:string){uni.navigateTo({url:loggedIn.value?url:'/pages/public/login'})}
onShow(load)
</script>
<template>
 <DesktopHeader />
 <view class="promotion-page">
  <view class="promotion-hero"><text class="eyebrow">智游美 · 邀请与推荐</text><view class="title">推广中心</view><text>级别、推荐关系与收益记录集中在这里</text></view>
  <view v-if="!loggedIn" class="promotion-card guest"><text>登录后查看您的推广记录</text><button @click="go('/pages/public/login')">登录</button></view>
  <view class="promotion-grid">
   <view class="promotion-card"><view class="section-title">数据看板<text class="caption-small">账户实际数据</text></view><view class="metrics"><view><text>{{inviteCount}}</text><text class="caption-small">已绑定下级人数</text></view><view><text>{{identity}}</text><text class="caption-small">当前级别</text></view><view><text>{{total}}</text><text class="caption-small">累计收益</text></view><view><text>{{available}}</text><text class="caption-small">可提现余额</text></view></view></view>
   <view class="promotion-card"><view class="section-title">会员级别</view><template v-if="membershipV2"><view class="level-row"><text>游客</text><text class="caption-small">注册后默认身份</text></view><view class="level-row" @click="go('/pages/member/membership')"><text>会员</text><text class="caption-small">购买 3999 元身份牌 ›</text></view><view class="level-row" @click="go('/pages/member/partner')"><text>合伙人</text><text class="caption-small">有效直邀 3 人晋升 ›</text></view><view class="level-row" @click="go('/pages/member/cofounder')"><text>联创</text><text class="caption-small">有效直邀 9 人晋升 ›</text></view></template><template v-else-if="membershipV2 === false"><view class="level-row" @click="go('/pages/member/partner')"><text>合伙人</text><text class="caption-small">查看权益方案 ›</text></view><view class="level-row" @click="go('/pages/member/cofounder')"><text>联创</text><text class="caption-small">查看权益方案 ›</text></view></template><text v-else>身份规则加载中…</text></view>
   <view class="promotion-card"><view class="section-title">邀请与推荐</view><view class="level-row" @click="go('/pages/member/invitation')"><text>我的邀请码</text><text class="caption-small">邀请链接与二维码 ›</text></view><view class="level-row" @click="go('/pages/member/invitation')"><text>我的推荐</text><text class="caption-small">查看直属推荐关系 ›</text></view></view>
   <view class="promotion-card"><view class="section-title">收益记录</view><view class="level-row" @click="go('/pages/member/walletWithdraw?mode=earnings')"><text>我的收益</text><text class="caption-small">余额与收益明细 ›</text></view><view class="level-row" @click="go('/pages/member/dividend')"><text>贡献与分配记录</text><text class="caption-small">查看已有记录 ›</text></view></view>
  </view>
  <view v-if="error" class="promotion-error" @click="load">{{error}}，点击重试</view>
  <view class="promotion-note">此处展示账户记录；真实支付、分账、返佣与提现打款仍按审批开关限制，本次页面调整不启用相关规则。</view>
 </view>
 <MainNavigation active="promotion" />
</template>
<style scoped>
.promotion-page{max-width:1240px;margin:auto;padding:24px;background:#f8f5f4}.promotion-hero{padding:32px;border-radius:22px;color:white;background:linear-gradient(90deg,#4c2616e8,#4c261648),url('/static/travel-photo/alpine-lake.jpg') center/cover}.eyebrow{font-size:12px;letter-spacing:2px}.title{font-size:30px;font-weight:700;margin:12px 0}.promotion-hero>text:last-child{font-size:13px;color:#f0e6e1}.promotion-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}.promotion-card{padding:24px;border-radius:18px;background:#fff}.section-title{font-weight:600;color:#623928;margin-bottom:20px}.section-title .caption-small{float:right;color:#b49081;font-size:11px;font-weight:400}.metrics{display:grid;grid-template-columns:1fr 1fr;gap:12px}.metrics>view{padding:18px;background:#f8f4f2;border-radius:12px}.metrics text{display:block;font-size:20px;color:#803e22;font-weight:600}.metrics .caption-small{display:block;margin-top:8px;color:#aa8271;font-size:11px;font-weight:400;}.level-row{display:flex;justify-content:space-between;align-items:center;padding:18px 0;border-bottom:1px solid #f3eeec;cursor:pointer}.level-row:last-child{border-bottom:0}.level-row .caption-small{color:#b49182;font-size:12px;font-weight:400;}.promotion-note,.promotion-error{margin-top:18px;padding:16px;color:#a67c6a;font-size:12px;line-height:1.8}.promotion-error{color:#ac4740}.guest{margin-top:18px}.guest button{margin-top:12px}@media(max-width:600px){.promotion-page{padding:14px}.promotion-grid{grid-template-columns:1fr}.promotion-card{padding:20px}}
</style>

