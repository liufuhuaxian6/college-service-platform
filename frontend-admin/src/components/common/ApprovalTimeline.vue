<template>
  <div class="approval-timeline">
    <div v-for="(item, index) in records" :key="item.id || index" class="approval-timeline__item">
      <span class="approval-timeline__dot" />
      <div class="approval-timeline__content">
        <div class="approval-timeline__title">
          {{ item.action || item.status || '审批记录' }}
          <StatusTag v-if="item.status" :status="item.status" />
        </div>
        <div v-if="item.comment" class="approval-timeline__comment">{{ item.comment }}</div>
        <div class="approval-timeline__meta">{{ item.createdAt || item.updatedAt || '-' }}</div>
      </div>
    </div>
    <EmptyState v-if="!records.length" title="暂无审批记录" />
  </div>
</template>

<script setup>
import StatusTag from './StatusTag.vue'
import EmptyState from './EmptyState.vue'

defineProps({
  records: { type: Array, default: () => [] },
})
</script>

<style scoped lang="scss">
.approval-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.approval-timeline__item {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 16px;

  &::before {
    content: "";
    position: absolute;
    left: 5px;
    top: 18px;
    bottom: 0;
    width: 1px;
    background: var(--app-border);
  }

  &:last-child::before {
    display: none;
  }
}

.approval-timeline__dot {
  width: 11px;
  height: 11px;
  margin-top: 4px;
  border-radius: 50%;
  background: var(--app-primary);
  flex: 0 0 auto;
}

.approval-timeline__content {
  flex: 1;
}

.approval-timeline__title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text);
  font-weight: 600;
}

.approval-timeline__comment {
  margin-top: 6px;
  color: var(--app-text-regular);
  line-height: 1.6;
}

.approval-timeline__meta {
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 12px;
}
</style>
