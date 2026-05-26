<template>
  <view class="page">
    <view class="tab-bar">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: activeTab === t.value }"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
      </view>
    </view>

    <view class="doc-item" v-for="doc in list" :key="doc.id" @click="download(doc)">
      <view class="doc-info">
        <text class="doc-title">{{ doc.title }}</text>
        <text class="doc-meta">
          {{ doc.category }}
          <text v-if="doc.filePath"> · {{ formatSize(doc.fileSize) }}</text>
          <text v-if="doc.filePath"> · {{ doc.downloadCount }}次下载</text>
        </text>
        <text v-if="doc.description && activeTab === 'template'" class="doc-desc">
          {{ doc.description }}
        </text>
      </view>
      <view v-if="isPlaceholder(doc)" class="badge-pending">待上线</view>
      <text v-else class="download-icon">下</text>
    </view>

    <EmptyState v-if="!loading && !list.length" :title="emptyText" />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { BASE_URL, qaApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'

const tabs = [
  { value: 'policy', label: '政策文档' },
  { value: 'template', label: '办公模板' },
]
const activeTab = ref('policy')
const list = ref([])
const loading = ref(false)

const emptyText = computed(() => activeTab.value === 'template' ? '暂无办公模板' : '暂无政策文档')

function isPlaceholder(doc) {
  return !doc.filePath
}

async function loadData() {
  loading.value = true
  list.value = []
  try {
    const api = activeTab.value === 'template' ? qaApi.getTemplateList : qaApi.getDocumentList
    const res = await api({})
    list.value = res.data || []
  } catch (e) {
    /* api 层已处理错误提示 */
  } finally {
    loading.value = false
  }
}

function switchTab(v) {
  if (activeTab.value === v) return
  activeTab.value = v
  loadData()
}

function download(doc) {
  if (isPlaceholder(doc)) {
    uni.showToast({ title: '该模板尚未上线', icon: 'none' })
    return
  }
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

onMounted(loadData)
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: var(--mp-bg);
  box-sizing: border-box;
}

.tab-bar {
  display: flex;
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: var(--mp-radius);
  padding: 6rpx;
  margin-bottom: 20rpx;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 16rpx 0;
  font-size: 26rpx;
  color: var(--mp-text-sub);
  border-radius: calc(var(--mp-radius) - 4rpx);
  transition: background 0.15s, color 0.15s;
}

.tab.active {
  background: var(--mp-primary);
  color: #fff;
  font-weight: 600;
}

.doc-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
  margin-bottom: 16rpx;
  padding: 24rpx;
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: var(--mp-radius);
}

.doc-info {
  min-width: 0;
  flex: 1;
}

.doc-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 28rpx;
  font-weight: 650;
}

.doc-meta {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.doc-desc {
  display: block;
  margin-top: 6rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
  line-height: 1.5;
}

.download-icon {
  width: 54rpx;
  height: 54rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--mp-primary-light);
  color: var(--mp-primary);
  font-size: 24rpx;
  font-weight: 700;
}

.badge-pending {
  flex-shrink: 0;
  padding: 6rpx 14rpx;
  font-size: 22rpx;
  color: #b97a00;
  background: #fff8e1;
  border-radius: 24rpx;
}
</style>
