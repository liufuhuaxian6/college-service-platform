<template>
  <view class="page">
    <view class="profile-card">
      <view class="avatar">{{ userInfo.name?.charAt(0) || '?' }}</view>
      <view class="info">
        <text class="name">{{ userInfo.name }}</text>
        <text class="detail">{{ userInfo.major }}</text>
        <text class="detail">{{ userInfo.className }}</text>
      </view>
    </view>

    <view class="section">
      <text class="section-title">我的荣誉</text>
      <view class="honor-item" v-for="h in honors" :key="h.id">
        <text class="honor-name">{{ h.name }}</text>
        <text class="honor-meta">{{ h.level }} · {{ h.date }}</text>
      </view>
      <view class="empty" v-if="!honors.length">暂无荣誉记录</view>
    </view>

    <button class="btn-logout" @click="handleLogout">退出登录</button>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const userInfo = computed(() => ({
  name: userStore.name || '同学',
  major: userStore.major || '—',
  className: userStore.className || '—',
}))

const mockHonors = [ // @UI_DEV_ONLY
  { id: 1, name: '优秀学生奖学金', level: '校级', date: '2026-04-18' }, // @UI_DEV_ONLY
  { id: 2, name: '社会实践先进个人', level: '院级', date: '2025-12-05' }, // @UI_DEV_ONLY
  { id: 3, name: '学术创新竞赛二等奖', level: '校级', date: '2025-10-21' }, // @UI_DEV_ONLY
] // @UI_DEV_ONLY

const honors = ref(mockHonors) // @UI_DEV_ONLY

function handleLogout() {
  uni.showModal({
    title: '确认退出',
    content: '确定退出登录？',
    success: (res) => { if (res.confirm) userStore.logout() },
  })
}
</script>

<style scoped>
.page { padding: 20rpx; background: #1a3a5c; min-height: 100vh; box-sizing: border-box; }
.profile-card { display: flex; align-items: center; gap: 24rpx; background: rgba(255, 255, 255, 0.12); color: #fff; padding: 30rpx; border-radius: 12rpx; margin-bottom: 20rpx; border: 1rpx solid rgba(255,255,255,0.16); }
.avatar { width: 80rpx; height: 80rpx; border-radius: 50%; background: #fff; color: #1a3a5c; font-size: 36rpx; font-weight: bold; display: flex; align-items: center; justify-content: center; }
.name { font-size: 32rpx; font-weight: bold; display: block; }
.detail { font-size: 24rpx; opacity: 0.8; display: block; margin-top: 4rpx; }
.section { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 16rpx; display: block; color: #1a3a5c; }
.honor-item { padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.honor-name { font-size: 28rpx; font-weight: bold; display: block; }
.honor-meta { font-size: 22rpx; color: #999; margin-top: 4rpx; display: block; }
.empty { text-align: center; color: #999; padding: 40rpx; font-size: 26rpx; }
.btn-logout { background: #f56c6c; color: #fff; border: none; border-radius: 10rpx; margin-top: 40rpx; font-size: 30rpx; padding: 18rpx 0; }
</style>
