<script lang="ts" setup>
defineOptions({ name: 'ScrollBar' })
</script>
<template><div class="scroll-container"><div class="scroll-wrapper"><slot /></div></div></template>
<style lang="scss" scoped>
@use '../../styles/variables.scss' as vars;
.scroll-container{position:relative;width:100%;height:100%;overflow-y:auto;overflow-x:hidden;overscroll-behavior-y:contain;touch-action:pan-y;-webkit-overflow-scrolling:touch;scrollbar-width:thin;background-color:vars.$menuBg}
.scroll-wrapper{position:relative;width:100%;min-height:100%}
</style>
