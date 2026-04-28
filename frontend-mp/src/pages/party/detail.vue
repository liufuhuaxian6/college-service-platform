<template>
  <view class="page">
    <view class="timeline" v-if="detail">
      <text class="title">{{ detail.templateName }}</text>
      <view class="step" v-for="(step, i) in detail.steps" :key="i">
        <view class="step-indicator" :class="{ done: step.completed, current: !step.completed && i === detail.currentStep - 1 }" />
        <view class="step-content">
          <text class="step-name">{{ step.stepOrder }}. {{ step.name }}</text>
          <text class="step-desc" v-if="step.description">{{ step.description }}</text>
          <text class="step-time" v-if="step.completedAt">完成时间: {{ step.completedAt }}</text>
          <text class="step-time" v-else-if="step.durationDays">预计需要 {{ step.durationDays }} 天</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { partyApi } from '@/api'

const detail = ref(null)

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1]
  const id = page.options?.id
  if (id) {
    const res = await partyApi.getProgressDetail(id)
    detail.value = res.data
  }
})
</script>

<style scoped>
.page { padding: 30rpx; }
.title { font-size: 34rpx; font-weight: bold; margin-bottom: 30rpx; display: block; color: #1a3a5c; }
.step { display: flex; gap: 20rpx; margin-bottom: 30rpx; position: relative; }
.step-indicator { width: 24rpx; height: 24rpx; border-radius: 50%; background: #ddd; margin-top: 6rpx; flex-shrink: 0; }
.step-indicator.done { background: #67c23a; }
.step-indicator.current { background: #409eff; }
.step-content { flex: 1; }
.step-name { font-size: 28rpx; font-weight: bold; display: block; }
.step-desc { font-size: 24rpx; color: #666; margin-top: 8rpx; display: block; }
.step-time { font-size: 22rpx; color: #999; margin-top: 6rpx; display: block; }
</style>
