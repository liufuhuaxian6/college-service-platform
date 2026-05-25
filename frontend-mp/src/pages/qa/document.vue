<template>
  <view class="page">
    <view class="doc-item" v-for="doc in list" :key="doc.id" @click="download(doc)">
      <view class="doc-info">
        <text class="doc-title">{{ doc.title }}</text>
        <text class="doc-meta">{{ doc.category }} · {{ formatSize(doc.fileSize) }} · {{ doc.downloadCount }}次下载</text>
      </view>
      <text class="download-icon">⬇️</text>
    </view>
    <view class="empty" v-if="!list.length">暂无政策文档</view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { BASE_URL, qaApi } from '@/api'

const list = ref([])

onMounted(async () => {
  const res = await qaApi.getDocumentList({})
  list.value = res.data || []
})

function download(doc) {
  const token = uni.getStorageSync('token') || ''
  uni.showLoading({ title: '下载中' })
  uni.downloadFile({
    url: `${BASE_URL}/qa/document/${doc.id}/download`,
    header: token ? { Authorization: `Bearer ${token}` } : {},
    success: (res) => {
      if (res.statusCode !== 200) {
        uni.showToast({ title: '下载失败', icon: 'none' })
        return
      }
      uni.openDocument({
        filePath: res.tempFilePath,
        showMenu: true,
        fail: () => {
          uni.showToast({ title: '打开文件失败', icon: 'none' })
        },
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

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}
</script>

<style scoped>
.page { padding: 20rpx; }
.doc-item { display: flex; justify-content: space-between; align-items: center; background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 16rpx; }
.doc-title { font-size: 28rpx; font-weight: bold; display: block; }
.doc-meta { font-size: 22rpx; color: #999; margin-top: 8rpx; display: block; }
.download-icon { font-size: 36rpx; }
.empty { text-align: center; color: #999; padding: 80rpx; }
</style>
