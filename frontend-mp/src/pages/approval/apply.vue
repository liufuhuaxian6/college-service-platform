<template>
  <view class="page mp-page-bg">
    <!-- ===== 顶部: 步骤指示 ===== -->
    <view class="hero mp-hero">
      <RucSeal :size="200" tone="light" class="mp-hero-seal" />
      <view class="hero-main">
        <text class="hero-title">发起证明申请</text>
        <view class="stepper">
          <view
            v-for="(s, i) in stepLabels"
            :key="s"
            class="stepper-item"
            :class="{ active: step === i + 1, done: step > i + 1 }"
          >
            <view class="stepper-dot">
              <text>{{ step > i + 1 ? '✓' : i + 1 }}</text>
            </view>
            <text class="stepper-label">{{ s }}</text>
            <view v-if="i < stepLabels.length - 1" class="stepper-line" :class="{ done: step > i + 1 }" />
          </view>
        </view>
      </view>
    </view>

    <!-- ===== 第 1 步: 选择模板 ===== -->
    <view v-if="step === 1">
      <view class="section-head">
        <text class="section-title">选择证明模板</text>
        <text class="section-desc">模板正文已由系统固化，提交时只需补充缺失字段</text>
      </view>

      <view class="type-list">
        <view
          v-for="t in templates"
          :key="t.id"
          class="type-item"
          :class="{ selected: selectedTemplate === t.id }"
          @click="selectTemplate(t.id)"
        >
          <view class="type-icon">
            <image class="type-icon__img" src="/static/icons/icon-cert.svg" mode="aspectFit" />
          </view>
          <view class="type-content">
            <text class="type-name">{{ t.title }}</text>
            <text class="type-desc">{{ t.description || t.category || '办公证明模板' }}</text>
          </view>
          <view class="type-check">
            <text v-if="selectedTemplate === t.id">✓</text>
          </view>
        </view>

        <view v-if="!templates.length" class="empty-hint">
          <text>暂无可用模板，请联系管理员上传办公模板。</text>
        </view>
      </view>
    </view>

    <!-- ===== 第 2 步: 填写信息 ===== -->
    <view v-else-if="step === 2">
      <view class="section-head">
        <text class="section-title">填写申请信息</text>
        <text class="section-desc">姓名、学号等基础信息已从个人档案自动带入</text>
      </view>

      <view class="form mp-card">
        <view class="form-summary">
          <text class="summary-label">已选模板</text>
          <text class="summary-value">{{ selectedTemplateName || '未选择' }}</text>
        </view>

        <view v-if="profileItems.length" class="profile-panel">
          <view v-for="item in profileItems" :key="item.label" class="profile-item">
            <text class="profile-label">{{ item.label }}</text>
            <text class="profile-value">{{ item.value }}</text>
          </view>
        </view>

        <view v-if="loadingFields" class="loading-fields">
          <text>正在读取模板字段...</text>
        </view>

        <view v-if="!loadingFields && !fields.length" class="loading-fields">
          <text>该模板无需补充字段，可直接进入下一步确认。</text>
        </view>

        <view v-for="field in fields" :key="field.key" class="field">
          <view class="label-row">
            <text class="label">{{ field.label }}</text>
            <text v-if="field.required" class="required">必填</text>
          </view>

          <picker
            v-if="field.type === 'select'"
            :range="field.options || []"
            @change="onSelectField(field, $event)"
          >
            <view class="input-wrap picker-wrap">
              <text :class="formData[field.key] ? 'input-text' : 'placeholder'">
                {{ formData[field.key] || field.placeholder || '请选择' }}
              </text>
              <text class="picker-arrow">›</text>
            </view>
          </picker>

          <picker
            v-else-if="field.type === 'date'"
            mode="date"
            @change="onDateField(field, $event)"
          >
            <view class="input-wrap picker-wrap">
              <text :class="formData[field.key] ? 'input-text' : 'placeholder'">
                {{ formData[field.key] || field.placeholder || '请选择日期' }}
              </text>
              <text class="picker-arrow">›</text>
            </view>
          </picker>

          <view v-else class="input-wrap">
            <input
              v-model="formData[field.key]"
              class="input"
              :type="field.type === 'number' ? 'number' : 'text'"
              :placeholder="field.placeholder || '请输入'"
            />
          </view>
        </view>
      </view>
    </view>

    <!-- ===== 第 3 步: 确认提交 ===== -->
    <view v-else>
      <view class="section-head">
        <text class="section-title">确认申请内容</text>
        <text class="section-desc">提交后进入审批流转，通过后自动生成证明 PDF</text>
      </view>

      <view class="confirm mp-card">
        <view class="confirm-row confirm-row--head">
          <view class="confirm-icon">
            <image class="confirm-icon__img" src="/static/icons/icon-cert.svg" mode="aspectFit" />
          </view>
          <view class="confirm-head-text">
            <text class="confirm-template">{{ selectedTemplateName }}</text>
            <text class="confirm-sub">证明申请 · 待提交</text>
          </view>
        </view>

        <view class="confirm-group" v-if="profileItems.length">
          <text class="confirm-group-title">档案信息（自动带入）</text>
          <view v-for="item in profileItems" :key="item.label" class="confirm-row">
            <text class="confirm-label">{{ item.label }}</text>
            <text class="confirm-value">{{ item.value }}</text>
          </view>
        </view>

        <view class="confirm-group" v-if="filledItems.length">
          <text class="confirm-group-title">补充信息</text>
          <view v-for="item in filledItems" :key="item.label" class="confirm-row">
            <text class="confirm-label">{{ item.label }}</text>
            <text class="confirm-value">{{ item.value }}</text>
          </view>
        </view>

        <view class="confirm-tip">
          <text>审批通过后可在「我的申请」中预览与下载；下载后申请将锁定归档。</text>
        </view>
      </view>
    </view>

    <!-- ===== 底部操作 ===== -->
    <view class="footer">
      <view class="footer-buttons">
        <button v-if="step > 1" class="btn-prev" @click="prevStep">上一步</button>
        <button
          v-if="step < 3"
          class="btn-next"
          :class="{ muted: !canNext }"
          @click="nextStep"
        >
          下一步
        </button>
        <button
          v-else
          class="btn-next"
          :loading="submitting"
          :disabled="submitting"
          @click="submitApply"
        >
          确认提交
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { approvalApi } from '@/api'
import RucSeal from '@/components/RucSeal.vue'

