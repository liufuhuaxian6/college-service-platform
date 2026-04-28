<template>
  <view class="page">
    <view class="app-item" v-for="app in list" :key="app.id" @click="viewDetail(app.id)">
      <view class="app-header">
        <text class="app-no">{{ app.appNo }}</text>
        <text class="app-status" :class="app.status">{{ statusLabel(app.status) }}</text>
      </view>
      <text class="app-time">{{ app.createdAt }}</text>
      <view class="app-actions" v-if="app.status === 'approved' && !app.downloadedAt">
        <button class="btn-sm btn-download" @click.stop="handleDownload(app.id)">下载证明</button>
      </view>
      <view class="app-actions" v-if="app.status === 'pending'">
        <button class="btn-sm btn-withdraw" @click.stop="handleWithdraw(app.id)">撤回</button>
      </view>
      <view class="locked-tag" v-if="app.status === 'downloaded'">已锁定归档</view>
    </view>

    <view class="empty" v-if="!list.length">暂无申请记录</view>

    <button class="btn-apply" @click="goApply">提交新申请</button>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { approvalApi } from '@/api'

const list = ref([])

const statusMap = {
  draft: '草稿', pending: '待审批', approved: '已通过',
  rejected: '已驳回', withdrawn: '已撤回', downloaded: '已锁定',
}
const statusLabel = (s) => statusMap[s] || s

onMounted(async () => {
  const res = await approvalApi.getMyPage({ page: 1, size: 50 })
  list.value = res.data?.records || []
})

function viewDetail(id) {
  // TODO: 详情页
}

async function handleDownload(id) {
  uni.showModal({
    title: '提示',
    content: '下载后证明将被锁定，不可再撤回。确认下载？',
    success: async (res) => {
      if (res.confirm) {
        await approvalApi.download(id)
        uni.showToast({ title: '下载成功，已锁定', icon: 'success' })
        // 刷新列表
        const r = await approvalApi.getMyPage({ page: 1, size: 50 })
        list.value = r.data?.records || []
      }
    },
  })
}

async function handleWithdraw(id) {
  uni.showModal({
    title: '确认撤回',
    content: '确定撤回该申请？',
    success: async (res) => {
      if (res.confirm) {
        await approvalApi.withdraw(id)
        uni.showToast({ title: '已撤回' })
        const r = await approvalApi.getMyPage({ page: 1, size: 50 })
        list.value = r.data?.records || []
      }
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
.app-header { display: flex; justify-content: space-between; align-items: center; }
.app-no { font-size: 26rpx; font-weight: bold; }
.app-status { font-size: 24rpx; padding: 4rpx 16rpx; border-radius: 4rpx; }
.app-status.pending { color: #e6a23c; background: #fdf6ec; }
.app-status.approved { color: #67c23a; background: #f0f9eb; }
.app-status.rejected { color: #f56c6c; background: #fef0f0; }
.app-status.downloaded { color: #909399; background: #f4f4f5; }
.app-status.withdrawn { color: #909399; background: #f4f4f5; }
.app-time { font-size: 22rpx; color: #999; margin-top: 8rpx; display: block; }
.app-actions { margin-top: 16rpx; display: flex; gap: 16rpx; }
.btn-sm { font-size: 24rpx; padding: 8rpx 24rpx; border-radius: 6rpx; border: none; }
.btn-download { background: #1a3a5c; color: #fff; }
.btn-withdraw { background: #fff; color: #e6a23c; border: 1rpx solid #e6a23c; }
.locked-tag { margin-top: 12rpx; font-size: 22rpx; color: #909399; }
.empty { text-align: center; color: #999; padding: 80rpx; }
.btn-apply { background: #1a3a5c; color: #fff; border: none; border-radius: 8rpx; margin-top: 20rpx; font-size: 30rpx; }
</style>
