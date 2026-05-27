<template>
  <div class="metric-card">
    <div class="metric-card__icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div>
      <div class="metric-card__value">{{ value }}</div>
      <div class="metric-card__label">{{ label }}</div>
    </div>
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
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 94px;
  padding: 18px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
}

.metric-card__icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  color: var(--app-primary);
  background: var(--app-primary-light);
  border-radius: 8px;
  font-size: 22px;
}

.metric-card__value {
  color: var(--app-text);
  font-size: 26px;
  line-height: 1.1;
  font-weight: 700;
}

.metric-card__label {
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
</style>
