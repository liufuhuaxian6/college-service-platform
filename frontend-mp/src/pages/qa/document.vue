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
        <text class="tab-label">{{ t.label }}</text>
        <text class="tab-count">{{ activeTab === t.value ? list.length : '' }}</text>
      </view>
    </view>

    <view class="doc-item" v-for="doc in list" :key="doc.id" @click="download(doc)">
      <view class="file-badge">
        <text class="file-ext">{{ fileExt(doc) }}</text>
      </view>

      <view class="doc-info">
        <text class="doc-title">{{ doc.title }}</text>
        <view class="doc-meta-row">
          <text v-if="doc.category" class="doc-chip">{{ doc.category }}</text>
          <text class="doc-meta">
            <text v-if="doc.filePath">{{ formatSize(doc.fileSize) }}</text>
            <text v-if="doc.filePath"> · {{ doc.downloadCount || 0 }} 次下载</text>
          </text>
        </view>
        <text v-if="doc.description && activeTab === 'template'" class="doc-desc">
          {{ doc.description }}
        </text>
      </view>

      <view v-if="isPlaceholder(doc)" class="badge-pending">待上线</view>
      <view v-else class="download-btn">
        <text class="download-arrow">↓</text>
      </view>
    </view>

    <EmptyState v-if="!loading && !list.length" :title="emptyText" />

    <view v-if="textPreview.visible" class="text-preview-mask" @click="closeTextPreview">
      <view class="text-preview-panel" @click.stop>
        <view class="text-preview-head">
          <text class="text-preview-title">{{ textPreview.title }}</text>
          <text class="text-preview-close" @click="closeTextPreview">×</text>
        </view>
        <scroll-view scroll-y class="text-preview-body">
          <text class="text-preview-content">{{ textPreview.content }}</text>
        </scroll-view>
      </view>
    </view>
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
const textPreview = ref({ visible: false, title: '', content: '' })

const emptyText = computed(() => activeTab.value === 'template' ? '暂无办公模板' : '暂无政策文档')

function isPlaceholder(doc) {
  return !doc.filePath
}

function fileExt(doc) {
  if (isPlaceholder(doc)) return '·'
  return (getActualExt(doc).toUpperCase() || 'FILE').slice(0, 4)
}

