<template>
  <view class="page mp-page-bg">
    <!-- ===== 顶部问候 ===== -->
    <view class="hero mp-hero">
      <RucSeal :size="280" tone="light" class="mp-hero-seal" />

      <!-- 第一行: 校徽校名 + 通知铃铛 -->
      <view class="hero-top">
        <view class="hero-brand">
          <RucSeal :size="60" tone="light" />
          <view class="hero-brand-text">
            <text class="hero-brand-cn">中国人民大学</text>
            <text class="hero-brand-sub">信息学院 · 学院综合服务</text>
          </view>
        </view>
        <view class="notify" @click="goNotify">
          <view class="bell-icon">
            <view class="bell-body" />
            <view class="bell-rim" />
            <view class="bell-clapper" />
          </view>
          <view v-if="unreadCount > 0" class="notify-dot" />
        </view>
      </view>

      <!-- 第二行: 问候语 -->
      <view class="hero-main">
        <text class="greeting">{{ greeting }}，{{ userStore.name || '同学' }}</text>
        <view class="greeting-underline" />
        <text class="school">学生服务、党团事务与证明审批统一入口</text>
      </view>
    </view>

    <!-- ===== 智能问答搜索式入口 (悬浮于 hero 之上) ===== -->
    <view class="ask-bar" @click="goQa">
      <view class="ask-icon">
        <view class="magnifier">
          <view class="magnifier-glass" />
          <view class="magnifier-handle" />
        </view>
      </view>
      <text class="ask-placeholder">有问题？问问学院智能助手…</text>
      <view class="ask-go">提问</view>
    </view>

    <!-- ===== 我的事项速览 ===== -->
    <view v-if="overview.show" class="glance mp-card">
      <view class="glance-item" @click="switchTo('/pages/approval/index')">
        <view class="glance-num-row">
          <text class="glance-num" :class="{ warn: overview.pendingCount > 0 }">{{ overview.pendingCount }}</text>
          <text class="glance-unit">件</text>
        </view>
        <text class="glance-label">审批中的申请</text>
      </view>
      <view class="glance-divider" />
      <view class="glance-item glance-item--wide" @click="goParty">
        <template v-if="overview.party">
          <view class="glance-progress-row">
            <text class="glance-party-name">{{ overview.party.templateName || '党团流程' }}</text>
            <text class="glance-party-step">{{ overview.party.currentStep || 1 }}/{{ overview.party.totalSteps || '-' }}</text>
          </view>
          <view class="glance-bar">
            <view class="glance-fill" :style="{ width: overview.party.percent + '%' }" />
          </view>
          <text class="glance-label">党团进度 · 点击查看节点</text>
        </template>
        <template v-else>
          <text class="glance-empty-title">暂无党团流程</text>
          <text class="glance-label">可查阅官方流程模板</text>
        </template>
      </view>
    </view>

    <!-- ===== 常用服务 ===== -->
    <SectionTitle title="常用服务" />
    <view class="service-list">
      <ServiceCard
        v-for="item in menuItems"
        :key="item.url"
        :title="item.label"
        :desc="item.desc"
        :icon-src="item.iconSrc"
        :badge="item.badge"
        :tone="item.tone"
        @click="navigateTo(item.url)"
      />
    </view>

    <!-- ===== 最新通知 ===== -->
    <SectionTitle title="最新通知">
      <text v-if="notifications.length" class="section-action" @click="goNotify">查看全部</text>
    </SectionTitle>
    <view class="notice-panel mp-card">
      <view v-if="notifications.length" class="notify-list">
        <view v-for="n in notifications" :key="n.id" class="notice-item" @click="goNotify">
          <view class="notice-dot" />
          <view class="notice-content">
            <text class="notice-title">{{ n.title }}</text>
            <text class="notice-summary">{{ n.content }}</text>
            <text class="notice-time">{{ formatTime(n.createdAt) }}</text>
          </view>
        </view>
      </view>
      <EmptyState v-else title="暂无通知" description="审批提醒和系统消息会显示在这里。" />
    </view>

    <view class="page-footer">
      <view class="footer-line" />
      <text class="footer-text">中国人民大学 · 信息学院</text>
      <view class="footer-line" />
    </view>
  </view>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { notifyApi, approvalApi, partyApi } from '@/api'
