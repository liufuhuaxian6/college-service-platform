<template>
  <view class="page">
    <view class="actions">
      <text class="mark-all" @click="markAll">全部标记已读</text>
    </view>
    <view class="notify-item" v-for="n in list" :key="n.id" :class="{ unread: !n.isRead }" @click="markRead(n)">
      <text class="notify-title">{{ n.title }}</text>
      <text class="notify-content">{{ n.content }}</text>
      <text class="notify-time">{{ n.createdAt }}</text>
    </view>
    <view class="empty" v-if="!list.length">暂无消息</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { notifyApi } from '@/api'

const list = ref([])

onMounted(async () => {
  const res = await notifyApi.getPage({ page: 1, size: 50 })
  list.value = res.data?.records || []
})

async function markRead(n) {
  if (!n.isRead) {
    await notifyApi.markRead(n.id)
    n.isRead = true
  }
}

async function markAll() {
  await notifyApi.markAllRead()
  list.value.forEach(n => n.isRead = true)
  uni.showToast({ title: '已全部标记', icon: 'success' })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.actions { text-align: right; margin-bottom: 16rpx; }
.mark-all { color: #409eff; font-size: 26rpx; }
.notify-item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 12rpx; }
.notify-item.unread { border-left: 6rpx solid #409eff; }
.notify-title { font-size: 28rpx; font-weight: bold; display: block; }
.notify-content { font-size: 26rpx; color: #666; margin-top: 8rpx; display: block; }
.notify-time { font-size: 22rpx; color: #999; margin-top: 8rpx; display: block; }
.empty { text-align: center; color: #999; padding: 80rpx; }
</style>
