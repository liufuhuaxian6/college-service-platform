<template>
  <div class="app-page">
    <PageHeader title="政策文档" description="上传政策制度文件，并执行向量入库供智能问答检索。">
      <template #actions>
        <el-button type="primary" @click="showUploadDialog">上传文档</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable placeholder="全部" style="width: 170px" @change="handleSearch">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <!-- 文档卡片网格 -->
    <div v-loading="loading" class="doc-grid">
      <div v-for="row in list" :key="row.id" class="doc-card">
        <div class="doc-card__main">
          <span class="file-badge" :class="fileBadgeClass(row)">{{ fileExt(row) }}</span>
          <div class="doc-card__info">
            <h3 class="doc-title" :title="row.title">{{ row.title }}</h3>
            <div class="doc-meta">
              <span v-if="row.category" class="doc-chip">{{ row.category }}</span>
              <span class="doc-meta-text">{{ formatSize(row.fileSize) }} · {{ row.downloadCount || 0 }} 次下载</span>
            </div>
            <span class="doc-time">更新于 {{ formatDateTime(row.updatedAt || row.createdAt) }}</span>
          </div>
        </div>
        <div class="doc-card__actions">
          <el-button size="small" @click="download(row)">下载</el-button>
          <el-button size="small" type="primary" plain :loading="indexingId === row.id" @click="indexDocument(row)">
            向量入库
          </el-button>
          <el-popconfirm title="确定删除该文档吗？" @confirm="handleDelete(row.id)">
            <template #reference><el-button size="small" type="danger" plain>删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>

      <EmptyState
        v-if="!loading && !list.length"
        class="doc-empty"
        title="暂无政策文档"
        description="点击右上角「上传文档」, 上传后执行向量入库即可供学生端智能问答检索。"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="上传政策文档" width="540px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="文档标题" required>
          <el-input v-model="form.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload action="" :auto-upload="false" :limit="1" :on-change="handleFileChange" :on-remove="handleFileRemove" :file-list="fileList">
            <el-button>选择文件</el-button>
            <template #tip><div class="upload-tip">文件大小不超过 30MB</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { qaApi, fileApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const loading = ref(false)
const uploading = ref(false)
const indexingId = ref(null)
const dialogVisible = ref(false)
const list = ref([])
const fileList = ref([])
const uploadFile = ref(null)
const categories = ['党团流程', '学籍管理', '纪律处分', '校历安排', '入党', '入团', '奖学金', '日常事务', '其他']
const query = reactive({ category: '' })
const form = reactive({ title: '', category: '' })

function buildQueryParams() {
  const params = {}
  if (query.category) params.category = query.category
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await qaApi.getDocumentList(buildQueryParams())
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function showUploadDialog() {
  form.title = ''
  form.category = ''
  fileList.value = []
  uploadFile.value = null
  dialogVisible.value = true
}

function handleFileChange(file, files) {
  if (!file.raw) return
  if (file.raw.size > 30 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 30MB')
    fileList.value = []
    uploadFile.value = null
    return
  }
  fileList.value = files.slice(-1)
  uploadFile.value = file.raw
  if (!form.title) form.title = file.name.replace(/\.[^.]+$/, '')
}

function handleFileRemove() {
  fileList.value = []
  uploadFile.value = null
}

async function handleUpload() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!form.category) {
    ElMessage.warning('请选择分类')
    return
  }
  if (!uploadFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  const uploadFormData = new FormData()
  uploadFormData.append('file', uploadFile.value)
  uploading.value = true
  try {
    const uploadRes = await fileApi.upload(uploadFormData)
    const fileInfo = uploadRes.data
    await qaApi.addDocument({
      title: form.title.trim(),
      category: form.category,
      fileName: fileInfo.fileName,
      filePath: fileInfo.filePath,
      fileSize: fileInfo.fileSize,
      fileType: uploadFile.value.type || ''
    })
    ElMessage.success('上传成功')
    dialogVisible.value = false
    loadData()
  } finally {
    uploading.value = false
  }
}

