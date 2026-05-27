<template>
  <view class="service-card" :class="`service-card--${tone}`" @click="$emit('click')">
    <view class="service-mark" />
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
  action: { type: String, default: '进入办理' },
  tone: { type: String, default: 'red' },
})
defineEmits(['click'])
</script>

<style scoped>
.service-card {
  position: relative;
  min-height: 200rpx;
  padding: 28rpx 24rpx 22rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  border-radius: 22rpx;
  box-shadow: 0 12rpx 30rpx rgba(31, 35, 41, 0.06);
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.service-card:active {
  transform: scale(0.985);
  opacity: 0.92;
}

.service-mark {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 6rpx;
  background: var(--card-color);
}

.service-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 700;
}

.service-desc {
  display: block;
  margin-top: 10rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
  line-height: 1.45;
}

.service-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
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
}

.service-card--blue {
  --card-color: #356382;
}

.service-card--green {
  --card-color: #3E7256;
}

.service-card--amber {
  --card-color: #8A6422;
}
</style>
