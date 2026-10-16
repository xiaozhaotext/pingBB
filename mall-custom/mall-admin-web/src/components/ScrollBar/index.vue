<script lang="ts" setup>
import { ref } from 'vue'
defineOptions({ name: 'ScrollBar' })
const container = ref<HTMLElement | null>(null)
const scrollActiveIntoView = () => {
  const active = container.value?.querySelector<HTMLElement>('.el-menu-item.is-active')
  active?.scrollIntoView({ block: 'nearest' })
}
defineExpose({ scrollActiveIntoView })
</script>
<template><div ref="container" class="scroll-container" tabindex="0" aria-label="后台菜单，可滚动"><div class="scroll-wrapper"><slot /></div></div></template>
<style lang="scss" scoped>
@use '../../styles/variables.scss' as vars;
.scroll-container{position:relative;width:100%;height:100%;overflow-y:scroll;overflow-x:hidden;overscroll-behavior-y:contain;touch-action:pan-y;-webkit-overflow-scrolling:touch;scrollbar-width:auto;scrollbar-color:#a8c4b0 #25463a;scrollbar-gutter:stable;background-color:vars.$menuBg}
.scroll-container::-webkit-scrollbar{width:10px}
.scroll-container::-webkit-scrollbar-track{background:#25463a}
.scroll-container::-webkit-scrollbar-thumb{background:#a8c4b0;border:2px solid #25463a;border-radius:10px}
.scroll-container::-webkit-scrollbar-thumb:hover{background:#d0dfd3}
.scroll-container:focus-visible{outline:2px solid #f5d58c;outline-offset:-2px}
.scroll-wrapper{position:relative;width:100%;min-height:100%}
</style>
