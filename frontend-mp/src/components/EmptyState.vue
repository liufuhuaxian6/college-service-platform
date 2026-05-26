<template>
  <view class="empty-state">
    <view class="empty-icon">—</view>
    <text class="empty-title">{{ title }}</text>
    <text v-if="description" class="empty-desc">{{ description }}</text>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
})
</script>

<style scoped>
.empty-state {
  padding: 70rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--mp-text-sub);
}

.empty-icon {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
  border-radius: 50%;
  background: #F0F1F3;
  color: #C0C4CC;
}

.empty-title {
  font-size: 26rpx;
}

.empty-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
}
</style>