import ServiceCard from '@/components/ServiceCard.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import RucSeal from '@/components/RucSeal.vue'

const userStore = useUserStore()
const unreadCount = ref(0)
const notifications = ref([])
const overview = reactive({ show: false, pendingCount: 0, party: null })

const greeting = (() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})()

const menuItems = [
  {
    label: '智能问答',
    desc: '政策制度、报到入学、党团事务即时咨询',
    iconSrc: '/static/icons/icon-qa.svg',
    badge: 'RAG',
    tone: 'red',
    url: '/pages/qa/index',
  },
  {
    label: '文件与模板',
    desc: '政策文件、请假条、活动预算等模板下载',
    iconSrc: '/static/icons/icon-doc.svg',
    badge: '资源',
    tone: 'blue',
    url: '/pages/qa/document',
  },
  {
    label: '党团进度',
    desc: '查看个人流程节点、材料与完成状态',
    iconSrc: '/static/icons/icon-party.svg',
    badge: '流程',
    tone: 'green',
    url: '/pages/party/index',
  },
  {
    label: '我的申请',
    desc: '证明申请、审批流转与下载归档',
    iconSrc: '/static/icons/icon-approval.svg',
    badge: '审批',
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

function switchTo(url) {
  uni.switchTab({ url })
}

function goNotify() {
  uni.navigateTo({ url: '/pages/notify/index' })
}

function goQa() {
  uni.switchTab({ url: '/pages/qa/index' })
}

function goParty() {
  uni.navigateTo({ url: '/pages/party/index' })
}

function formatTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})/)
  if (!match) return text.split('.')[0]
  const now = new Date()
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const prefix = now.getFullYear() === year ? `${month}-${day}` : `${year}-${month}-${day}`
  return `${prefix} ${match[4]}:${match[5]}`
}

// 事项速览: 审批中的申请数 + 进行中党团流程进度 (任一失败不阻塞)
async function loadOverview() {
  try {
    const [appRes, partyRes] = await Promise.all([
      approvalApi.getMyPage({ page: 1, size: 50 }).catch(() => null),
      partyApi.getMyProgress().catch(() => null),
    ])
    if (appRes) {
      const records = appRes.data?.records || []
      overview.pendingCount = records.filter((r) => r.status === 'pending').length
    }
    const progressList = partyRes?.data || []
    const current = progressList.find((p) => p.status === 'active') || progressList[0]
    if (current) {
      const totalSteps = current.steps?.length || current.totalSteps || 0
      const step = Math.min(current.currentStep || 1, totalSteps || 1)
      overview.party = {
        templateName: current.templateName,
        currentStep: step,
        totalSteps: totalSteps || '-',
        percent: totalSteps ? Math.round((step / totalSteps) * 100) : 0,
      }
    }
    overview.show = true
  } catch (e) {
    overview.show = true
  }
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loadOverview()
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
}

/* ===== Hero ===== */
.hero {
  padding: 34rpx 30rpx 88rpx;
  box-sizing: border-box;
}

/* 第一行: 品牌 + 铃铛 */
.hero-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}

.hero-brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.hero-brand-cn {
  color: #fff;
  font-family: var(--mp-font-display);
  font-size: 30rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
}

.hero-brand-sub {
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 19rpx;
  letter-spacing: 2rpx;
}

/* 第二行: 问候语 */
.hero-main {
  position: relative;
  z-index: 1;
  margin-top: 34rpx;
  min-width: 0;
}

.greeting {
  display: block;
  color: #fff;
  font-family: var(--mp-font-display);
  font-size: 46rpx;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: 3rpx;
}

/* 鎏金短下划线 */
.greeting-underline {
  width: 72rpx;
  height: 7rpx;
  margin-top: 16rpx;
  border-radius: 4rpx;
  background: linear-gradient(90deg, var(--mp-gold), rgba(184, 146, 62, 0.2));
}

.school {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 23rpx;
  line-height: 1.45;
}

.notify {
  flex-shrink: 0;
  position: relative;
  width: 66rpx;
  height: 66rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.15);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  color: #fff;
}

.bell-icon {
  position: relative;
  width: 36rpx;
  height: 36rpx;
}

