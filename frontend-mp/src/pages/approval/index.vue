<template>
  <view class="page">
    <view class="hero-card">
      <view>
        <text class="hero-eyebrow">证明审批</text>
        <text class="hero-title">我的申请</text>
        <text class="hero-desc">查看证明申请、审批进度与下载归档状态</text>
      </view>
      <view class="hero-count">
        <text class="count-value">{{ applicationList.length }}</text>
        <text class="count-label">条记录</text>
      </view>
    </view>

    <view class="stats-card">
      <view class="stat-item">
        <text class="stat-value">{{ pendingCount }}</text>
        <text class="stat-label">待审批</text>
      </view>
      <view class="stat-item">
        <text class="stat-value">{{ approvedCount }}</text>
        <text class="stat-label">可下载</text>
      </view>
      <view class="stat-item">
        <text class="stat-value">{{ lockedCount }}</text>
        <text class="stat-label">已归档</text>
      </view>
    </view>

    <view class="notice-strip">
      <text class="notice-dot">!</text>
      <text class="notice-text">证明下载后将锁定归档，锁定后不能撤回或重新审批。</text>
    </view>

    <view class="list-header">
      <text class="section-title">申请记录</text>
      <text class="section-action" @click="loadApplications">刷新</text>
    </view>

    <view
      v-for="app in applicationList"
      :key="app.id"
      class="app-card"
      :class="`app-card--${normalizedStatus(app)}`"
      @click="viewDetail(app.id)"
    >
      <view class="app-top">
        <view class="app-main">
          <text class="app-title">{{ app.typeName || '证明申请' }}</text>
          <text class="app-no">{{ app.appNo || '暂无编号' }}</text>
        </view>
        <StatusPill :status="normalizedStatus(app)" />
      </view>

      <view class="meta-grid">
        <view class="meta-item">
          <text class="meta-label">提交时间</text>
          <text class="meta-value">{{ app.createdAt || '-' }}</text>
        </view>
        <view class="meta-item">
          <text class="meta-label">当前状态</text>
          <text class="meta-value">{{ statusLabel(normalizedStatus(app)) }}</text>
        </view>
      </view>

      <view v-if="app.status === 'rejected' && app.rejectReason" class="reason-box">
        <text>驳回原因：{{ app.rejectReason }}</text>
      </view>
      <view v-else-if="isLocked(app)" class="state-box locked">
        <text>已下载并锁定归档，禁止撤回或重新审批。</text>
      </view>
      <view v-else-if="app.status === 'approved'" class="state-box">
        <text>审批已通过，下载后将自动锁定归档。</text>
      </view>

      <view class="app-actions" v-if="app.status === 'approved' || app.status === 'pending' || isLocked(app)">
        <view
          v-if="app.status === 'approved' && !isLocked(app)"
          class="action-btn primary"
          @click.stop="handleDownload(app.id)"
        >
          下载证明
        </view>
        <view
          v-if="canWithdraw(app)"
          class="action-btn ghost"
          @click.stop="handleWithdraw(app.id)"
        >
          撤回重批
        </view>
        <view v-if="isLocked(app)" class="action-btn disabled">锁定归档</view>
      </view>
    </view>

    <EmptyState
      v-if="!applicationList.length"
      title="暂无申请记录"
      description="点击下方按钮提交新的证明申请。"
    />

    <view class="footer">
      <view class="btn-apply" @click="goApply">提交新申请</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { approvalApi } from '@/api'
import StatusPill from '@/components/StatusPill.vue'
import EmptyState from '@/components/EmptyState.vue'

const applicationList = ref([])

const statusMap = {
  draft: '草稿',
  pending: '待审批',
  approved: '已通过',
  rejected: '已驳回',
  withdrawn: '已撤回',
  downloaded: '已锁定归档',
}

const pendingCount = computed(() => applicationList.value.filter((item) => item.status === 'pending').length)
const approvedCount = computed(() => applicationList.value.filter((item) => item.status === 'approved' && !isLocked(item)).length)
const lockedCount = computed(() => applicationList.value.filter((item) => isLocked(item)).length)

onMounted(loadApplications)

function isLocked(app) {
  return app?.status === 'downloaded' || !!app?.downloadedAt
}

function normalizedStatus(app) {
  return isLocked(app) ? 'downloaded' : app?.status
}

function canWithdraw(app) {
  return ['pending', 'approved'].includes(app?.status) && !isLocked(app)
}

function statusLabel(status) {
  return statusMap[status] || status || '-'
}

async function loadApplications() {
  try {
    const res = await approvalApi.getMyPage({ page: 1, size: 50 })
    applicationList.value = res.data?.records || []
  } catch (e) {
    applicationList.value = []
  }
}

async function viewDetail(id) {
  try {
    const res = await approvalApi.getMyDetail(id)
    const app = res.data?.application || res.data
    const records = res.data?.records || []
    const latestRecord = records.length ? records[records.length - 1] : null
    const lines = [
      `编号：${app.appNo || '-'}`,
      `状态：${statusLabel(normalizedStatus(app))}`,
      `提交时间：${app.createdAt || '-'}`,
      isLocked(app) ? '该申请已下载并锁定归档，不能撤回或重新审批。' : '',
      latestRecord?.comment ? `审批意见：${latestRecord.comment}` : '',
    ].filter(Boolean)
    uni.showModal({ title: '申请详情', content: lines.join('\n'), showCancel: false })
  } catch (e) {
    // request helper already shows the error toast
  }
}