const stepLabels = ['选择模板', '填写信息', '确认提交']
const step = ref(1)

const templates = ref([])
const fields = ref([])
const profileValues = ref({})
const selectedTemplate = ref(null)
const loadingFields = ref(false)
const submitting = ref(false)
const formData = reactive({})

const selectedTemplateName = computed(
  () => templates.value.find((item) => item.id === selectedTemplate.value)?.title || ''
)
const profileItems = computed(() =>
  Object.entries(profileValues.value || {}).map(([label, value]) => ({ label, value }))
)
const filledItems = computed(() =>
  fields.value
    .map((f) => ({ label: f.label, value: String(formData[f.key] || '').trim() }))
    .filter((item) => item.value)
)

const canNext = computed(() => {
  if (step.value === 1) return !!selectedTemplate.value && !loadingFields.value
  if (step.value === 2) {
    return fields.value.every((field) => !field.required || String(formData[field.key] || '').trim())
  }
  return true
})

onMounted(loadTemplates)

async function loadTemplates() {
  try {
    const res = await approvalApi.getTemplates()
    templates.value = res.data || []
  } catch (e) {
    templates.value = []
  }
}

async function selectTemplate(id) {
  if (selectedTemplate.value === id) return
  selectedTemplate.value = id
  fields.value = []
  profileValues.value = {}
  Object.keys(formData).forEach((key) => delete formData[key])
  loadingFields.value = true
  try {
    const res = await approvalApi.getTemplateFields(id)
    fields.value = res.data?.inputs || []
    profileValues.value = res.data?.profileValues || {}
  } finally {
    loadingFields.value = false
  }
}

function onSelectField(field, event) {
  const index = Number(event.detail.value)
  formData[field.key] = field.options?.[index] || ''
}

function onDateField(field, event) {
  formData[field.key] = event.detail.value || ''
}

function prevStep() {
  if (step.value > 1) step.value -= 1
}

