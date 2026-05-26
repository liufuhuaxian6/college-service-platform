<template>
  <div class="empty-state">
    <el-icon><Document /></el-icon>
    <div class="empty-state__title">{{ title }}</div>
    <div v-if="description" class="empty-state__desc">{{ description }}</div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
})
</script>

<style scoped lang="scss">
.empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--app-text-secondary);

  .el-icon {
    font-size: 34px;
    color: #C9CDD4;
  }
}

.empty-state__title {
  color: var(--app-text-regular);
  font-size: 14px;
}

.empty-state__desc {
  font-size: 12px;
}
</style>
