<template>
  <view class="page">
    <view class="app-item" :class="app.status" v-for="app in applicationList" :key="app.id" @click="viewDetail(app.id)">
      <view class="app-header">
        <text class="app-no">{{ app.appNo }}</text>
        <text class="app-status" :class="app.status">{{ statusLabel(app.status) }}</text>
      </view>
      <text class="app-time">{{ app.createdAt }}</text>
      <text class="reject-reason" v-if="app.status === 'rejected' && app.rejectReason">原因：{{ app.rejectReason }}</text>
      <view class="app-actions" v-if="app.status === 'approved'">
        <button class="btn-sm btn-download" @click.stop="handleDownload(app.id)">下载证明</button>
        <button class="btn-sm btn-withdraw" @click.stop="handleWithdraw(app.id)">撤回</button>
      </view>
      <view class="app-actions" v-else-if="app.status === 'pending'">
        <button class="btn-sm btn-withdraw" @click.stop="handleWithdraw(app.id)">撤回</button>
      </view>
      <view class="app-actions" v-else-if="app.status === 'downloaded'">
        <button class="btn-sm btn-archived" disabled>已归档</button>
      </view>
      <view class="locked-tag" v-if="app.status === 'downloaded'">已锁定归档</view>
    </view>

    <view class="empty" v-if="!applicationList.length">暂无申请记录</view>

    <button class="btn-apply" @click="goApply">提交新申请</button>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { approvalApi } from '@/api'

const applicationList = ref([])

const statusMap = {
  draft: '草稿', pending: '待审批', approved: '已通过',
  rejected: '已驳回', withdrawn: '已撤回', downloaded: '已锁定',
}
const statusLabel = (s) => statusMap[s] || s

onMounted(loadApplications)

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
      `状态：${statusLabel(app.status)}`,
      `提交时间：${app.createdAt || '-'}`,
      latestRecord?.comment ? `审批意见：${latestRecord.comment}` : '',
    ].filter(Boolean)
    uni.showModal({ title: '申请详情', content: lines.join('\n'), showCancel: false })
  } catch (e) { /* handled */ }
}

async function handleDownload(id) {
  const target = applicationList.value.find((x) => x.id === id)
  if (!target || target.status !== 'approved') return

  uni.showModal({
    title: '提示',
    content: '下载后申请将归档锁定，不可再撤回或修改，确定吗？',
    success: (res) => {
      if (!res.confirm) return
      downloadFile(id)
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
  if (!target || !['pending', 'approved'].includes(target.status)) return

  uni.showModal({
    title: '确认撤回',
    content: '确定撤回该申请？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await approvalApi.withdraw(id)
        uni.showToast({ title: '已撤回' })
        loadApplications()
      } catch (e) { /* handled */ }
    },
  })
}

function goApply() {
  uni.navigateTo({ url: '/pages/approval/apply' })
}
</script>

<style scoped>
.page { padding: 20rpx; }
.app-item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.app-item.downloaded { background: #f4f4f5; }
.app-header { display: flex; justify-content: space-between; align-items: center; }
.app-no { font-size: 26rpx; font-weight: bold; }
.app-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 4rpx; }
.app-status.pending { color: #e6a23c; background: #fdf6ec; }
.app-status.approved { color: #67c23a; background: #f0f9eb; }
.app-status.rejected { color: #f56c6c; background: #fef0f0; }
.app-status.downloaded { color: #909399; background: #f4f4f5; }
.app-status.withdrawn { color: #909399; background: #f4f4f5; }
.app-time { font-size: 22rpx; color: #999; margin-top: 8rpx; display: block; }
.reject-reason { font-size: 22rpx; color: #f56c6c; margin-top: 6rpx; display: block; }
.app-actions { margin-top: 16rpx; display: flex; gap: 16rpx; justify-content: flex-end; }
.btn-sm { font-size: 24rpx; padding: 8rpx 24rpx; border-radius: 6rpx; border: none; }
.btn-download { background: #1a3a5c; color: #fff; }
.btn-withdraw { background: #fff; color: #e6a23c; border: 1rpx solid #e6a23c; }
.btn-archived { background: #e5e7eb; color: #909399; border: none; }
.locked-tag { margin-top: 12rpx; font-size: 22rpx; color: #909399; }
.empty { text-align: center; color: #999; padding: 80rpx; }
.btn-apply { background: #1a3a5c; color: #fff; border: none; border-radius: 8rpx; margin-top: 20rpx; font-size: 30rpx; }
</style>
