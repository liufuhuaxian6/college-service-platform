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
      <text class="section-title">流程申请</text>
      <text class="section-action" @click="openApplyForm">{{ showApplyForm ? '收起' : '发起申请' }}</text>
    </view>

    <view class="apply-card">
      <view v-if="showApplyForm" class="apply-form">
        <picker :range="applicationTemplateNames" @change="onTemplatePick">
          <view class="input-wrap picker-wrap">
            <text :class="applicationTemplateId ? 'input-text' : 'placeholder'">
              {{ selectedApplicationTemplateName || '请选择入党 / 入团流程' }}
            </text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
        <view class="textarea-wrap">
          <textarea
            v-model="applicationReason"
            class="textarea"
            maxlength="300"
            placeholder="可填写申请说明、当前准备情况或希望老师关注的事项"
          ></textarea>
        </view>
        <view class="apply-actions">
          <button class="btn-ghost" @click="cancelApply">取消</button>
          <button class="btn-solid" :disabled="submittingApplication" @click="submitApplication">
            {{ submittingApplication ? '提交中' : '提交申请' }}
          </button>
        </view>
      </view>

      <view v-if="applicationList.length" class="application-list">
        <view v-for="app in applicationList" :key="app.id" class="application-item">
          <view class="application-main">
            <view class="application-title-row">
              <text class="application-title">{{ app.templateName || getTemplateName(app.templateId) }}</text>
              <StatusPill :status="app.status" />
            </view>
            <text class="application-meta">申请编号 {{ app.appNo || app.id }} · {{ formatDate(app.createdAt) }}</text>
            <text v-if="app.reason" class="application-reason">{{ app.reason }}</text>
            <text v-if="app.reviewComment" class="application-comment">审核意见：{{ app.reviewComment }}</text>
          </view>
          <button v-if="app.status === 'pending'" class="btn-link" @click="withdrawApplication(app)">撤回</button>
        </view>
      </view>
      <EmptyState
        v-else-if="!showApplyForm"
        title="暂无流程申请"
        description="提交入党或入团流程申请后，可在这里查看审核状态。"
      />
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
const applicationList = ref([])
const showApplyForm = ref(false)
const applicationTemplateId = ref(null)
const applicationReason = ref('')
const submittingApplication = ref(false)

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

const applicationTemplateNames = computed(() => templates.value.map((item) => item.name))

const selectedApplicationTemplateName = computed(() => (
  templates.value.find((item) => item.id === applicationTemplateId.value)?.name || ''
))

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

function getTemplateName(id) {
  return templates.value.find((item) => item.id === id)?.name || '党团流程'
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function openApplyForm() {
  if (showApplyForm.value) {
    showApplyForm.value = false
    return
  }
  if (!templates.value.length) {
    uni.showToast({ title: '暂无可申请的流程模板', icon: 'none' })
    return
  }
  applicationTemplateId.value = applicationTemplateId.value || templates.value[0]?.id || null
  showApplyForm.value = true
}

function cancelApply() {
  showApplyForm.value = false
  applicationReason.value = ''
}

function onTemplatePick(e) {
  const index = Number(e.detail.value || 0)
  applicationTemplateId.value = templates.value[index]?.id || null
}

async function submitApplication() {
  if (!applicationTemplateId.value) {
    uni.showToast({ title: '请选择流程模板', icon: 'none' })
    return
  }
  submittingApplication.value = true
  try {
    await partyApi.apply({
      templateId: Number(applicationTemplateId.value),
      reason: applicationReason.value.trim(),
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    showApplyForm.value = false
    applicationReason.value = ''
    await loadPartyData()
  } finally {
    submittingApplication.value = false
  }
}

function withdrawApplication(app) {
  uni.showModal({
    title: '撤回申请',
    content: `确定撤回「${app.templateName || getTemplateName(app.templateId)}」申请吗？`,
    success: async (res) => {
      if (!res.confirm) return
      await partyApi.withdrawApplication(app.id)
      uni.showToast({ title: '已撤回', icon: 'success' })
      loadPartyData()
    },
  })
}

async function loadPartyData() {
  try {
    const [progressRes, templateRes, applicationRes] = await Promise.all([
      partyApi.getMyProgress().catch(() => ({ data: [] })),
      partyApi.getTemplates().catch(() => ({ data: [] })),
      partyApi.getMyApplications({ page: 1, size: 20 }).catch(() => ({ data: { records: [] } })),
    ])
    progressList.value = progressRes.data || []
    templates.value = templateRes.data || []
    applicationList.value = applicationRes.data?.records || []
  } catch (e) {
    uni.showToast({ title: '流程数据加载失败', icon: 'none' })
  }
}

onMounted(loadPartyData)
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

.section-action {
  color: var(--mp-primary);
  font-size: 24rpx;
  font-weight: 700;
}

.panel,
.apply-card,
.template-card {
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: 24rpx;
  box-shadow: 0 10rpx 28rpx rgba(31, 35, 41, .04);
}

.apply-card {
  padding: 18rpx;
}

.apply-form {
  padding: 6rpx;
}

.input-wrap,
.textarea-wrap {
  min-height: 88rpx;
  padding: 0 24rpx;
  border: 1rpx solid var(--mp-border);
  border-radius: 18rpx;
  background: #fff;
  box-sizing: border-box;
}

.picker-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.input-text,
.placeholder {
  font-size: 27rpx;
}

.input-text {
  color: var(--mp-text-main);
}

.placeholder {
  color: var(--mp-text-muted);
}

.picker-arrow {
  color: var(--mp-text-muted);
  font-size: 34rpx;
}

.textarea-wrap {
  min-height: 170rpx;
  margin-top: 16rpx;
  padding-top: 18rpx;
}

.textarea {
  width: 100%;
  min-height: 130rpx;
  color: var(--mp-text-main);
  font-size: 26rpx;
  line-height: 1.55;
}

.apply-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 18rpx;
}

.btn-ghost,
.btn-solid,
.btn-link {
  height: 72rpx;
  margin: 0;
  border-radius: 18rpx;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 72rpx;
}

.btn-ghost {
  flex: 1;
  color: var(--mp-text-sub);
  background: var(--mp-bg-warm);
  border: 1rpx solid var(--mp-border);
}

.btn-solid {
  flex: 2;
  color: #fff;
  background: var(--mp-red-gradient);
  border: 0;
}

.application-list {
  margin-top: 4rpx;
}

.application-item {
  display: flex;
  gap: 18rpx;
  padding: 22rpx 8rpx;
}

.application-item + .application-item {
  border-top: 1rpx solid var(--mp-border);
}

.application-main {
  flex: 1;
  min-width: 0;
}

.application-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}

.application-title {
  color: var(--mp-text-main);
  font-size: 28rpx;
  font-weight: 720;
}

.application-meta,
.application-reason,
.application-comment {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 23rpx;
  line-height: 1.45;
}

.application-reason {
  color: var(--mp-text-main);
}

.application-comment {
  color: var(--mp-primary);
}

.btn-link {
  align-self: center;
  width: 104rpx;
  height: 58rpx;
  color: var(--mp-primary);
  background: #fff;
  border: 1rpx solid rgba(157, 34, 53, 0.22);
  font-size: 23rpx;
  line-height: 58rpx;
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
