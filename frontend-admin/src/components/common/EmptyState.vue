<template>
  <div class="empty-state">
    <div class="empty-state__badge">
      <el-icon><FolderOpened /></el-icon>
    </div>
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
  gap: 10px;
  color: var(--app-text-secondary);
}

.empty-state__badge {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--app-muted);
  border: 1px dashed var(--app-border);

  .el-icon {
    font-size: 26px;
    color: #C9C1BC;
  }
}

.empty-state__title {
  color: var(--app-text-regular);
  font-size: 14px;
  font-weight: 500;
}

.empty-state__desc {
  font-size: 12px;
  max-width: 320px;
  text-align: center;
  line-height: 1.6;
}
</style>
