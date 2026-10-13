<template>
  <view v-if="enabled && text" class="watermark" :class="`pos-${position}`" :style="style">
    <text v-for="index in repeat" :key="index" class="wm-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 商品图片水印。参数来自后台「平台设置 - 水印设置」，默认关闭。
 * 数据库没有内置图片水印处理，这里按后台配置在展示层叠加，不修改原图。
 */
const POSITIONS = ['topLeft', 'topRight', 'bottomLeft', 'bottomRight', 'tile']

const props = withDefaults(
  defineProps<{
    enabled?: boolean
    text?: string
    position?: string
    opacity?: string | number
    size?: string | number
  }>(),
  { enabled: false, text: '', position: 'bottomRight', opacity: '0.35', size: 18 },
)

const position = computed(() => (POSITIONS.includes(props.position) ? props.position : 'bottomRight'))
const repeat = computed(() => (position.value === 'tile' ? 9 : 1))
const style = computed(() => ({
  opacity: String(props.opacity ?? '0.35'),
  fontSize: `${Number(props.size) || 18}px`,
}))
</script>

<style lang="scss" scoped>
.watermark {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  pointer-events: none;
  padding: 16rpx;
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 0 6rpx rgba(0, 0, 0, 0.65);
  line-height: 1.2;
}
.pos-topLeft { align-items: flex-start; justify-content: flex-start; }
.pos-topRight { align-items: flex-start; justify-content: flex-end; }
.pos-bottomLeft { align-items: flex-end; justify-content: flex-start; }
.pos-bottomRight { align-items: flex-end; justify-content: flex-end; }
.pos-tile { flex-wrap: wrap; align-content: space-around; justify-content: space-around; }
.wm-text { white-space: nowrap; }
</style>