function getActualExt(doc) {
  // 优先从文件名/路径取真实扩展名 (fileType 多为 MIME, 直接截会得到 APPL/IMAG 乱码)
  const m = (doc.fileName || doc.filePath || doc.title || '').match(/\.([a-zA-Z0-9]+)$/)
  if (m) return m[1].toLowerCase()
  // 退而从 MIME 映射常见类型
  const type = (doc.fileType || '').toLowerCase()
  if (type.includes('pdf')) return 'pdf'
  if (type.includes('word') || type.includes('msword')) return type.includes('openxml') ? 'docx' : 'doc'
  if (type.includes('sheet') || type.includes('excel')) return type.includes('openxml') ? 'xlsx' : 'xls'
  if (type.includes('presentation') || type.includes('powerpoint')) return type.includes('openxml') ? 'pptx' : 'ppt'
  if (type.includes('png')) return 'png'
  if (type.includes('jpeg') || type.includes('jpg')) return 'jpg'
  if (type.includes('gif')) return 'gif'
  if (type.includes('webp')) return 'webp'
  if (type.includes('image')) return 'img'
  if (type.includes('text') || type.includes('json')) return 'txt'
  return ''
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
      const ext = getActualExt(doc)
      if (isImageExt(ext)) {
        previewImageFile(res.tempFilePath, ext)
        return
      }
      if (isTextExt(ext)) {
        previewTextFile(res.tempFilePath, doc)
        return
      }

      // uni.openDocument 支持: doc/docx/xls/xlsx/ppt/pptx/pdf
      const supported = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pdf']
      if (!supported.includes(ext)) {
        uni.showModal({
          title: '暂不支持预览',
          content: `当前小程序暂不支持直接预览 ${ext || '该格式'} 文件，请在 PC 管理端下载查看。`,
          showCancel: false,
        })
        return
      }

      uni.openDocument({
        filePath: res.tempFilePath,
        fileType: ext,
        showMenu: true,
        fail: (err) => {
          // 微信内嵌预览失败时, 仍保留临时文件并提示用户改成另存
          uni.showModal({
            title: '提示',
            content: `当前环境不支持直接预览 ${ext || '该格式'} 文件, 请点击右上 ··· 选择"用其他应用打开"或保存到手机.`,
            showCancel: false,
          })
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

function isImageExt(ext) {
  return ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'img'].includes(ext)
}

function isTextExt(ext) {
  return ['txt', 'text', 'md', 'csv', 'log', 'json'].includes(ext)
}

function previewImageFile(filePath, ext) {
  uni.previewImage({
    current: filePath,
    urls: [filePath],
    fail: () => {
      uni.showModal({
        title: '预览失败',
        content: `图片已下载，但当前环境无法直接预览 ${ext || '图片'} 文件。`,
        showCancel: false,
      })
    },
  })
}

function previewTextFile(filePath, doc) {
  const fs = typeof uni.getFileSystemManager === 'function' ? uni.getFileSystemManager() : null
  if (!fs) {
    uni.showModal({
      title: '暂不支持预览',
      content: '当前环境无法读取文本文件，请在 PC 管理端下载查看。',
      showCancel: false,
    })
    return
  }

  fs.readFile({
    filePath,
    encoding: 'utf8',
    success: (readRes) => {
      const content = String(readRes.data || '')
      textPreview.value = {
        visible: true,
        title: doc.title || doc.fileName || '文本预览',
        content: content || '文件内容为空',
      }
    },
    fail: () => {
      uni.showModal({
        title: '文本预览失败',
        content: '文件已下载，但无法按 UTF-8 文本读取。请确认文件编码，或在 PC 管理端下载查看。',
        showCancel: false,
      })
    },
  })
}

function closeTextPreview() {
  textPreview.value.visible = false
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
  padding: 20rpx 24rpx 42rpx;
  background:
    radial-gradient(circle at 22% 0%, rgba(157, 34, 53, 0.08), transparent 36%),
    linear-gradient(180deg, #FBF7F5 0%, var(--mp-bg) 320rpx, var(--mp-bg) 100%);
  box-sizing: border-box;
}

/* ===== Tab Bar ===== */
.tab-bar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 22rpx;
  padding: 8rpx;
  background: #fff;
  border-radius: 24rpx;
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  box-shadow: 0 8rpx 22rpx rgba(31, 35, 41, 0.04);
}

.tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 22rpx 0;
  font-size: 28rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
  color: var(--mp-text-sub);
  border-radius: 18rpx;
  transition: background 0.18s, color 0.18s;
}

.tab.active {
  background: var(--mp-red-gradient);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 6rpx 14rpx rgba(157, 34, 53, 0.28);
}

.tab-label {
  letter-spacing: 1rpx;
}

.tab-count {
  min-width: 34rpx;
  padding: 0 10rpx;
  font-size: 20rpx;
  color: inherit;
  opacity: 0.85;
}

.tab.active .tab-count {
  background: rgba(255, 255, 255, 0.22);
  border-radius: 16rpx;
}

/* ===== Doc Item ===== */
.doc-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
  padding: 22rpx;
  background: #fff;
  border: 1rpx solid rgba(35, 31, 32, 0.05);
  border-radius: 22rpx;
  box-shadow: 0 8rpx 22rpx rgba(35, 31, 32, 0.04);
  transition: transform 0.15s ease;
}

.doc-item:active {
  transform: scale(0.985);
}

/* 左侧文件类型徽标 (中性蓝灰底 + 扩展名文字, 与管理端一致) */
.file-badge {
  width: 84rpx;
  height: 96rpx;
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 12rpx;
  border-radius: 14rpx;
  position: relative;
  color: #fff;
  font-weight: 800;
  /* 折角效果 */
  background: linear-gradient(135deg, transparent 0 18rpx, #5B6472 18rpx);
}

.file-badge::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 18rpx;
  height: 18rpx;
  background: rgba(255, 255, 255, 0.5);
  border-bottom-left-radius: 6rpx;
}

.file-ext {
  font-size: 22rpx;
  letter-spacing: 1rpx;
  color: #fff;
}

/* 中部信息 */
.doc-info {
  min-width: 0;
  flex: 1;
}

.doc-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 29rpx;
  font-weight: 700;
  line-height: 1.4;
}

.doc-meta-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 10rpx;
}

.doc-chip {
  padding: 2rpx 14rpx;
  font-size: 20rpx;
  color: #9D2235;
  background: var(--mp-primary-light);
  border-radius: 18rpx;
}

.doc-meta {
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.doc-desc {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-muted);
  font-size: 22rpx;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 右侧下载按钮 */
.download-btn {
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--mp-primary-light);
  border: 1rpx solid rgba(157, 34, 53, 0.18);
}

.download-arrow {
  font-size: 32rpx;
  color: #9D2235;
  font-weight: 700;
  line-height: 1;
}

.badge-pending {
  flex-shrink: 0;
  padding: 8rpx 16rpx;
  font-size: 22rpx;
  color: #b97a00;
  background: #fff8e1;
  border: 1rpx solid #f3e0a3;
  border-radius: 24rpx;
}

.text-preview-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 24rpx;
  background: rgba(0, 0, 0, 0.42);
  box-sizing: border-box;
}

.text-preview-panel {
  width: 100%;
  max-height: 78vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 18rpx 44rpx rgba(0, 0, 0, 0.18);
}

.text-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 26rpx 30rpx 20rpx;
  border-bottom: 1rpx solid rgba(35, 31, 32, 0.08);
}

.text-preview-title {
  flex: 1;
  min-width: 0;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 750;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-preview-close {
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 50%;
  color: var(--mp-text-sub);
  background: var(--mp-bg-warm);
  font-size: 42rpx;
}

.text-preview-body {
  max-height: 62vh;
  padding: 26rpx 30rpx 34rpx;
  box-sizing: border-box;
}

.text-preview-content {
  color: var(--mp-text-regular);
  font-size: 25rpx;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
