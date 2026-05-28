<template>
  <span class="status-tag" :class="`status-tag--${normalized}`">
    {{ label || computedLabel }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: [String, Number], default: '' },
  label: { type: String, default: '' },
})

const normalized = computed(() => {
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
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1;
  border: 1px solid transparent;
}

.status-tag--draft,
.status-tag--withdrawn,
.status-tag--disabled,
.status-tag--default {
  color: var(--app-info);
  background: #F2F3F5;
  border-color: #E5E6EB;
}

.status-tag--pending,
.status-tag--active {
  color: var(--app-warning);
  background: #FFF7E8;
  border-color: #F3D6A2;
}

.status-tag--approved,
.status-tag--completed {
  color: var(--app-success);
  background: #ECF6F0;
  border-color: #B8DCC8;
}

.status-tag--rejected {
  color: var(--app-danger);
  background: #FBECEC;
  border-color: #E8B8BA;
}

.status-tag--locked {
  color: #475467;
  background: #EEF0F3;
  border-color: #D8DCE2;
}
</style>
