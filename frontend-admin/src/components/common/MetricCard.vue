<template>
  <div class="metric-card">
    <div class="metric-card__icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div class="metric-card__body">
      <div class="metric-card__value">{{ value }}</div>
      <div class="metric-card__label">{{ label }}</div>
    </div>
    <span class="metric-card__accent" />
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '-' },
  icon: { type: String, default: 'DataLine' },
})
</script>

<style scoped lang="scss">
.metric-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 96px;
  padding: 20px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
  overflow: hidden;
  transition: transform 0.25s var(--app-ease), box-shadow 0.25s var(--app-ease);

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--app-shadow-lg);

    .metric-card__accent {
      transform: scaleX(1);
    }

    .metric-card__icon {
      color: #fff;
      background: var(--app-primary);
    }
  }
}

.metric-card__icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  color: var(--app-primary);
  background: var(--app-primary-light);
  border-radius: 12px;
  font-size: 23px;
  transition: all 0.25s var(--app-ease);
}

.metric-card__value {
  color: var(--app-text);
  font-size: 28px;
  line-height: 1.1;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.metric-card__label {
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
  letter-spacing: 0.5px;
}

/* hover 时滑出的底部红色细线 */
.metric-card__accent {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--app-primary), var(--app-gold));
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s var(--app-ease);
}
</style>
