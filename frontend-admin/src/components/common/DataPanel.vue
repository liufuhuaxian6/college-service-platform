<template>
  <section class="data-panel">
    <div v-if="title || $slots.actions" class="data-panel__header">
      <div class="data-panel__heading">
        <span v-if="title" class="data-panel__dot" />
        <div>
          <h2 v-if="title">{{ title }}</h2>
          <p v-if="description">{{ description }}</p>
        </div>
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
  padding: 18px;
}

.data-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    color: var(--app-text);
    font-size: 16px;
    font-weight: 650;
    letter-spacing: 0.3px;
  }

  p {
    margin: 5px 0 0;
    color: var(--app-text-secondary);
    font-size: 13px;
  }
}

.data-panel__heading {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.data-panel__dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 2px;
  transform: rotate(45deg);
  background: var(--app-primary);
  box-shadow: 0 0 0 3px var(--app-primary-light);
}

.data-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
