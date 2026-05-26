<template>
  <section class="data-panel">
    <div v-if="title || $slots.actions" class="data-panel__header">
      <div>
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="data-panel__actions">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
})
</script>

<style scoped lang="scss">
.data-panel {
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow);
  padding: 16px;
}

.data-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  h2 {
    margin: 0;
    color: var(--app-text);
    font-size: 16px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    color: var(--app-text-secondary);
    font-size: 13px;
  }
}

.data-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
