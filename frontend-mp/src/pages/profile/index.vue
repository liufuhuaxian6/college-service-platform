<template>
  <view class="page">
    <!-- 个人信息卡片 -->
    <view class="profile-card">
      <view class="avatar">{{ profile.name?.charAt(0) || '?' }}</view>
      <view class="info">
        <text class="name">{{ profile.name }}</text>
        <text class="detail">{{ profile.studentId }} · {{ profile.major }}</text>
        <text class="detail">{{ profile.grade }} · {{ profile.className }}</text>
      </view>
    </view>

    <!-- 荣誉列表 -->
    <view class="section">
      <text class="section-title">我的荣誉</text>
      <view class="honor-item" v-for="h in honors" :key="h.id">
        <text class="honor-name">{{ h.honorName }}</text>
        <text class="honor-meta">{{ h.honorLevel }} · {{ h.awardDate }}</text>
      </view>
      <view class="empty" v-if="!honors.length">暂无荣誉记录</view>
    </view>

    <!-- 退出登录 -->
    <button class="btn-logout" @click="handleLogout">退出登录</button>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { studentApi } from '@/api'

const userStore = useUserStore()
const profile = ref({})
const honors = ref([])

onMounted(async () => {
  try {
    const profileRes = await studentApi.getProfile()
    profile.value = profileRes.data || {}
    const honorsRes = await studentApi.getHonors()
    honors.value = honorsRes.data || []
  } catch (e) { /* ignore */ }
})

function handleLogout() {
  uni.showModal({
    title: '确认退出',
    content: '确定退出登录？',
    success: (res) => { if (res.confirm) userStore.logout() },
  })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.profile-card { display: flex; align-items: center; gap: 24rpx; background: #1a3a5c; color: #fff; padding: 30rpx; border-radius: 12rpx; margin-bottom: 20rpx; }
.avatar { width: 80rpx; height: 80rpx; border-radius: 50%; background: #fff; color: #1a3a5c; font-size: 36rpx; font-weight: bold; display: flex; align-items: center; justify-content: center; }
.name { font-size: 32rpx; font-weight: bold; display: block; }
.detail { font-size: 24rpx; opacity: 0.8; display: block; margin-top: 4rpx; }
.section { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 16rpx; display: block; color: #1a3a5c; }
.honor-item { padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.honor-name { font-size: 28rpx; font-weight: bold; display: block; }
.honor-meta { font-size: 22rpx; color: #999; margin-top: 4rpx; display: block; }
.empty { text-align: center; color: #999; padding: 40rpx; font-size: 26rpx; }
.btn-logout { background: #fff; color: #f56c6c; border: 1rpx solid #f56c6c; border-radius: 8rpx; margin-top: 40rpx; font-size: 28rpx; }
</style>
