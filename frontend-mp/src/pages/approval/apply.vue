<template>
  <view class="page">
    <view class="hero">
      <view class="hero-badge">证明申请</view>
      <text class="hero-title">发起证明申请</text>
      <text class="hero-desc">确认申请用途、份数和备注后提交，申请将进入审批流程。</text>
    </view>

    <view class="section-head">
      <text class="section-title">类型选择</text>
      <text class="section-desc">请选择本次需要开具的证明类型</text>
    </view>

    <view class="type-list">
      <view
        v-for="t in types"
        :key="t.id"
        class="type-item"
        :class="{ selected: selectedType === t.id }"
        @click="selectType(t.id)"
      >
        <view class="type-icon">{{ getTypeInitial(t.name) }}</view>
        <view class="type-content">
          <text class="type-name">{{ t.name }}</text>
          <text class="type-desc">{{ t.description }}</text>
        </view>
        <view class="type-check">
          <text v-if="selectedType === t.id">✓</text>
        </view>
      </view>
    </view>

    <view class="section-head">
      <text class="section-title">申请表单</text>
      <text class="section-desc">用途和份数会进入审批记录</text>
    </view>

    <view class="form">
      <view class="form-summary">
        <text class="summary-label">当前类型</text>
        <text class="summary-value">{{ selectedTypeName || '未选择' }}</text>
      </view>

      <view class="field">
        <text class="label">申请用途</text>
        <view class="input-wrap">
          <input v-model="formData.purpose" class="input" placeholder="如：考研、实习、就业材料" />
        </view>
      </view>

      <view class="field">
        <text class="label">申请份数</text>
        <view class="stepper">
          <button
            class="step-btn"
            :class="{ disabled: formData.copies <= 1 }"
            :disabled="formData.copies <= 1"
            @click="changeCopies(-1)"
          >
            -
          </button>
          <view class="step-value-wrap">
            <text class="step-value">{{ formData.copies }}</text>
            <text class="step-unit">份</text>
          </view>
          <button
            class="step-btn"
            :class="{ disabled: formData.copies >= 99 }"
            :disabled="formData.copies >= 99"
            @click="changeCopies(1)"
          >
            +
          </button>
        </view>
      </view>

      <view class="field">
        <text class="label">备注说明</text>
        <view class="input-wrap textarea-wrap">
          <textarea
            v-model="formData.remark"
            class="textarea"
            placeholder="如需补充说明，可在此填写（可选）"
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

const types = ref([])
const selectedType = ref(null)
const submitting = ref(false)
const formData = reactive({ purpose: '', copies: 1, remark: '' })

const selectedTypeName = computed(() => types.value.find((item) => item.id === selectedType.value)?.name || '')
const canSubmit = computed(() => !!selectedType.value && !!formData.purpose.trim())

onMounted(loadTypes)

async function loadTypes() {
  try {
    const res = await approvalApi.getTypes()
    types.value = res.data || []
  } catch (e) {
    types.value = []
  }
  if (!types.value.length) {
    types.value = [
      { id: 1, name: '在读证明', description: '用于证明当前在校就读状态' },
      { id: 2, name: '成绩证明', description: '用于证明课程成绩与学业情况' },
      { id: 3, name: '政审证明', description: '开具政审相关证明，需院领导审批' },
      { id: 4, name: '离校证明', description: '用于证明毕业或离校相关事项' },
    ]
  }
}

function selectType(id) {
  selectedType.value = id
}

function getTypeInitial(name) {
  return String(name || '证').slice(0, 1)
}

function changeCopies(delta) {
  const next = Number(formData.copies || 1) + delta
  formData.copies = Math.min(99, Math.max(1, next))
}

function confirmSubmit() {
  return new Promise((resolve) => {
    uni.showModal({
      title: '确认提交',
      content: '确定提交该证明申请吗？',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false),
    })
  })
}

async function submitApply() {
  if (!selectedType.value) {
    uni.showToast({ title: '请选择类型', icon: 'none' })
    return
  }
  if (!formData.purpose.trim()) {
    uni.showToast({ title: '请填写申请用途', icon: 'none' })
    return
  }
  const ok = await confirmSubmit()
  if (!ok) return

  submitting.value = true
  try {
    await approvalApi.apply({
      typeId: selectedType.value,
      formData: {
        purpose: formData.purpose,
        copies: Number(formData.copies || 1),
        remark: formData.remark,
      },
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
  background:
    linear-gradient(180deg, #FBF7F5 0%, #F6F4F2 340rpx, #F6F4F2 100%);
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
  margin-bottom: 24rpx;
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: #F7F8FA;
}

.summary-label {
  color: #86909C;
  font-size: 23rpx;
}

.summary-value {
  color: #9B2C36;
  font-size: 25rpx;
  font-weight: 800;
}

.field {
  margin-bottom: 24rpx;
}

.field:last-child {
  margin-bottom: 0;
}

.label {
  display: block;
  margin-bottom: 12rpx;
  color: #1F2329;
  font-size: 26rpx;
  font-weight: 750;
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

.textarea-wrap {
  min-height: 178rpx;
  align-items: flex-start;
  padding-top: 20rpx;
  padding-bottom: 20rpx;
}

.textarea {
  width: 100%;
  height: 138rpx;
  font-size: 27rpx;
  color: #1F2329;
  line-height: 1.5;
}

.stepper {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10rpx;
  border-radius: 18rpx;
  background: #FAFAFB;
  border: 1rpx solid rgba(31, 35, 41, 0.08);
}

.step-btn {
  width: 76rpx;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0;
  border-radius: 18rpx;
  background: #FFFFFF;
  color: #9B2C36;
  border: 1rpx solid rgba(155, 44, 54, 0.14);
  font-size: 34rpx;
  font-weight: 800;
}

.step-btn.disabled {
  color: #C9CDD4;
  background: #F0F1F3;
  border-color: #F0F1F3;
}

.step-value-wrap {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.step-value {
  color: #1F2329;
  font-size: 34rpx;
  font-weight: 800;
}

.step-unit {
  color: #86909C;
  font-size: 22rpx;
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
