<template>
  <view class="page">
    <view class="empty" v-if="!list.length && !loading">暂无进行中的流程</view>
    <view class="progress-card" v-for="item in list" :key="item.id" @click="goDetail(item.id)">
      <view class="card-header">
        <text class="card-title">{{ item.templateName }}</text>
        <text class="card-status" :class="item.status">
          {{ { active: '进行中', completed: '已完成', suspended: '已暂停' }[item.status] }}
        </text>
      </view>
      <view class="steps-bar">
        <view
          class="step-dot"
          v-for="(step, i) in item.steps"
          :key="i"
          :class="{ done: step.completed, current: !step.completed && i === item.currentStep - 1 }"
        />
      </view>
      <text class="step-name">当前: {{ getCurrentStepName(item) }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { partyApi } from '@/api'

const loading = ref(false)
const list = ref([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await partyApi.getMyProgress()
    list.value = res.data || []
  } finally { loading.value = false }
})

function getCurrentStepName(item) {
  const step = item.steps?.find((s, i) => i === item.currentStep - 1)
  return step?.name || '-'
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/party/detail?id=${id}` })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.progress-card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; }
.card-title { font-size: 30rpx; font-weight: bold; }
.card-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 4rpx; }
.card-status.active { color: #409eff; background: #ecf5ff; }
.card-status.completed { color: #67c23a; background: #f0f9eb; }
.card-status.suspended { color: #e6a23c; background: #fdf6ec; }
.steps-bar { display: flex; gap: 8rpx; margin-bottom: 12rpx; }
.step-dot { width: 24rpx; height: 24rpx; border-radius: 50%; background: #ddd; flex-shrink: 0; }
.step-dot.done { background: #67c23a; }
.step-dot.current { background: #409eff; }
.step-name { font-size: 24rpx; color: #666; }
.empty { text-align: center; color: #999; padding: 80rpx; }
</style>
