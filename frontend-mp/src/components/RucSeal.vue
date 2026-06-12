<template>
  <image class="ruc-seal" :src="src" :style="sealStyle" mode="aspectFit" />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 直径, 单位 rpx */
  size: { type: Number, default: 120 },
  /** light = 深色底用白色校徽; primary = 浅色底用红色校徽 */
  tone: { type: String, default: 'light' },
})

const src = computed(() =>
  props.tone === 'light' ? '/static/ruc-logo-white.svg' : '/static/ruc-logo.svg',
)
const sealStyle = computed(() => ({
  width: `${props.size}rpx`,
  height: `${props.size}rpx`,
}))
</script>

<style scoped>
.ruc-seal {
  display: block;
  flex-shrink: 0;
}
</style>
