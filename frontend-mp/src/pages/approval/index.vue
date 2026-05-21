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
    <text class="dev-mock-btn" @click="loadMockData">加载测试数据</text><!-- // @UI_DEV_ONLY -->
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

onMounted(async () => {
  if (applicationList.value.length) return
  try {
    const res = await approvalApi.getMyPage({ page: 1, size: 50 })
    applicationList.value = res.data?.records || []
  } catch (e) {
    applicationList.value = []
  }
})

function formatDate(offsetDays) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function loadMockData() { // @UI_DEV_ONLY
  applicationList.value = [ // @UI_DEV_ONLY
    { id: 1, appNo: '在读证明', status: 'pending', createdAt: formatDate(0) }, // @UI_DEV_ONLY
    { id: 2, appNo: '成绩证明', status: 'approved', createdAt: formatDate(-1) }, // @UI_DEV_ONLY
    { id: 3, appNo: '离校证明', status: 'rejected', rejectReason: '材料不全', createdAt: formatDate(-2) }, // @UI_DEV_ONLY
  ] // @UI_DEV_ONLY
} // @UI_DEV_ONLY

function viewDetail(id) {
  // TODO: 详情页
}

async function handleDownload(id) {
  const target = applicationList.value.find((x) => x.id === id)
  if (!target || target.status !== 'approved') return

  uni.showModal({
    title: '提示',
    content: '下载后申请将归档锁定，不可再撤回或修改，确定吗？',
    success: (res) => {
      if (!res.confirm) return
      target.status = 'downloaded'
      target.downloadedAt = formatDate(0)
      uni.showToast({ title: '已归档锁定', icon: 'success' })
    },
  })
}

async function handleWithdraw(id) {
  const target = applicationList.value.find((x) => x.id === id)
  if (!target || !['pending', 'approved'].includes(target.status)) return

  uni.showModal({
    title: '确认撤回',
    content: '确定撤回该申请？',
    success: (res) => {
      if (!res.confirm) return
      target.status = 'withdrawn'
      uni.showToast({ title: '已撤回' })
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
.dev-mock-btn { /* // @UI_DEV_ONLY */
  display: block; /* // @UI_DEV_ONLY */
  text-align: center; /* // @UI_DEV_ONLY */
  font-size: 20rpx; /* // @UI_DEV_ONLY */
  color: #c0c4cc; /* // @UI_DEV_ONLY */
  margin-top: 10rpx; /* // @UI_DEV_ONLY */
} /* // @UI_DEV_ONLY */
</style>