async function download(row) {
  const userStore = useUserStore()
  if (!userStore.token) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  const res = await fetch(qaApi.getDocumentDownloadUrl(row.id), {
    method: 'GET',
    headers: { Authorization: `Bearer ${userStore.token}` },
  })
  if (res.status === 401) {
    userStore.logout()
    ElMessage.error('登录已过期，请重新登录')
    router.push('/login')
    return
  }
  if (!res.ok) {
    ElMessage.error('下载失败')
    return
  }
  const blob = await res.blob()
  const objectUrl = URL.createObjectURL(blob)
  const disposition = res.headers.get('content-disposition') || ''
  const filename = parseDownloadFilename(disposition) || `${row.title || 'document'}`
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(objectUrl)
  loadData()
}

async function handleDelete(id) {
  await qaApi.deleteDocument(id)
  ElMessage.success('删除成功')
  loadData()
}

async function indexDocument(row) {
  indexingId.value = row.id
  ElMessage.info('向量入库已开始, 大文件可能需要 1-5 分钟, 请勿关闭页面')
  try {
    const res = await qaApi.indexDocument(row.id)
    ElMessage.success(`向量入库完成，共 ${res.data?.chunks || 0} 个片段`)
  } finally {
    indexingId.value = null
  }
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

function fileExt(row) {
  const m = (row.fileName || row.filePath || '').match(/\.([a-zA-Z0-9]+)$/)
  return m ? m[1].toUpperCase().slice(0, 4) : 'DOC'
}

function fileBadgeClass(row) {
  const ext = fileExt(row).toLowerCase()
  if (ext === 'pdf') return 'badge-pdf'
  if (['doc', 'docx'].includes(ext)) return 'badge-doc'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'badge-xls'
  if (['ppt', 'pptx'].includes(ext)) return 'badge-ppt'
  if (['txt', 'md'].includes(ext)) return 'badge-txt'
  return 'badge-other'
}

function parseDownloadFilename(disposition) {
  if (!disposition) return ''
  const match = disposition.match(/filename\*\=UTF-8''([^;]+)/i)
  if (match && match[1]) {
    try { return decodeURIComponent(match[1]) } catch { return match[1] }
  }
  return ''
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.upload-tip {
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

/* ===== 文档卡片网格 ===== */
.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 14px;
  min-height: 150px;
}

.doc-empty {
  grid-column: 1 / -1;
}

.doc-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  transition: transform 0.2s var(--app-ease), box-shadow 0.2s var(--app-ease), border-color 0.2s var(--app-ease);

  &:hover {
    transform: translateY(-2px);
    border-color: var(--app-primary-soft);
    box-shadow: var(--app-shadow);
  }
}

.doc-card__main {
  display: flex;
  gap: 14px;
}

/* 文件类型徽标 (折角文件造型) */
.file-badge {
  flex: 0 0 auto;
  width: 46px;
  height: 54px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 7px;
  position: relative;
  border-radius: 8px;
  color: #fff;
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, transparent 0 10px, currentColor 10px);

  &::before {
    content: "";
    position: absolute;
    top: 0;
    right: 0;
    width: 10px;
    height: 10px;
    background: rgba(255, 255, 255, 0.45);
    border-bottom-left-radius: 4px;
  }
}

.badge-pdf { color: #C2453A; }
.badge-doc { color: #3568A8; }
.badge-xls { color: #2F7D55; }
.badge-ppt { color: #C77023; }
.badge-txt { color: #6E7681; }
.badge-other { color: #9D2235; }

.doc-card__info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  margin: 0;
  color: var(--app-text);
  font-size: 14.5px;
  font-weight: 650;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.doc-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 7px;
}

.doc-chip {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--app-primary-light);
  color: var(--app-primary);
  font-size: 12px;
}

.doc-meta-text {
  color: var(--app-text-secondary);
  font-size: 12.5px;
}

.doc-time {
  display: block;
  margin-top: 6px;
  color: var(--app-text-placeholder);
  font-size: 12px;
}

.doc-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border-light);
}
</style>
