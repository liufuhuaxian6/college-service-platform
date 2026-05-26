<template>
  <view class="service-card" :class="`service-card--${tone}`" @click="$emit('click')">
    <view class="service-mark" />
    <view class="service-top">
      <view class="service-icon">{{ icon }}</view>
      <text class="service-badge">{{ badge }}</text>
    </view>
    <view class="service-body">
      <text class="service-title">{{ title }}</text>
      <text class="service-desc">{{ desc }}</text>
    </view>
    <view class="service-footer">
      <text class="service-action">{{ action }}</text>
      <text class="service-arrow">›</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  icon: { type: String, default: '•' },
  badge: { type: String, default: '服务' },
  action: { type: String, default: '进入办理' },
  tone: { type: String, default: 'red' },
})
defineEmits(['click'])
</script>

<style scoped>
.service-card {
  position: relative;
  min-height: 214rpx;
  padding: 24rpx 22rpx 20rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  border-radius: 22rpx;
  box-shadow: 0 12rpx 30rpx rgba(31, 35, 41, 0.06);
  box-sizing: border-box;
  overflow: hidden;
}

.service-card:active {
  transform: scale(0.985);
  opacity: 0.92;
}

.service-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.service-mark {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 6rpx;
  background: var(--card-color);
}

.service-icon {
  width: 62rpx;
  height: 62rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 18rpx;
  background: var(--card-soft);
  color: var(--card-color);
  font-size: 28rpx;
  font-weight: 800;
}

.service-badge {
  padding: 5rpx 12rpx;
  border-radius: 999rpx;
  background: var(--card-soft);
  color: var(--card-color);
  font-size: 20rpx;
  line-height: 1.2;
}

.service-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 700;
}

.service-desc {
  display: block;
  margin-top: 9rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
  line-height: 1.35;
}

.service-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #F0F1F3;
}

.service-action {
  color: var(--mp-text-regular);
  font-size: 21rpx;
}

.service-arrow {
  color: var(--card-color);
  font-size: 32rpx;
  line-height: 1;
}

.service-card--red {
  --card-color: #9B2C36;
  --card-soft: #F7EDEF;
}

.service-card--blue {
  --card-color: #356382;
  --card-soft: #EDF4F8;
}

.service-card--green {
  --card-color: #3E7256;
  --card-soft: #ECF5EF;
}

.service-card--amber {
  --card-color: #8A6422;
  --card-soft: #FBF3E4;
}
</style>
