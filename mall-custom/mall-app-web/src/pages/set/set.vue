<template>
  <view class="container">
    <view
      class="list-cell b-b m-t"
      @click="navTo('个人资料')"
      hover-class="cell-hover"
      :hover-stay-time="50"
    >
      <text class="cell-tit">个人资料</text>
      <text class="cell-more yticon icon-you"></text>
    </view>
    <view
      class="list-cell b-b"
      @click="navTo('/pages/address/address')"
      hover-class="cell-hover"
      :hover-stay-time="50"
    >
      <text class="cell-tit">收货地址</text>
      <text class="cell-more yticon icon-you"></text>
    </view>
    <view
      class="list-cell"
      @click="navTo('/pages/member/contact')"
      hover-class="cell-hover"
      :hover-stay-time="50"
    >
      <text class="cell-tit">联系资料</text>
      <text class="cell-more yticon icon-you"></text>
    </view>

    <view class="list-cell m-t">
      <text class="cell-tit">消息推送</text>
      <switch checked :color="themePrimary" @change="switchChange" />
    </view>
    <view class="list-cell m-t b-b">
      <text class="cell-tit">主题换肤</text>
      <view class="theme-options">
        <text
          v-for="t in THEMES"
          :key="t.key"
          :class="['theme-option', theme === t.key ? 'active' : '']"
          :style="theme === t.key ? { background: t.primary, color: '#fff' } : {}"
          @click="chooseTheme(t.key)"
        >
          {{ t.label }}
        </text>
      </view>
    </view>
    <view
      class="list-cell m-t b-b"
      @click="navTo('清除缓存')"
      hover-class="cell-hover"
      :hover-stay-time="50"
    >
      <text class="cell-tit">清除缓存</text>
      <text class="cell-more yticon icon-you"></text>
    </view>
    <view class="list-cell">
      <text class="cell-tit">检查更新</text>
      <text class="cell-tip">当前版本 1.0.0</text>
      <text class="cell-more yticon icon-you"></text>
    </view>
    <view class="list-cell log-out-btn" @click="toLogout">
      <text class="cell-tit">退出登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMemberStore } from '@/stores/member'
import { getTheme, setTheme, THEMES, getThemePrimary, type AppTheme } from '@/utils/theme'
// 获取会员store
const memberStore = useMemberStore()

// 换肤主题（存 uni.storage，刷新后保持；商品详情页 onShow 时读取即生效）
const theme = ref<AppTheme>(getTheme())
const themePrimary = computed(() => getThemePrimary(theme.value))
const chooseTheme = (t: AppTheme) => {
  setTheme(t)
  theme.value = t
  const label = THEMES.find((item) => item.key === t)?.label ?? t
  uni.showToast({ title: `已切换为${label}`, icon: 'none' })
}

// 退出登录
const toLogout = () => {
  uni.showModal({
    content: '确定要退出登录么',
    success: (e) => {
      if (e.confirm) {
        memberStore.memberLogout()
        uni.showToast({
          title: '已退出登录',
          icon: 'success',
        })
        setTimeout(() => {
          uni.navigateBack()
        }, 200)
      }
    },
  })
}

// 页面内导航
const navTo = (url: string) => {
  if (url.includes('pages')) {
    uni.navigateTo({
      url: url,
    })
  } else {
    uni.showToast({
      title: `跳转到${url}`,
      icon: 'none',
    })
  }
}

// 消息推送开关
const switchChange = (e: any) => {
  const statusTip = e.detail.value ? '打开' : '关闭'
  uni.showToast({
    title: `${statusTip}消息推送`,
    icon: 'none',
  })
}
</script>

<style lang="scss">
page {
  background: $page-color-base;
}
.list-cell {
  display: flex;
  align-items: baseline;
  padding: 20upx $page-row-spacing;
  line-height: 60upx;
  position: relative;
  background: #fff;
  justify-content: center;
  &.log-out-btn {
    margin-top: 40upx;
    .cell-tit {
      color: $uni-color-primary;
      text-align: center;
      margin-right: 0;
    }
  }
  &.cell-hover {
    background: #fafafa;
  }
  &.b-b:after {
    left: 30upx;
  }
  &.m-t {
    margin-top: 16upx;
  }
  .cell-more {
    align-self: baseline;
    font-size: $font-lg;
    color: $font-color-light;
    margin-left: 10upx;
  }
  .cell-tit {
    flex: 1;
    font-size: $font-base + 2rpx;
    color: $font-color-dark;
    margin-right: 10upx;
  }
  .cell-tip {
    font-size: $font-base;
    color: $font-color-light;
  }
  switch {
    transform: translateX(16upx) scale(0.84);
  }
  .theme-options {
    display: flex;
    gap: 16upx;
  }
  .theme-option {
    padding: 8upx 28upx;
    border-radius: 999upx;
    background: #f4f4f5;
    color: $font-color-base;
    font-size: $font-base;
    &.active {
      font-weight: 600;
    }
  }
}
</style>
