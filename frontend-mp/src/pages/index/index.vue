<template>
  <view class="container">
    <!-- 顶部欢迎 -->
    <view class="header">
      <view class="welcome">
        <text class="greeting">你好，{{ userStore.name || '同学' }}</text>
        <text class="school">中国人民大学信息学院</text>
      </view>
      <view class="notify-icon" @click="goNotify">
        <text class="badge" v-if="unreadCount > 0">{{ unreadCount }}</text>
        🔔
      </view>
    </view>

    <!-- 四宫格功能入口 -->
    <view class="grid">
      <view class="grid-item" v-for="item in menuItems" :key="item.url" @click="navigateTo(item.url)">
        <view class="grid-icon">{{ item.icon }}</view>
        <text class="grid-label">{{ item.label }}</text>
      </view>
    </view>

    <!-- 最新通知 -->
    <view class="section">
      <view class="section-title">最新通知</view>
      <view class="notify-list" v-if="notifications.length">
        <view class="notify-item" v-for="n in notifications" :key="n.id">
          <text class="notify-title">{{ n.title }}</text>
          <text class="notify-time">{{ n.createdAt }}</text>
        </view>
      </view>
      <view class="empty" v-else>暂无通知</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { notifyApi } from '@/api'

const userStore = useUserStore()
const unreadCount = ref(0)
const notifications = ref([])

const menuItems = [
  { label: '智能问答', icon: '💬', url: '/pages/qa/index' },
  { label: '政策文档', icon: '📄', url: '/pages/qa/document' },
  { label: '党团进度', icon: '🏛️', url: '/pages/party/index' },
  { label: '我的申请', icon: '📋', url: '/pages/approval/index' },
]

function navigateTo(url) {
  uni.navigateTo({ url })
}

function goNotify() {
  uni.navigateTo({ url: '/pages/notify/index' })
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  try {
    const res = await notifyApi.getUnreadCount()
    unreadCount.value = res.data?.count || 0
    const listRes = await notifyApi.getPage({ page: 1, size: 5 })
    notifications.value = listRes.data?.records || []
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.container { padding: 20rpx; }
.header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 30rpx; background: #1a3a5c; color: #fff; border-radius: 12rpx; margin-bottom: 30rpx;
}
.greeting { font-size: 34rpx; font-weight: bold; display: block; }
.school { font-size: 24rpx; opacity: 0.8; margin-top: 8rpx; display: block; }
.notify-icon { font-size: 40rpx; position: relative; }
.badge { position: absolute; top: -10rpx; right: -10rpx; background: red; color: #fff; font-size: 20rpx; padding: 2rpx 10rpx; border-radius: 20rpx; }
.grid { display: flex; flex-wrap: wrap; gap: 20rpx; margin-bottom: 30rpx; }
.grid-item {
  width: calc(50% - 10rpx); background: #fff; border-radius: 12rpx; padding: 30rpx;
  display: flex; flex-direction: column; align-items: center; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.06);
}
.grid-icon { font-size: 48rpx; margin-bottom: 12rpx; }
.grid-label { font-size: 28rpx; color: #333; }
.section { background: #fff; border-radius: 12rpx; padding: 24rpx; }
.section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 16rpx; color: #1a3a5c; }
.notify-item { padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; display: flex; justify-content: space-between; }
.notify-title { font-size: 26rpx; }
.notify-time { font-size: 22rpx; color: #999; }
.empty { text-align: center; color: #999; padding: 40rpx; font-size: 26rpx; }
</style>