function nextStep() {
  if (step.value === 1) {
    if (!selectedTemplate.value) {
      uni.showToast({ title: '请选择模板', icon: 'none' })
      return
    }
    if (loadingFields.value) {
      uni.showToast({ title: '正在读取模板字段', icon: 'none' })
      return
    }
    step.value = 2
    return
  }
  if (step.value === 2) {
    const missing = fields.value.find(
      (field) => field.required && !String(formData[field.key] || '').trim()
    )
    if (missing) {
      uni.showToast({ title: `请填写${missing.label}`, icon: 'none' })
      return
    }
    step.value = 3
  }
}

async function submitApply() {
  if (!selectedTemplate.value) {
    uni.showToast({ title: '请选择模板', icon: 'none' })
    step.value = 1
    return
  }
  submitting.value = true
  try {
    await approvalApi.apply({
      templateDocId: selectedTemplate.value,
      formData: { ...formData },
    })
    uni.showModal({
      title: '提交成功',
      content: '申请已提交，请关注审批进度。',
      showCancel: false,
      success: () => {
        uni.switchTab({ url: '/pages/approval/index' })
      },
    })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 24rpx calc(156rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* ===== 顶部 ===== */
.hero {
  padding: 30rpx 30rpx 34rpx;
  margin-bottom: 28rpx;
}

.hero-main {
  position: relative;
  z-index: 1;
}

.hero-title {
  display: block;
  color: #fff;
  font-family: var(--mp-font-display);
  font-size: 42rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
}

/* ===== 步骤指示器 ===== */
.stepper {
  display: flex;
  align-items: flex-start;
  margin-top: 28rpx;
}

.stepper-item {
  flex: 1;
  display: flex;
  align-items: center;
  position: relative;
}

.stepper-dot {
  width: 46rpx;
  height: 46rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  border: 1rpx solid rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.8);
  font-size: 22rpx;
  font-weight: 800;
}

.stepper-item.active .stepper-dot {
  background: #fff;
  color: var(--mp-primary);
  border-color: #fff;
}

.stepper-item.done .stepper-dot {
  background: var(--mp-gold);
  border-color: var(--mp-gold);
  color: #fff;
}

.stepper-label {
  margin-left: 12rpx;
  color: rgba(255, 255, 255, 0.65);
  font-size: 22rpx;
  white-space: nowrap;
}

.stepper-item.active .stepper-label {
  color: #fff;
  font-weight: 700;
}

.stepper-line {
  flex: 1;
  height: 2rpx;
  margin: 0 14rpx;
  background: rgba(255, 255, 255, 0.25);
}

.stepper-line.done {
  background: var(--mp-gold);
}

/* ===== 区块标题 ===== */
.section-head {
  margin: 24rpx 0 16rpx;
}

.section-title {
  position: relative;
  display: block;
  padding-left: 20rpx;
  color: var(--mp-text-main);
  font-size: 31rpx;
  font-weight: 800;
}

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

.section-desc {
  display: block;
  margin-top: 8rpx;
  padding-left: 20rpx;
  color: var(--mp-text-sub);
  font-size: 23rpx;
}

/* ===== 第 1 步: 模板卡片 ===== */
.type-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 112rpx;
  padding: 22rpx;
  background: #FFFFFF;
  border: 2rpx solid rgba(35, 31, 32, 0.08);
  border-radius: 22rpx;
  box-shadow: var(--mp-shadow-card);
  box-sizing: border-box;
  transition: transform 0.15s ease;
}

.type-item:active {
  transform: scale(0.985);
}

.type-item.selected {
  border-color: var(--mp-primary);
  background: #FFF8F8;
  box-shadow: 0 14rpx 32rpx rgba(157, 34, 53, 0.1);
}

.type-icon {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 18rpx;
  background: var(--mp-primary-light);
}

.type-icon__img {
  width: 40rpx;
  height: 40rpx;
}

.type-content {
  flex: 1;
  min-width: 0;
}

.type-name {
  display: block;
  color: var(--mp-text-main);
  font-size: 29rpx;
  font-weight: 800;
}

.type-desc {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-regular);
  font-size: 24rpx;
  line-height: 1.35;
}

.type-check {
  width: 42rpx;
  height: 42rpx;
  line-height: 42rpx;
  text-align: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--mp-primary);
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 800;
  opacity: 0;
}

.type-item.selected .type-check {
  opacity: 1;
}

