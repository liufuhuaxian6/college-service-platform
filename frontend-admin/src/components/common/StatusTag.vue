<template>
  <span class="status-tag" :class="`status-tag--${normalized}`">
    <span class="status-tag__dot" />
    {{ text || label || computedLabel }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
  // 直接指定色调与文案 (绕过 status 映射), 供广播历史等非标准状态使用
  type: { type: String, default: '' },
  text: { type: String, default: '' },
})

const TYPE_TO_CLASS = {
  success: 'approved',
  warning: 'pending',
  danger: 'rejected',
  info: 'default',
}

const normalized = computed(() => {
  if (props.type) return TYPE_TO_CLASS[props.type] || 'default'
  const map = {
    draft: 'draft',
    pending: 'pending',
    approved: 'approved',
    rejected: 'rejected',
    withdrawn: 'withdrawn',
    downloaded: 'locked',
    active: 'active',
    completed: 'completed',
    suspended: 'suspended',
    online: 'completed',
    placeholder: 'draft',
    1: 'active',
    0: 'disabled',
  }
  return map[props.status] || 'default'
})

const computedLabel = computed(() => {
  const map = {
    draft: '草稿',
    pending: '待审批',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
    downloaded: '已锁定归档',
    active: '进行中',
    completed: '已完成',
    suspended: '已暂停',
    online: '已上线',
    placeholder: '待补传',
    1: '启用',
    0: '停用',
  }
  return map[props.status] || props.status || '-'
})
</script>

<style scoped lang="scss">
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  border: 1px solid transparent;
  white-space: nowrap;
}

.status-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.85;
}

.status-tag--draft,
.status-tag--withdrawn,
.status-tag--disabled,
.status-tag--suspended,
.status-tag--default {
  color: var(--app-info);
  background: var(--app-info-bg);
  border-color: #E5E6EB;
}

.status-tag--pending,
.status-tag--active {
  color: var(--app-warning);
  background: var(--app-warning-bg);
  border-color: #F3D6A2;
}

.status-tag--approved,
.status-tag--completed {
  color: var(--app-success);
  background: var(--app-success-bg);
  border-color: #B8DCC8;
}

.status-tag--rejected {
  color: var(--app-danger);
  background: var(--app-danger-bg);
  border-color: #E8B8BA;
}

.status-tag--locked {
  color: #475467;
  background: #EEF0F3;
  border-color: #D8DCE2;
}
</style>
