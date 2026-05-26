<template>
  <view class="section-title">
    <text>{{ title }}</text>
    <slot />
  </view>
</template>

<script setup>
defineProps({ title: { type: String, required: true } })
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8rpx 0 16rpx;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 650;
}
</style>

