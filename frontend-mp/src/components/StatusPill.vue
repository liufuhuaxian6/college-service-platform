<template>
  <view class="status-pill" :class="`status-pill--${type}`">{{ text }}</view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, default: '' },
  label: { type: String, default: '' },
})

const type = computed(() => {
  const map = {
    draft: 'info',
    pending: 'warning',
    approved: 'success',
    rejected: 'danger',
    withdrawn: 'info',
    downloaded: 'locked',
    active: 'warning',
    finished: 'success',
    completed: 'success',
    todo: 'info',
    suspended: 'info',
  }
  return map[props.status] || 'info'
})

const text = computed(() => {
  const map = {
    draft: '草稿',
    pending: '待审批',
    approved: '已通过',
    rejected: '已驳回',
    withdrawn: '已撤回',
    downloaded: '已锁定归档',
    active: '进行中',
    finished: '已完成',
    completed: '已完成',
    todo: '未开始',
    suspended: '已暂停',
  }
  return props.label || map[props.status] || props.status || '-'
})
</script>

<style scoped>
.status-pill {
  display: inline-flex;
  align-items: center;
  height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 650;
  border: 1rpx solid transparent;
  white-space: nowrap;
}

.status-pill--warning {
  color: #9A6A16;
  background: #FFF7E8;
  border-color: #F3D6A2;
}

.status-pill--success {
  color: #2F7D55;
  background: #ECF6F0;
  border-color: #B8DCC8;
}

.status-pill--danger {
  color: #B4232C;
  background: #FBECEC;
  border-color: #E8B8BA;
}

.status-pill--locked,
.status-pill--info {
  color: #5B6472;
  background: #F2F3F5;
  border-color: #E5E6EB;
}
</style>
