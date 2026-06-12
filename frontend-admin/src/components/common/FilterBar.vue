<template>
  <div class="filter-bar">
    <slot />
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  position: relative;
  padding: 16px 18px 2px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  overflow: hidden;

  /* 左侧细红线, 与 PageHeader 装饰条呼应 */
  &::before {
    content: "";
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 3px;
    background: linear-gradient(180deg, var(--app-primary-soft), transparent);
  }
}

:deep(.el-form-item) {
  margin-bottom: 14px;
}

:deep(.el-form-item__label) {
  color: var(--app-text-secondary);
}
</style>
