<template>
  <view class="empty-state">
    <view class="empty-icon">
      <view class="empty-icon__doc" />
      <view class="empty-icon__line" />
      <view class="empty-icon__line short" />
    </view>
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
  position: relative;
  width: 96rpx;
  height: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin-bottom: 18rpx;
  border-radius: 50%;
  background: #F4F1ED;
  border: 1rpx dashed var(--mp-border);
}

.empty-icon__doc {
  width: 36rpx;
  height: 6rpx;
  border-radius: 4rpx;
  background: #D6CFC9;
}

.empty-icon__line {
  width: 44rpx;
  height: 6rpx;
  border-radius: 4rpx;
  background: #E2DCD6;
}

.empty-icon__line.short {
  width: 26rpx;
}

.empty-title {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--mp-text-regular);
}

.empty-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  text-align: center;
  line-height: 1.6;
  max-width: 480rpx;
}
</style>