async function handleDownload(id) {
  const target = applicationList.value.find((x) => x.id === id)
  if (!target || target.status !== 'approved' || isLocked(target)) return

  uni.showModal({
    title: '确认下载',
    content: '下载后申请将归档锁定，不能再撤回或修改，确定下载吗？',
    success: (res) => {
      if (res.confirm) downloadFile(id)
    },
  })
}

function downloadFile(id) {
  const token = uni.getStorageSync('token') || ''
  uni.showLoading({ title: '下载中' })
  uni.downloadFile({
    url: approvalApi.downloadFileUrl(id),
    header: token ? { Authorization: `Bearer ${token}` } : {},
    success: (res) => {
      if (res.statusCode !== 200) {
        uni.showToast({ title: '下载失败', icon: 'none' })
        return
      }
      uni.openDocument({
        filePath: res.tempFilePath,
        showMenu: true,
        complete: loadApplications,
      })
    },
    fail: () => {
      uni.showToast({ title: '下载失败', icon: 'none' })
    },
    complete: () => {
      uni.hideLoading()
    },
  })
}

async function handleWithdraw(id) {
  const target = applicationList.value.find((x) => x.id === id)
  if (!canWithdraw(target)) return

  uni.showModal({
    title: '确认撤回',
    content: target.status === 'approved' ? '撤回后需要重新提交审批，确定继续吗？' : '确定撤回该申请吗？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await approvalApi.withdraw(id)
        uni.showToast({ title: '已撤回' })
        loadApplications()
      } catch (e) {
        // request helper already shows the error toast
      }
    },
  })
}

function goApply() {
  uni.navigateTo({ url: '/pages/approval/apply' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 24rpx calc(132rpx + env(safe-area-inset-bottom));
  background:
    radial-gradient(circle at 15% 0%, rgba(155, 44, 54, 0.1), transparent 34%),
    linear-gradient(180deg, #FBF7F5 0%, var(--mp-bg) 38%, var(--mp-bg) 100%);
  box-sizing: border-box;
}

.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 30rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #9B2C36 0%, #7E2630 100%);
  box-shadow: 0 16rpx 38rpx rgba(155, 44, 54, 0.2);
}

.hero-eyebrow {
  display: inline-flex;
  margin-bottom: 16rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.85);
  font-size: 21rpx;
}

.hero-title {
  display: block;
  color: #fff;
  font-size: 38rpx;
  font-weight: 800;
}

.hero-desc {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.76);
  font-size: 23rpx;
}

.hero-count {
  width: 96rpx;
  height: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.14);
}

.count-value {
  color: #fff;
  font-size: 34rpx;
  font-weight: 800;
}

.count-label {
  color: rgba(255, 255, 255, 0.72);
  font-size: 19rpx;
}

.stats-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  margin: -24rpx 18rpx 22rpx;
  padding: 18rpx;
  position: relative;
  z-index: 2;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--mp-shadow);
}

.stat-item {
  text-align: center;
  padding: 12rpx 4rpx;
}

.stat-value {
  display: block;
  color: var(--mp-text-main);
  font-size: 32rpx;
  font-weight: 800;
}

.stat-label {
  display: block;
  margin-top: 6rpx;
  color: var(--mp-text-sub);
  font-size: 21rpx;
}

.notice-strip {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 24rpx;
  padding: 18rpx 20rpx;
  border-radius: 18rpx;
  background: #FFF7E8;
  border: 1rpx solid #F3D6A2;
}

.notice-dot {
  width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  border-radius: 50%;
  background: #9A6A16;
  color: #fff;
  font-size: 21rpx;
  font-weight: 800;
}

.notice-text {
  flex: 1;
  color: #765112;
  font-size: 22rpx;
  line-height: 1.5;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4rpx 0 16rpx;
}

.section-title {
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 800;
}

.section-action {
  color: #9B2C36;
  font-size: 23rpx;
}

.app-card {
  margin-bottom: 18rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  box-shadow: 0 10rpx 26rpx rgba(31, 35, 41, 0.05);
}

.app-card--downloaded {
  background: #F7F8FA;
}

.app-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.app-main {
  min-width: 0;
}

.app-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 800;
}

.app-no {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 22rpx;
  padding: 18rpx;
  border-radius: 16rpx;
  background: #F8F9FA;
}

.meta-label {
  display: block;
  color: var(--mp-text-sub);
  font-size: 20rpx;
}

.meta-value {
  display: block;
  margin-top: 6rpx;
  color: var(--mp-text-regular);
  font-size: 22rpx;
  line-height: 1.4;
}

.reason-box,
.state-box {
  margin-top: 18rpx;
  padding: 16rpx;
  border-radius: 14rpx;
  color: #765112;
  background: #FFF7E8;
  font-size: 22rpx;
  line-height: 1.5;
}

.reason-box {
  color: #B4232C;
  background: #FBECEC;
}

.state-box.locked {
  color: #5B6472;
  background: #F2F3F5;
}

.app-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14rpx;
  margin-top: 20rpx;
}

.action-btn {
  min-width: 144rpx;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  border-radius: 16rpx;
  font-size: 24rpx;
  font-weight: 650;
}

.action-btn.primary {
  background: #9B2C36;
  color: #fff;
}

.action-btn.ghost {
  background: #fff;
  color: #9A6A16;
  border: 1rpx solid #E7C27F;
}

.action-btn.disabled {
  background: #E5E7EB;
  color: #5B6472;
}

.footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid rgba(31, 35, 41, 0.06);
  box-sizing: border-box;
}

.btn-apply {
  width: 100%;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 24rpx;
  background: #9B2C36;
  color: #fff;
  font-size: 30rpx;
  font-weight: 800;
  box-shadow: 0 10rpx 24rpx rgba(155, 44, 54, 0.22);
}
</style>
