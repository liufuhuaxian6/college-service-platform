<template>
  <view class="service-card" :class="`service-card--${tone}`" @click="$emit('click')">
    <view class="service-icon">
      <image v-if="iconSrc" class="service-icon__img" :src="iconSrc" mode="aspectFit" />
    </view>
    <view class="service-body">
      <view class="service-title-row">
        <text class="service-title">{{ title }}</text>
        <text v-if="badge" class="service-badge">{{ badge }}</text>
      </view>
      <text class="service-desc">{{ desc }}</text>
    </view>
    <text class="service-arrow">›</text>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  // SVG 图标路径 (如 /static/icons/icon-qa.svg)
  iconSrc: { type: String, default: '' },
  badge: { type: String, default: '' },
  tone: { type: String, default: 'red' },
  // 兼容旧调用
  icon: { type: String, default: '' },
  action: { type: String, default: '' },
})
defineEmits(['click'])
</script>

<style scoped>
/* 横向行卡: 图标 + 标题/描述 + 箭头 */
.service-card {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 26rpx 24rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(35, 31, 32, 0.06);
  border-radius: 22rpx;
  box-shadow: 0 10rpx 26rpx rgba(35, 31, 32, 0.05);
  box-sizing: border-box;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.service-card:active {
  transform: scale(0.985);
  opacity: 0.92;
}

.service-icon {
  width: 84rpx;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 22rpx;
  background: var(--card-bg);
}

.service-icon__img {
  width: 46rpx;
  height: 46rpx;
}

.service-body {
  flex: 1;
  min-width: 0;
}

.service-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.service-title {
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 700;
}

.service-badge {
  padding: 3rpx 12rpx;
  border-radius: 999rpx;
  color: var(--card-color);
  background: var(--card-bg);
  font-size: 19rpx;
  font-weight: 650;
}

.service-desc {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 23rpx;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-arrow {
  flex-shrink: 0;
  color: var(--mp-text-muted);
  font-size: 36rpx;
  line-height: 1;
}

.service-card--red {
  --card-color: #9D2235;
  --card-bg: #F7ECEE;
}

.service-card--blue {
  --card-color: #356382;
  --card-bg: #EBF1F5;
}

.service-card--green {
  --card-color: #3E7256;
  --card-bg: #ECF4EF;
}

.service-card--amber {
  --card-color: #8A6422;
  --card-bg: #F7F0E3;
}
</style>