.bell-body {
  position: absolute;
  top: 4rpx;
  left: 6rpx;
  right: 6rpx;
  bottom: 10rpx;
  background: #fff;
  border-radius: 12rpx 12rpx 4rpx 4rpx;
}

.bell-rim {
  position: absolute;
  left: 2rpx;
  right: 2rpx;
  bottom: 7rpx;
  height: 4rpx;
  background: #fff;
  border-radius: 2rpx;
}

.bell-clapper {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #fff;
}

.notify-dot {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #F0B35A;
  border: 2rpx solid #9D2235;
  box-sizing: content-box;
}

/* ===== 问答搜索式入口 ===== */
.ask-bar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 16rpx;
  height: 92rpx;
  margin: -46rpx 18rpx 22rpx;
  padding: 0 12rpx 0 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: var(--mp-shadow);
  transition: transform 0.15s ease;
}

.ask-bar:active {
  transform: scale(0.98);
}

.ask-icon {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--mp-primary-light);
}

/* CSS 放大镜 */
.magnifier {
  position: relative;
  width: 30rpx;
  height: 30rpx;
}

.magnifier-glass {
  position: absolute;
  top: 0;
  left: 0;
  width: 22rpx;
  height: 22rpx;
  border: 4rpx solid var(--mp-primary);
  border-radius: 50%;
  box-sizing: border-box;
}

.magnifier-handle {
  position: absolute;
  right: 1rpx;
  bottom: 1rpx;
  width: 11rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: var(--mp-primary);
  transform: rotate(45deg);
}

.ask-placeholder {
  flex: 1;
  min-width: 0;
  color: var(--mp-text-sub);
  font-size: 25rpx;
}

.ask-go {
  flex-shrink: 0;
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 30rpx;
  border-radius: 999rpx;
  color: #fff;
  background: var(--mp-red-gradient);
  box-shadow: 0 8rpx 18rpx rgba(157, 34, 53, 0.28);
  font-size: 24rpx;
  font-weight: 700;
}

/* ===== 事项速览 ===== */
.glance {
  display: flex;
  align-items: stretch;
  margin-bottom: 28rpx;
  padding: 22rpx 18rpx;
}

.glance-item {
  flex: 0 0 200rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 6rpx 14rpx;
}

.glance-item--wide {
  flex: 1;
  min-width: 0;
}

.glance-divider {
  width: 1rpx;
  margin: 8rpx 6rpx;
  background: rgba(35, 31, 32, 0.08);
}

.glance-num-row {
  display: flex;
  align-items: baseline;
  gap: 6rpx;
}

.glance-num {
  color: var(--mp-text-main);
  font-size: 44rpx;
  font-weight: 800;
  line-height: 1.1;
}

.glance-num.warn {
  color: var(--mp-warning);
}

.glance-unit {
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.glance-label {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 21rpx;
}

.glance-progress-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.glance-party-name {
  color: var(--mp-text-main);
  font-size: 26rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.glance-party-step {
  flex-shrink: 0;
  color: var(--mp-primary);
  font-size: 23rpx;
  font-weight: 700;
}

.glance-bar {
  height: 10rpx;
  margin-top: 12rpx;
  border-radius: 999rpx;
  background: #F0EDE9;
  overflow: hidden;
}

.glance-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--mp-primary), var(--mp-gold));
}

.glance-empty-title {
  color: var(--mp-text-regular);
  font-size: 25rpx;
  font-weight: 600;
}

/* ===== 服务列表 ===== */
.service-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.section-action {
  color: var(--mp-primary);
  font-size: 23rpx;
  font-weight: 500;
}

/* ===== 通知 ===== */
.notice-panel {
  min-height: 210rpx;
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
  font-weight: 750;
}

.notice-summary {
  display: block;
  margin-top: 6rpx;
  color: var(--mp-text-regular);
  font-size: 23rpx;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-time {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

/* ===== 页脚 ===== */
.page-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  margin-top: 40rpx;
  padding-bottom: 10rpx;
}

.footer-line {
  width: 48rpx;
  height: 1rpx;
  background: rgba(157, 34, 53, 0.25);
}

.footer-text {
  font-family: var(--mp-font-display);
  color: var(--mp-text-muted);
  font-size: 22rpx;
  letter-spacing: 4rpx;
}
</style>