.empty-hint {
  padding: 32rpx;
  text-align: center;
  color: var(--mp-text-sub);
  background: #FFFFFF;
  border-radius: 22rpx;
  font-size: 24rpx;
}

/* ===== 第 2 步: 表单 ===== */
.form {
  padding: 24rpx;
}

.form-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: var(--mp-bg-warm);
}

.summary-label,
.profile-label {
  color: var(--mp-text-sub);
  font-size: 23rpx;
}

.summary-value {
  color: var(--mp-primary);
  font-size: 25rpx;
  font-weight: 800;
}

.profile-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.profile-item {
  padding: 16rpx;
  border-radius: 16rpx;
  background: var(--mp-bg-warm);
}

.profile-label,
.profile-value {
  display: block;
}

.profile-value {
  margin-top: 6rpx;
  color: var(--mp-text-main);
  font-size: 24rpx;
  font-weight: 700;
}

.loading-fields {
  padding: 28rpx 0;
  text-align: center;
  color: var(--mp-text-sub);
  font-size: 24rpx;
}

.field {
  margin-bottom: 24rpx;
}

.field:last-child {
  margin-bottom: 0;
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.label {
  color: var(--mp-text-main);
  font-size: 26rpx;
  font-weight: 750;
}

.required {
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  color: var(--mp-primary);
  background: var(--mp-primary-light);
  font-size: 20rpx;
}

.input-wrap {
  min-height: 84rpx;
  padding: 0 22rpx;
  display: flex;
  align-items: center;
  border: 1rpx solid rgba(35, 31, 32, 0.08);
  border-radius: 18rpx;
  background: #FAFAFB;
  box-sizing: border-box;
}

.input {
  width: 100%;
  height: 82rpx;
  font-size: 27rpx;
  color: var(--mp-text-main);
}

.picker-wrap {
  justify-content: space-between;
}

.input-text {
  color: var(--mp-text-main);
  font-size: 27rpx;
}

.placeholder {
  color: #A8ABB2;
  font-size: 27rpx;
}

.picker-arrow {
  color: #C0C4CC;
  font-size: 40rpx;
}

/* ===== 第 3 步: 确认卡 ===== */
.confirm {
  padding: 26rpx;
}

.confirm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 12rpx 0;
}

.confirm-row--head {
  justify-content: flex-start;
  padding: 0 0 20rpx;
  border-bottom: 1rpx solid var(--mp-border);
}

.confirm-icon {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 20rpx;
  background: var(--mp-primary-light);
}

.confirm-icon__img {
  width: 44rpx;
  height: 44rpx;
}

.confirm-head-text {
  min-width: 0;
}

.confirm-template {
  display: block;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 800;
}

.confirm-sub {
  display: block;
  margin-top: 6rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.confirm-group {
  margin-top: 20rpx;
}

.confirm-group-title {
  display: block;
  margin-bottom: 6rpx;
  color: var(--mp-gold);
  font-size: 22rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}

.confirm-label {
  flex-shrink: 0;
  color: var(--mp-text-sub);
  font-size: 24rpx;
}

.confirm-value {
  min-width: 0;
  color: var(--mp-text-main);
  font-size: 25rpx;
  font-weight: 600;
  text-align: right;
}

.confirm-tip {
  margin-top: 22rpx;
  padding: 18rpx 20rpx;
  border-radius: 16rpx;
  background: var(--mp-warning-bg);
  color: #765112;
  font-size: 22rpx;
  line-height: 1.5;
}

/* ===== 底部按钮 ===== */
.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid rgba(35, 31, 32, 0.08);
  box-shadow: 0 -10rpx 28rpx rgba(35, 31, 32, 0.06);
  box-sizing: border-box;
}

.footer-buttons {
  display: flex;
  gap: 16rpx;
}

.btn-prev {
  flex: 0 0 200rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 22rpx;
  background: #fff;
  color: var(--mp-text-regular);
  border: 1rpx solid rgba(35, 31, 32, 0.12);
  font-size: 28rpx;
  font-weight: 700;
}

.btn-next {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 22rpx;
  background: var(--mp-primary);
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 800;
  box-shadow: 0 12rpx 26rpx rgba(157, 34, 53, 0.22);
}

.btn-next.muted {
  background: #B8A3A6;
  box-shadow: none;
}
</style>
