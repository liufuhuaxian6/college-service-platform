<template>
  <view class="section-title">
    <view class="section-title__main">
      <view class="section-title__bar" />
      <text>{{ title }}</text>
    </view>
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
  margin: 8rpx 0 18rpx;
  color: var(--mp-text-main);
  font-family: var(--mp-font-display);
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
}

.section-title__main {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

/* 红金双色装饰条, 与管理端 PageHeader 呼应 */
.section-title__bar {
  width: 7rpx;
  height: 30rpx;
  border-radius: 4rpx;
  background: linear-gradient(
    180deg,
    var(--mp-primary) 0%,
    var(--mp-primary) 62%,
    var(--mp-gold) 62%,
    var(--mp-gold) 100%
  );
}
</style>
