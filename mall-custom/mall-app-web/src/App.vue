<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { getTheme, applyTheme } from '@/utils/theme'
import { useMemberStore } from '@/stores/member'
import { contactSaveKey, isContactHistoryEntry } from '@/utils/contactNavigation'

onLaunch(() => {
  // 应用已保存的换肤主题（刷新后保持）
  applyTheme(getTheme())
  // #ifdef H5
  let returning = false
  window.addEventListener('popstate', () => {
    const id = useMemberStore().memberInfo?.id
    if (!id || !uni.getStorageSync(contactSaveKey(id)) || !isContactHistoryEntry(window.location.href)) return
    setTimeout(() => {
      if (returning || !isContactHistoryEntry(window.location.href)) return
      returning = true
      uni.reLaunch({ url: '/pages/user/user', complete: () => { returning = false } })
    }, 0)
  })
  // #endif
  console.log('App Launch')
})
onShow(() => {
  console.log('App Show')
})
onHide(() => {
  console.log('App Hide')
})
</script>

<style lang="scss">
@use '@/styles/fonts.scss';
@use '@/styles/travel.scss';
/* #ifdef H5 */
uni-tabbar{display:none!important}
body:has(.main-navigation) .uni-page-wrapper{padding-bottom:0!important}
/* #endif */
/* #ifdef H5 */
@media (min-width: 900px) {
  body:has(.desktop-home, .desktop-site-header) { background: #f6f7f4; }
  body:has(.desktop-home, .desktop-site-header) .uni-page-head,
  body:has(.desktop-home, .desktop-site-header) .uni-tabbar { display: none !important; }
  body:has(.desktop-home, .desktop-site-header) .uni-page-wrapper { padding-bottom: 0 !important; }
  body:has(.desktop-home, .desktop-site-header) .container,
  body:has(.desktop-home, .desktop-site-header) .content { max-width: 1240px; margin-left: auto; margin-right: auto; box-sizing: border-box; }
  body:has(.desktop-home, .desktop-site-header) .custom-navbar { display: none !important; }
}
/* #endif */
// 字体图标

view,
navigator,
input,
scroll-view {
  box-sizing: border-box;
}

button::after {
  border: none;
}

swiper,
scroll-view {
  flex: 1;
  height: 100%;
  overflow: hidden;
}

image {
  width: 100%;
  height: 100%;
  vertical-align: middle;
}

// 一行省略
.clamp {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

// 两行省略
.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  line-clamp: 2;
  -webkit-line-clamp: 2;
}

// 边框样式（底部边框）
.b-b:after,
.b-t:after {
  position: absolute;
  z-index: 3;
  left: 0;
  right: 0;
  height: 0;
  content: '';
  transform: scaleY(0.5);
  border-bottom: 1px solid #e4e7ed;
}

.b-b:after {
  bottom: 0;
}

.b-t:after {
  top: 0;
}
</style>
