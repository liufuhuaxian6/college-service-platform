<template>
  <view class="page mp-page-bg">
    <view class="hero mp-hero">
      <RucSeal :size="250" tone="light" class="mp-hero-seal" />
      <view class="hero-main">
        <text class="eyebrow mp-eyebrow">党团事务</text>
        <text class="title">我的党团流程</text>
        <view class="title-underline" />
        <text class="subtitle">查看个人进度，也可查阅官方流程模板</text>
      </view>
    </view>

    <view class="section-head">
      <text class="section-title">我的流程</text>
      <text class="section-extra">{{ progressList.length }} 项</text>
    </view>

    <view class="panel">
      <view v-if="progressList.length" class="progress-list">
        <view
          class="progress-card"
          v-for="item in progressList"
          :key="item.id"
          @click="openProgress(item)"
        >
          <view class="percent-ring">
            <text class="percent-num">{{ progressPercent(item) }}</text>
            <text class="percent-unit">%</text>
          </view>
          <view class="card-main">
            <view class="card-title-row">
              <text class="card-title">{{ item.templateName || '党团流程' }}</text>
              <StatusPill :status="item.status" />
            </view>
            <text class="card-desc">
              第 {{ item.currentStep || 1 }}/{{ item.steps?.length || '-' }} 步{{ currentStepName(item) ? ' · ' + currentStepName(item) : '' }}
            </text>
            <view class="progress-bar">
              <view class="progress-fill" :style="{ width: progressPercent(item) + '%' }" />
            </view>
          </view>
          <text class="card-arrow">›</text>
        </view>
      </view>
      <EmptyState
        v-else
        title="暂无个人流程"
        description="管理员可在 PC 管理端为学生创建流程实例；此处仍可查看官方模板。"
      />
    </view>

    <view class="section-head">
      <text class="section-title">流程模板</text>
      <text class="section-extra">官方流程</text>
    </view>

    <view class="template-grid">
      <view
        class="template-card"
        v-for="template in templateCards"
        :key="template.id"
        @click="openTemplate(template)"
      >
        <view class="template-body">
          <text class="template-name">{{ template.name }}</text>
          <text class="template-desc">{{ template.description || template.localDescription }}</text>
          <text class="template-count">共 {{ template.totalSteps || template.localSteps }} 个节点</text>
        </view>
        <text class="template-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { partyApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import StatusPill from '@/components/StatusPill.vue'
import RucSeal from '@/components/RucSeal.vue'

const progressList = ref([])
const templates = ref([])

const localTemplateMeta = {
  1: { icon: '党', localDescription: '发展党员工作程序', localSteps: 29 },
  2: { icon: '团', localDescription: '标准入团流程', localSteps: 5 },
}

const templateCards = computed(() => {
  const list = templates.value.length
    ? templates.value
    : [
        { id: 1, name: '入党流程', description: '发展党员工作程序', totalSteps: 29 },
        { id: 2, name: '入团流程', description: '标准入团流程', totalSteps: 5 },
      ]

  return list.map((item) => {
    const meta = localTemplateMeta[item.id] || {}
    return {
      ...item,
      icon: meta.icon || (item.name?.includes('团') ? '团' : '党'),
      localDescription: meta.localDescription || '党团事务流程模板',
      localSteps: meta.localSteps || item.totalSteps || 0,
    }
  })
})

function progressPercent(item) {
  const total = item.steps?.length || 0
  if (!total) return 0
  if (item.status === 'completed') return 100
  const current = Math.min(item.currentStep || 1, total)
  return Math.round((current / total) * 100)
}

function currentStepName(item) {
  const step = (item.steps || []).find((s) => s.stepOrder === item.currentStep)
  return step?.name || ''
}

function openProgress(item) {
  uni.navigateTo({ url: `/pages/party/detail?mode=progress&id=${item.id}` })
}

function openTemplate(template) {
  uni.navigateTo({
    url: `/pages/party/detail?mode=template&id=${template.id}&name=${encodeURIComponent(template.name || '')}`,
  })
}

