<template>
  <view class="page">
    <view class="card">
      <text class="title">合伙人和联创</text>
      <text class="intro">购买 3999 元会员身份牌后，按直接邀请成功购买会员的人数自动升级。</text>
      <view class="row"><text>直邀满 3 人</text><text>合伙人池 2 份，上限 8000 元</text></view>
      <view class="row"><text>直邀满 6 人</text><text>合伙人池 4 份，上限 16000 元</text></view>
      <view class="row"><text>直邀满 9 人</text><text>联创：合伙人池 6 份，上限 24000 元；联创池 1 份，上限 1200 元</text></view>
      <text class="intro">每个池按所有人持有的总份数分配池内金额；分红额度不等于可提现余额。</text>
    </view>
  </view>
</template>

<style scoped>
.page{min-height:100vh;padding:24rpx;background:#f2f6f5}.card{padding:32rpx;border-radius:24rpx;background:#fff}.title{display:block;font-size:36rpx;font-weight:700;color:#28565a}.intro{display:block;margin:24rpx 0;color:#829599;font-size:25rpx;line-height:1.7}.row{display:flex;justify-content:space-between;gap:24rpx;padding:28rpx 0;border-bottom:1rpx solid #edf2f1;font-size:26rpx;color:#405e62}.row text:last-child{text-align:right;color:#167a80}
</style>
