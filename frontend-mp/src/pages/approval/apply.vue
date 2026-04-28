<template>
  <view class="page">
    <text class="title">选择证明类型</text>
    <view class="type-list">
      <view class="type-item" v-for="t in types" :key="t.id" :class="{ selected: selectedType === t.id }" @click="selectedType = t.id">
        <text class="type-name">{{ t.name }}</text>
        <text class="type-desc">{{ t.description }}</text>
      </view>
    </view>

    <view class="form" v-if="selectedType">
      <text class="label">申请用途</text>
      <input class="input" v-model="formData.purpose" placeholder="如: 考研、求职等" />
      <text class="label">份数</text>
      <input class="input" v-model="formData.copies" type="number" placeholder="1" />
      <text class="label">备注</text>
      <textarea class="textarea" v-model="formData.remark" placeholder="其他说明(可选)" />
      <button class="btn-submit" :loading="submitting" @click="handleSubmit">提交申请</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { approvalApi } from '@/api'

const types = ref([])
const selectedType = ref(null)
const submitting = ref(false)
const formData = reactive({ purpose: '', copies: '1', remark: '' })

onMounted(async () => {
  const res = await approvalApi.getTypes()
  types.value = res.data || []
})

async function handleSubmit() {
  if (!selectedType.value) {
    uni.showToast({ title: '请选择类型', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await approvalApi.apply({ typeId: selectedType.value, formData })
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } finally { submitting.value = false }
}
</script>

<style scoped>
.page { padding: 30rpx; }
.title { font-size: 32rpx; font-weight: bold; display: block; margin-bottom: 20rpx; color: #1a3a5c; }
.type-list { margin-bottom: 30rpx; }
.type-item { background: #fff; border: 2rpx solid #eee; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.type-item.selected { border-color: #1a3a5c; background: #f0f5fa; }
.type-name { font-size: 28rpx; font-weight: bold; display: block; }
.type-desc { font-size: 24rpx; color: #666; margin-top: 8rpx; display: block; }
.form { background: #fff; border-radius: 12rpx; padding: 30rpx; }
.label { font-size: 26rpx; font-weight: bold; display: block; margin: 16rpx 0 8rpx; }
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; margin-bottom: 8rpx; }
.textarea { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; height: 160rpx; width: 100%; }
.btn-submit { background: #1a3a5c; color: #fff; border: none; border-radius: 8rpx; margin-top: 30rpx; font-size: 30rpx; }
</style>
