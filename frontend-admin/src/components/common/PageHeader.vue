<template>
  <div class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
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
  margin-bottom: 16px;

  h1 {
    margin: 0;
    color: var(--app-text);
    font-size: 22px;
    font-weight: 650;
    letter-spacing: 0;
  }

  p {
    margin: 6px 0 0;
    color: var(--app-text-secondary);
    font-size: 13px;
  }
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
