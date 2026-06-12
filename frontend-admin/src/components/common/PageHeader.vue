<template>
  <div class="page-header">
    <div class="page-header__main">
      <span class="page-header__bar" />
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;

  h1 {
    margin: 0;
    color: var(--app-text);
    font-family: var(--app-font-display);
    font-size: 23px;
    font-weight: 700;
    letter-spacing: 1px;
    line-height: 1.25;
  }

  p {
    margin: 7px 0 0;
    color: var(--app-text-secondary);
    font-size: 13px;
    letter-spacing: 0.3px;
  }
}

.page-header__main {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

/* 红金双色装饰条: 上段人大红, 下段鎏金 */
.page-header__bar {
  flex: 0 0 auto;
  width: 4px;
  height: 30px;
  margin-top: 2px;
  border-radius: 2px;
  background: linear-gradient(
    180deg,
    var(--app-primary) 0%,
    var(--app-primary) 62%,
    var(--app-gold) 62%,
    var(--app-gold) 100%
  );
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