onMounted(async () => {
  try {
    const [progressRes, templateRes] = await Promise.all([
      partyApi.getMyProgress(),
      partyApi.getTemplates(),
    ])
    progressList.value = progressRes.data || []
    templates.value = templateRes.data || []
  } catch (e) {
    uni.showToast({ title: '流程数据加载失败', icon: 'none' })
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.hero {
  margin-bottom: 28rpx;
  padding: 32rpx;
  color: #fff;
}

.hero-main {
  position: relative;
  z-index: 1;
}

.eyebrow {
  margin-bottom: 18rpx;
}

.title {
  display: block;
  font-family: var(--mp-font-display);
  font-size: 44rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
}

.title-underline {
  width: 64rpx;
  height: 6rpx;
  margin-top: 14rpx;
  border-radius: 4rpx;
  background: linear-gradient(90deg, var(--mp-gold), rgba(184, 146, 62, 0.2));
}

.subtitle {
  display: block;
  margin-top: 12rpx;
  color: rgba(255, 255, 255, .76);
  font-size: 24rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 30rpx 2rpx 16rpx;
}

.section-title {
  position: relative;
  padding-left: 20rpx;
  color: var(--mp-text-main);
  font-size: 31rpx;
  font-weight: 750;
}

/* 红金双色装饰条 */
.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6rpx;
  bottom: 6rpx;
  width: 7rpx;
  border-radius: 4rpx;
  background: linear-gradient(180deg, var(--mp-primary) 0%, var(--mp-primary) 62%, var(--mp-gold) 62%, var(--mp-gold) 100%);
}

.section-extra {
  color: var(--mp-text-sub);
  font-size: 23rpx;
}

.panel,
.template-card {
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: 24rpx;
  box-shadow: 0 10rpx 28rpx rgba(31, 35, 41, .04);
}

.progress-list {
  padding: 10rpx;
}

.progress-card {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 26rpx 24rpx;
  border-radius: 18rpx;
  transition: background 0.15s ease;
}

.progress-card:active {
  background: var(--mp-bg-warm);
}

.progress-card + .progress-card {
  border-top: 1rpx solid var(--mp-border);
}

/* 左侧百分比章 (实心人大红渐变圆) */
.percent-ring {
  width: 110rpx;
  height: 110rpx;
  display: flex;
  align-items: baseline;
  justify-content: center;
  flex-direction: row;
  flex-shrink: 0;
  padding-top: 34rpx;
  border-radius: 50%;
  background: var(--mp-red-gradient);
  box-shadow: 0 10rpx 24rpx rgba(157, 34, 53, 0.28), inset 0 0 0 5rpx rgba(255, 255, 255, 0.16);
  box-sizing: border-box;
}

.percent-num {
  color: #fff;
  font-size: 36rpx;
  font-weight: 800;
  line-height: 1;
}

.percent-unit {
  color: rgba(255, 255, 255, 0.85);
  font-size: 19rpx;
  font-weight: 700;
}

.card-main {
  flex: 1;
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}

.card-title {
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 23rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-arrow {
  flex-shrink: 0;
  color: var(--mp-text-muted);
  font-size: 36rpx;
  line-height: 1;
}

.progress-bar {
  height: 10rpx;
  margin-top: 18rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: #F0F2F5;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--mp-primary), var(--mp-gold));
  transition: width 0.4s ease;
}

.template-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16rpx;
}

.template-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 26rpx 26rpx 32rpx;
}

.template-card::before {
  content: '';
  position: absolute;
  left: 18rpx;
  top: 30rpx;
  bottom: 30rpx;
  width: 6rpx;
  border-radius: 4rpx;
  background: var(--mp-primary);
}

.template-body {
  flex: 1;
  min-width: 0;
}

.template-arrow {
  flex-shrink: 0;
  color: var(--mp-text-muted);
  font-size: 36rpx;
  line-height: 1;
}

.template-name {
  display: block;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 700;
}

.template-desc,
.template-count {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 24rpx;
  line-height: 1.45;
}

.template-count {
  color: var(--mp-primary);
  font-weight: 650;
}
</style>
