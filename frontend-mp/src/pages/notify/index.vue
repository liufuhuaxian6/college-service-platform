<template>
  <view class="page">
    <view class="actions">
      <text class="mark-all" @click="markAll">全部标记已读</text>
    </view>

    <view
      v-for="n in list"
      :key="n.id"
      class="notify-item"
      :class="{ unread: !n.isRead }"
      @click="markRead(n)"
    >
      <text class="notify-title">{{ n.title }}</text>
      <text class="notify-content">{{ n.content }}</text>
      <text class="notify-time">{{ n.createdAt }}</text>
    </view>

    <EmptyState v-if="!list.length" title="暂无消息" description="新的审批和系统通知会显示在这里。" />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { notifyApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])

onMounted(loadList)

async function loadList() {
  const res = await notifyApi.getPage({ page: 1, size: 50 })
  list.value = res.data?.records || []
}

async function markRead(n) {
  if (!n.isRead) {
    await notifyApi.markRead(n.id)
    n.isRead = true
  }
}

async function markAll() {
  await notifyApi.markAllRead()
  list.value.forEach((n) => {
    n.isRead = true
  })
  uni.showToast({ title: '已全部标记', icon: 'success' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: var(--mp-bg);
  box-sizing: border-box;
}

.actions {
  text-align: right;
  margin-bottom: 16rpx;
}

.mark-all {
  color: var(--mp-primary);
  font-size: 26rpx;
}

.notify-item {
  padding: 24rpx;
  margin-bottom: 12rpx;
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: var(--mp-radius);
}

.notify-item.unread {
  border-left: 8rpx solid var(--mp-primary);
}

.notify-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 28rpx;
  font-weight: 700;
}

.notify-content {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 26rpx;
  line-height: 1.5;
}

.notify-time {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-muted);
  font-size: 22rpx;
}
</style>
