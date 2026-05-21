<template>
  <view class="page">
    <view class="top-card">
      <view class="top-main">
        <text class="flow-name">{{ flowName }}</text>
        <text class="flow-subtitle">党团流程进度</text>
      </view>
      <text class="flow-status" :class="flowStatus">{{ statusLabel[flowStatus] }}</text>
    </view>

    <view class="timeline">
      <view class="tl-item" v-for="(s, idx) in steps" :key="s.id" @click="showStepTip(s)">
        <view class="tl-rail">
          <view class="tl-dot" :class="s.status" />
          <view class="tl-line" v-if="idx !== steps.length - 1" :class="s.status" />
        </view>
        <view class="tl-content">
          <view class="tl-header">
            <text class="tl-name">{{ s.name }}</text>
            <text class="tl-time" v-if="s.completedAt">{{ s.completedAt }}</text>
          </view>
          <text class="tl-desc">{{ s.desc }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const flowName = ref('入党流程')
const flowStatus = ref('active')
const statusLabel = { finished: '已完成', active: '进行中', todo: '未开始' }

const steps = ref([
  {
    id: 1,
    name: '提交入党申请书',
    desc: '向党支部递交申请书，登记备案。',
    status: 'finished',
    completedAt: '2026-03-02',
    requirement: '准备申请书与个人基本情况材料。',
  },
  {
    id: 2,
    name: '参加入党积极分子培训',
    desc: '完成规定课程学习与考核。',
    status: 'finished',
    completedAt: '2026-04-12',
    requirement: '按时签到学习，完成结业考试。',
  },
  {
    id: 3,
    name: '确定为发展对象',
    desc: '支部讨论与公示，进入政治审查。',
    status: 'active',
    completedAt: '',
    requirement: '准备思想汇报与个人自查材料。',
  },
  {
    id: 4,
    name: '接收为预备党员',
    desc: '完成政审与谈话，召开支部大会表决。',
    status: 'todo',
    completedAt: '',
    requirement: '等待通知，按要求准备证明材料。',
  },
])

function showStepTip(step) {
  uni.showToast({
    title: step.requirement || step.desc || step.name,
    icon: 'none',
  })
}
</script>

<style scoped>
.page { padding: 20rpx; background: #f0f2f5; min-height: 100vh; box-sizing: border-box; }

.top-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-left: 10rpx solid #1a3a5c;
}
.flow-name { display: block; font-size: 32rpx; font-weight: bold; color: #1a3a5c; }
.flow-subtitle { display: block; margin-top: 6rpx; font-size: 24rpx; color: #666; }
.flow-status { font-size: 24rpx; padding: 6rpx 18rpx; border-radius: 999rpx; }
.flow-status.active { color: #409eff; background: #ecf5ff; }
.flow-status.finished { color: #67c23a; background: #f0f9eb; }
.flow-status.todo { color: #909399; background: #f4f4f5; }

.timeline { background: #fff; border-radius: 12rpx; padding: 24rpx 20rpx; }
.tl-item { display: flex; gap: 18rpx; padding: 8rpx 0 24rpx; }
.tl-rail { width: 40rpx; display: flex; flex-direction: column; align-items: center; flex-shrink: 0; }
.tl-dot { width: 22rpx; height: 22rpx; border-radius: 50%; background: #c0c4cc; margin-top: 6rpx; }
.tl-dot.finished { background: #67c23a; }
.tl-dot.active { background: #409eff; }
.tl-dot.todo { background: #c0c4cc; }
.tl-line { width: 4rpx; flex: 1; background: #e5e7eb; margin-top: 8rpx; border-radius: 2rpx; }
.tl-line.finished { background: #67c23a; opacity: 0.45; }
.tl-line.active { background: #409eff; opacity: 0.45; }
.tl-line.todo { background: #c0c4cc; opacity: 0.45; }

.tl-content { flex: 1; }
.tl-header { display: flex; justify-content: space-between; align-items: baseline; gap: 16rpx; }
.tl-name { font-size: 30rpx; font-weight: bold; color: #111; }
.tl-time { font-size: 22rpx; color: #999; flex-shrink: 0; }
.tl-desc { display: block; margin-top: 10rpx; font-size: 24rpx; color: #666; line-height: 1.6; }
</style>
