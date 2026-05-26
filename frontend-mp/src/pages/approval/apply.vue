<template>
  <view class="page">
    <view class="hero">
      <view class="hero-badge">证明申请</view>
      <text class="hero-title">发起证明申请</text>
      <text class="hero-desc">选择证明模板并补充必要信息，审批通过后系统会自动生成对应证明 PDF。</text>
    </view>

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
        <view class="type-icon">{{ getTemplateInitial(t.title) }}</view>
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

    <view class="section-head">
      <text class="section-title">申请表单</text>
      <text class="section-desc">姓名、学号等基础信息会从个人档案自动带入</text>
    </view>

    <view class="form">
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

    <view class="footer">
      <button
        class="btn-submit"
        :class="{ muted: !canSubmit }"
        :loading="submitting"
        :disabled="submitting"
        @click="submitApply"
      >
        提交申请
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { approvalApi } from '@/api'

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
const canSubmit = computed(() =>
  !!selectedTemplate.value &&
  fields.value.every((field) => !field.required || String(formData[field.key] || '').trim())
)

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

function getTemplateInitial(title) {
  const text = String(title || '证').replace('证明模板', '').replace('证明', '')
  return text.slice(0, 1) || '证'
}

function onSelectField(field, event) {
  const index = Number(event.detail.value)
  formData[field.key] = field.options?.[index] || ''
}

function onDateField(field, event) {
  formData[field.key] = event.detail.value || ''
}

function confirmSubmit() {
  return new Promise((resolve) => {
    uni.showModal({
      title: '确认提交',
      content: '确定提交该证明申请吗？审批通过后将自动生成 PDF。',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    })
  })
}

async function submitApply() {
  if (!selectedTemplate.value) {
    uni.showToast({ title: '请选择模板', icon: 'none' })
    return
  }
  const missing = fields.value.find((field) => field.required && !String(formData[field.key] || '').trim())
  if (missing) {
    uni.showToast({ title: `请填写${missing.label}`, icon: 'none' })
    return
  }
  const ok = await confirmSubmit()
  if (!ok) return

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
  background: linear-gradient(180deg, #FBF7F5 0%, #F6F4F2 340rpx, #F6F4F2 100%);
  box-sizing: border-box;
}

.hero {
  padding: 28rpx;
  margin-bottom: 28rpx;
  border-radius: 26rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(155, 44, 54, 0.1);
  box-shadow: 0 14rpx 36rpx rgba(31, 35, 41, 0.06);
}

.hero-badge {
  display: inline-flex;
  padding: 8rpx 16rpx;
  margin-bottom: 18rpx;
  border-radius: 999rpx;
  background: #F7EDEF;
  color: #9B2C36;
  font-size: 22rpx;
  font-weight: 700;
}

.hero-title {
  display: block;
  color: #1F2329;
  font-size: 38rpx;
  font-weight: 800;
}

.hero-desc {
  display: block;
  margin-top: 10rpx;
  color: #5B6472;
  font-size: 24rpx;
  line-height: 1.55;
}

.section-head {
  margin: 24rpx 0 16rpx;
}

.section-title {
  display: block;
  color: #1F2329;
  font-size: 31rpx;
  font-weight: 800;
}

.section-desc {
  display: block;
  margin-top: 6rpx;
  color: #86909C;
  font-size: 23rpx;
}

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
  border: 2rpx solid rgba(31, 35, 41, 0.08);
  border-radius: 22rpx;
  box-shadow: 0 10rpx 26rpx rgba(31, 35, 41, 0.04);
  box-sizing: border-box;
}

.type-item.selected {
  border-color: #9B2C36;
  background: #FFF8F8;
  box-shadow: 0 14rpx 32rpx rgba(155, 44, 54, 0.1);
}

.type-icon {
  width: 64rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  flex-shrink: 0;
  border-radius: 18rpx;
  background: #F7EDEF;
  color: #9B2C36;
  font-size: 25rpx;
  font-weight: 800;
}

.type-content {
  flex: 1;
  min-width: 0;
}

.type-name {
  display: block;
  color: #1F2329;
  font-size: 29rpx;
  font-weight: 800;
}

.type-desc {
  display: block;
  margin-top: 8rpx;
  color: #5B6472;
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
  background: #9B2C36;
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
  color: #86909C;
  background: #FFFFFF;
  border-radius: 22rpx;
  font-size: 24rpx;
}

.form {
  padding: 24rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(31, 35, 41, 0.08);
  border-radius: 24rpx;
  box-shadow: 0 14rpx 36rpx rgba(31, 35, 41, 0.06);
}

.form-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: #F7F8FA;
}

.summary-label,
.profile-label {
  color: #86909C;
  font-size: 23rpx;
}

.summary-value {
  color: #9B2C36;
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
  background: #FAFAFB;
}

.profile-label,
.profile-value {
  display: block;
}

.profile-value {
  margin-top: 6rpx;
  color: #1F2329;
  font-size: 24rpx;
  font-weight: 700;
}

.loading-fields {
  padding: 28rpx 0;
  text-align: center;
  color: #86909C;
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
  color: #1F2329;
  font-size: 26rpx;
  font-weight: 750;
}

.required {
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  color: #9B2C36;
  background: #F7EDEF;
  font-size: 20rpx;
}

.input-wrap {
  min-height: 84rpx;
  padding: 0 22rpx;
  display: flex;
  align-items: center;
  border: 1rpx solid rgba(31, 35, 41, 0.08);
  border-radius: 18rpx;
  background: #FAFAFB;
  box-sizing: border-box;
}

.input {
  width: 100%;
  height: 82rpx;
  font-size: 27rpx;
  color: #1F2329;
}

.picker-wrap {
  justify-content: space-between;
}

.input-text {
  color: #1F2329;
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

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid rgba(31, 35, 41, 0.08);
  box-shadow: 0 -10rpx 28rpx rgba(31, 35, 41, 0.06);
  box-sizing: border-box;
}

.btn-submit {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 22rpx;
  background: #9B2C36;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 800;
  box-shadow: 0 12rpx 26rpx rgba(155, 44, 54, 0.22);
}

.btn-submit.muted {
  background: #B8A3A6;
  box-shadow: none;
}
</style>
