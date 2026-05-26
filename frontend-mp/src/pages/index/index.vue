<template>
  <view class="page">
    <view class="hero">
      <view class="hero-main">
        <text class="eyebrow">学院综合服务</text>
        <text class="greeting">你好，{{ userStore.name || '同学' }}</text>
        <text class="school">学生服务、党团事务与证明审批统一入口</text>
      </view>
      <view class="notify" @click="goNotify">
        <text class="notify-bell">铃</text>
        <text v-if="unreadCount > 0" class="notify-dot">{{ unreadCount }}</text>
      </view>
    </view>

    <view class="quick-panel">
      <view class="quick-item" @click="navigateTo('/pages/qa/index')">
        <text class="quick-value">问</text>
        <text class="quick-label">政策咨询</text>
      </view>
      <view class="quick-item" @click="navigateTo('/pages/approval/index')">
        <text class="quick-value">审</text>
        <text class="quick-label">审批进度</text>
      </view>
      <view class="quick-item" @click="navigateTo('/pages/party/index')">
        <text class="quick-value">团</text>
        <text class="quick-label">党团流程</text>
      </view>
    </view>

    <SectionTitle title="常用服务" />
    <view class="service-grid">
      <ServiceCard
        v-for="item in menuItems"
        :key="item.url"
        :title="item.label"
        :desc="item.desc"
        :icon="item.icon"
        :badge="item.badge"
        :action="item.action"
        :tone="item.tone"
        @click="navigateTo(item.url)"
      />
    </view>

    <SectionTitle title="最新通知">
      <text v-if="notifications.length" class="section-action" @click="goNotify">查看全部</text>
    </SectionTitle>
    <view class="notice-panel">
      <view v-if="notifications.length" class="notify-list">
        <view v-for="n in notifications" :key="n.id" class="notice-item" @click="goNotify">
          <view class="notice-dot" />
          <view class="notice-content">
            <text class="notice-title">{{ n.title }}</text>
            <text class="notice-time">{{ n.createdAt }}</text>
          </view>
        </view>
      </view>
      <EmptyState v-else title="暂无通知" description="审批提醒和系统消息会显示在这里。" />
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { notifyApi } from '@/api'
import ServiceCard from '@/components/ServiceCard.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore()
const unreadCount = ref(0)
const notifications = ref([])

const menuItems = [
  {
    label: '智能问答',
    desc: '政策制度、报到入学、党团事务即时咨询',
    icon: '问',
    badge: 'RAG',
    action: '开始提问',
    tone: 'red',
    url: '/pages/qa/index',
  },
  {
    label: '文件与模板',
    desc: '政策文件、请假条、活动预算等模板下载',
    icon: '文',
    badge: '资源',
    action: '查阅下载',
    tone: 'blue',
    url: '/pages/qa/document',
  },
  {
    label: '党团进度',
    desc: '查看个人流程节点、材料与完成状态',
    icon: '流',
    badge: '流程',
    action: '查看节点',
    tone: 'green',
    url: '/pages/party/index',
  },
  {
    label: '我的申请',
    desc: '证明申请、审批流转与下载归档',
    icon: '审',
    badge: '审批',
    action: '办理证明',
    tone: 'amber',
    url: '/pages/approval/index',
  },
]

const tabPages = new Set([
  '/pages/index/index',
  '/pages/qa/index',
  '/pages/approval/index',
  '/pages/profile/index',
])

function navigateTo(url) {
  if (tabPages.has(url)) {
    uni.switchTab({ url })
    return
  }
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
  } catch (e) {
    // 首页通知失败不阻塞服务入口
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 36rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 12% 0%, rgba(155, 44, 54, 0.1), transparent 34%),
    linear-gradient(180deg, #FBF7F5 0%, var(--mp-bg) 34%, var(--mp-bg) 100%);
}

.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  min-height: 196rpx;
  margin-bottom: 20rpx;
  padding: 34rpx 30rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #9B2C36 0%, #7E2630 100%);
  box-shadow: 0 18rpx 40rpx rgba(155, 44, 54, 0.22);
  box-sizing: border-box;
}

.hero-main {
  min-width: 0;
}

.eyebrow {
  display: inline-flex;
  width: fit-content;
  margin-bottom: 20rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.86);
  font-size: 21rpx;
}

.greeting {
  display: block;
  color: #fff;
  font-size: 38rpx;
  font-weight: 800;
  line-height: 1.25;
}

.school {
  display: block;
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 23rpx;
  line-height: 1.45;
}

.notify {
  position: relative;
  width: 66rpx;
  height: 66rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.notify-bell {
  font-size: 24rpx;
  font-weight: 700;
}

.notify-dot {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  border-radius: 999rpx;
  background: #F0B35A;
  color: #3D2024;
  font-size: 20rpx;
  font-weight: 700;
}

.quick-panel {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  margin: -42rpx 18rpx 28rpx;
  padding: 18rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--mp-shadow);
  position: relative;
  z-index: 2;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14rpx 4rpx;
}

.quick-value {
  width: 52rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 16rpx;
  background: var(--mp-primary-light);
  color: var(--mp-primary);
  font-size: 24rpx;
  font-weight: 800;
}

.quick-label {
  margin-top: 10rpx;
  color: var(--mp-text-regular);
  font-size: 22rpx;
}

.service-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.section-action {
  color: var(--mp-primary);
  font-size: 23rpx;
  font-weight: 500;
}

.notice-panel {
  min-height: 210rpx;
  background: var(--mp-card);
  border: 1rpx solid rgba(31, 35, 41, 0.05);
  border-radius: 20rpx;
  box-shadow: 0 10rpx 26rpx rgba(31, 35, 41, 0.04);
  overflow: hidden;
}

.notice-item {
  display: flex;
  gap: 18rpx;
  padding: 24rpx;
  border-bottom: 1rpx solid var(--mp-border);
}

.notice-item:last-child {
  border-bottom: none;
}

.notice-dot {
  width: 12rpx;
  height: 12rpx;
  margin-top: 12rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--mp-primary);
}

.notice-content {
  min-width: 0;
}

.notice-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 26rpx;
  line-height: 1.45;
}

.notice-time {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
}
</style>
