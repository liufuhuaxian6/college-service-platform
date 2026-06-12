<template>
  <span class="ruc-seal" :class="{ 'ruc-seal--disc': disc }" :style="rootStyle">
    <img class="ruc-seal__img" :src="src" :style="imgStyle" alt="中国人民大学校徽" />
  </span>
</template>

<script setup>
import { computed } from 'vue'
import logoColor from '@/assets/ruc-logo.svg'
import logoWhite from '@/assets/ruc-logo-white.svg'

const props = defineProps({
  size: { type: [Number, String], default: 64 },
  // 深色背景上使用白色版校徽
  light: { type: Boolean, default: false },
  // 兼容旧调用: 传入偏白的颜色时自动用白色版
  color: { type: String, default: '' },
  // 圆盘模式: 白色圆底 + 原色校徽 (细节最清晰, 适合小尺寸/任意背景)
  disc: { type: Boolean, default: false },
})

// disc 模式强制用原色校徽 (放在白盘上, 细节完整)
const isLight = computed(
  () => !props.disc && (props.light || /255|#fff|#ffffff|white/i.test(props.color)),
)
const src = computed(() => (isLight.value ? logoWhite : logoColor))
const sizePx = computed(() =>
  typeof props.size === 'number' ? `${props.size}px` : props.size,
)
const rootStyle = computed(() => ({ width: sizePx.value, height: sizePx.value }))
const imgStyle = computed(() =>
  props.disc ? { width: '78%', height: '78%' } : { width: '100%', height: '100%' },
)
</script>

<style scoped>
.ruc-seal {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ruc-seal__img {
  object-fit: contain;
}

.ruc-seal--disc {
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.18);
}
</style>
