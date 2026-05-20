<template>
  <view class="page">
    <view class="header">
      <text class="header-title">发起证明申请</text>
    </view>

    <view class="section">
      <text class="section-title">类型选择</text>
      <view class="type-list">
        <view
          class="type-item"
          v-for="t in types"
          :key="t.id"
          :class="{ selected: selectedType === t.id }"
          @click="selectType(t.id)"
        >
          <text class="type-name">{{ t.name }}</text>
          <text class="type-desc">{{ t.description }}</text>
        </view>
      </view>
    </view>

    <view class="section">
      <text class="section-title">申请表单</text>
      <view class="form">
        <view class="field">
          <text class="label">申请用途</text>
          <input class="input" v-model="formData.purpose" placeholder="如：考研、实习" />
        </view>

        <view class="field">
          <text class="label">申请份数</text>
          <view class="stepper">
            <button class="step-btn" @click="changeCopies(-1)" :disabled="formData.copies <= 1">-</button>
            <text class="step-value">{{ formData.copies }}</text>
            <button class="step-btn" @click="changeCopies(1)" :disabled="formData.copies >= 99">+</button>
          </view>
        </view>

        <view class="field">
          <text class="label">备注说明</text>
          <textarea class="textarea" v-model="formData.remark" placeholder="如需补充说明，可在此填写（可选）" />
        </view>
      </view>
    </view>

    <view class="footer">
      <button class="btn-submit" :loading="submitting" :disabled="submitting" @click="submitApply">提交申请</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { approvalApi } from '@/api'

const types = ref([])
const selectedType = ref(null)
const submitting = ref(false)
const formData = reactive({ purpose: '', copies: 1, remark: '' })

onMounted(async () => {
  try {
    const res = await approvalApi.getTypes()
    types.value = res.data || []
  } catch (e) {}
  if (!types.value.length) {
    types.value = [
      { id: 1, name: '在读证明', description: '用于证明在校就读状态' },
      { id: 2, name: '成绩证明', description: '用于证明课程成绩与学业情况' },
      { id: 3, name: '离校证明', description: '用于证明毕业或离校相关事项' },
    ]
  }
})

function selectType(id) {
  selectedType.value = id
}

function changeCopies(delta) {
  const next = Number(formData.copies || 1) + delta
  formData.copies = Math.min(99, Math.max(1, next))
}

function confirmSubmit() {
  return new Promise((resolve) => {
    uni.showModal({
      title: '提示',
      content: '确定提交吗？',
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
      content: '提交成功',
      showCancel: false,
      success: () => {
        uni.switchTab({ url: '/pages/approval/index' })
      },
    })
  } finally { submitting.value = false }
}
</script>

<style scoped>
.page { padding: 20rpx; padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); background: #f0f2f5; min-height: 100vh; box-sizing: border-box; }

.header { padding: 10rpx 0 18rpx; }
.header-title { font-size: 34rpx; font-weight: bold; color: #1a3a5c; }

.section { margin-bottom: 20rpx; }
.section-title { font-size: 26rpx; font-weight: bold; display: block; margin: 10rpx 0 12rpx; color: #111; }

.type-list { display: flex; flex-direction: column; gap: 16rpx; }
.type-item { background: #fff; border: 2rpx solid #e9edf2; border-radius: 12rpx; padding: 22rpx 24rpx; }
.type-item.selected { border-color: #1a3a5c; background: #f0f5fa; }
.type-name { font-size: 28rpx; font-weight: bold; display: block; }
.type-desc { font-size: 24rpx; color: #666; margin-top: 8rpx; display: block; }

.form { background: #fff; border-radius: 12rpx; padding: 24rpx; }
.field { margin-bottom: 18rpx; }
.label { font-size: 26rpx; font-weight: bold; display: block; margin-bottom: 10rpx; color: #111; }
.input { border: 1rpx solid #dcdfe6; border-radius: 10rpx; padding: 16rpx; font-size: 28rpx; background: #fff; }
.textarea { border: 1rpx solid #dcdfe6; border-radius: 10rpx; padding: 16rpx; font-size: 28rpx; height: 170rpx; width: 100%; background: #fff; box-sizing: border-box; }

.stepper { display: flex; align-items: center; gap: 18rpx; }
.step-btn { width: 76rpx; height: 64rpx; line-height: 64rpx; text-align: center; background: #f4f6f8; color: #1a3a5c; border: 1rpx solid #e1e6eb; border-radius: 10rpx; padding: 0; font-size: 34rpx; }
.step-value { min-width: 70rpx; text-align: center; font-size: 28rpx; font-weight: bold; color: #111; }

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #e9edf2;
  box-sizing: border-box;
}
.btn-submit { width: 100%; background: #1a3a5c; color: #fff; border: none; border-radius: 10rpx; font-size: 32rpx; padding: 22rpx 0; }
</style>
