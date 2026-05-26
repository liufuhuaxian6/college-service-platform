<template>
  <div class="filter-bar">
    <slot />
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  margin-bottom: 14px;
  padding: 14px 16px 0;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
}

:deep(.el-form-item) {
  margin-bottom: 14px;
}
</style>
