<template>
  <div class="app-page">
    <PageHeader title="办公模板" description="提供请假条、活动预算、班会简报、党团证明等常用 Word/Excel 模板下载。占位模板可由管理员补传文件。">
      <template #actions>
        <el-button type="primary" @click="showUploadDialog">上传模板</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable placeholder="全部" style="width: 170px" @change="handleSearch">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.placeholder" clearable placeholder="全部" style="width: 150px" @change="handleSearch">
            <el-option label="已上线" :value="false" />
            <el-option label="占位待传" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <!-- 模板卡片网格: 占位待传卡片用虚线琥珀边提示 -->
    <div v-loading="loading" class="tpl-grid">
      <div
        v-for="row in filteredList"
        :key="row.id"
        class="tpl-card"
        :class="{ 'tpl-card--placeholder': isPlaceholder(row) }"
      >
        <div class="tpl-card__head">
          <span class="tpl-icon">
            <el-icon :size="19"><Document /></el-icon>
          </span>
          <div class="tpl-head-info">
            <h3 class="tpl-title" :title="row.title">{{ row.title }}</h3>
            <span v-if="row.category" class="tpl-chip">{{ row.category }}</span>
          </div>
          <StatusTag :status="isPlaceholder(row) ? 'placeholder' : 'online'" />
        </div>

        <p class="tpl-desc">{{ row.description || '暂无适用范围说明' }}</p>

        <div class="tpl-meta">
          <span>{{ row.filePath ? formatSize(row.fileSize) : '未上传文件' }}</span>
          <span>{{ row.downloadCount || 0 }} 次下载</span>
          <span>更新于 {{ formatDateTime(row.updatedAt || row.createdAt) }}</span>
        </div>

        <div class="tpl-actions">
          <el-button size="small" :disabled="isPlaceholder(row)" @click="download(row)">下载</el-button>
          <el-button v-if="isPlaceholder(row)" size="small" type="primary" @click="showFillDialog(row)">补传文件</el-button>
          <el-button v-else size="small" type="warning" plain @click="showFillDialog(row)">替换</el-button>
          <el-popconfirm title="确定删除该模板吗？" @confirm="handleDelete(row.id)">
            <template #reference><el-button size="small" type="danger" plain>删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>

      <EmptyState
        v-if="!loading && !filteredList.length"
        class="tpl-empty"
        title="暂无办公模板"
        description="点击右上角「上传模板」, 学生端「文件与模板」页即可下载使用。"
      />
    </div>

    <!-- 新增模板 -->
    <el-dialog v-model="dialogVisible" title="上传办公模板" width="540px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="模板名称" required>
          <el-input v-model="form.title" placeholder="如：学生请假条" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="简要说明使用场景与填写要点"
          />
        </el-form-item>
        <el-form-item label="模板文件" required>
          <el-upload action="" :auto-upload="false" :limit="1" :on-change="handleFileChange" :on-remove="handleFileRemove" :file-list="fileList">
            <el-button>选择文件</el-button>
            <template #tip><div class="upload-tip">支持 Word / Excel / PDF, 不超过 30MB</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 占位补传 / 替换 -->
    <el-dialog v-model="fillDialogVisible" :title="fillTargetTitle" width="540px">
      <el-alert
        v-if="fillTarget && isPlaceholder(fillTarget)"
        type="warning"
        :closable="false"
        title="该模板目前为占位记录, 上传后即可对外提供下载。"
        style="margin-bottom: 16px;"
      />
      <el-form label-width="90px">
        <el-form-item label="模板名称">
          <el-input :model-value="fillTarget?.title || ''" disabled />
        </el-form-item>
        <el-form-item label="模板文件" required>
          <el-upload action="" :auto-upload="false" :limit="1" :on-change="handleFillFileChange" :on-remove="handleFillFileRemove" :file-list="fillFileList">
            <el-button>选择文件</el-button>
            <template #tip><div class="upload-tip">上传后会替换该模板的当前文件</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fillDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="filling" @click="handleFillSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { qaApi, fileApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const categories = ['党团证明', '请假申请', '活动报销', '工作简报', '其他']

const loading = ref(false)
const uploading = ref(false)
const filling = ref(false)
const dialogVisible = ref(false)
const fillDialogVisible = ref(false)
const list = ref([])
const fileList = ref([])
const fillFileList = ref([])
const uploadFile = ref(null)
const fillFile = ref(null)
const fillTarget = ref(null)
const query = reactive({ category: '', placeholder: null })
const form = reactive({ title: '', category: '', description: '' })

const fillTargetTitle = computed(() => {
  if (!fillTarget.value) return ''
  return isPlaceholder(fillTarget.value) ? `补传模板文件 - ${fillTarget.value.title}` : `替换模板文件 - ${fillTarget.value.title}`
})

const filteredList = computed(() => {
  if (query.placeholder === null || query.placeholder === '') return list.value
  return list.value.filter(r => isPlaceholder(r) === query.placeholder)
})

function isPlaceholder(row) {
  return !row.filePath
}

async function loadData() {
  loading.value = true
  try {
    const params = {}
    if (query.category) params.category = query.category
    const res = await qaApi.getTemplateList(params)
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
  form.description = ''
  fileList.value = []
  uploadFile.value = null
  dialogVisible.value = true
}

function showFillDialog(row) {
  fillTarget.value = row
  fillFileList.value = []
  fillFile.value = null
  fillDialogVisible.value = true
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

function handleFillFileChange(file, files) {
  if (!file.raw) return
  if (file.raw.size > 30 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 30MB')
    fillFileList.value = []
    fillFile.value = null
    return
  }
  fillFileList.value = files.slice(-1)
  fillFile.value = file.raw
}

function handleFillFileRemove() {
  fillFileList.value = []
  fillFile.value = null
}

async function handleUpload() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入模板名称')
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
    await qaApi.addTemplate({
      title: form.title.trim(),
      category: form.category,
      description: form.description?.trim() || '',
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

async function handleFillSubmit() {
  if (!fillTarget.value) return
  if (!fillFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  const uploadFormData = new FormData()
  uploadFormData.append('file', fillFile.value)
  filling.value = true
  try {
    const uploadRes = await fileApi.upload(uploadFormData)
    const fileInfo = uploadRes.data
    await qaApi.fillTemplateFile(fillTarget.value.id, {
      filePath: fileInfo.filePath,
      fileSize: fileInfo.fileSize,
      fileType: fillFile.value.type || ''
    })
    ElMessage.success('上传成功')
    fillDialogVisible.value = false
    loadData()
  } finally {
    filling.value = false
  }
}

async function download(row) {
  if (isPlaceholder(row)) {
    ElMessage.warning('该模板尚未上线, 请先补传文件')
    return
  }
  const userStore = useUserStore()
  if (!userStore.token) {
    ElMessage.error('请先登录')
    router.push('/login')
    return
  }
  const res = await fetch(qaApi.getTemplateDownloadUrl(row.id), {
    method: 'GET',
    headers: { Authorization: `Bearer ${userStore.token}` },
  })
  if (res.status === 401) {
    userStore.logout()
    ElMessage.error('登录已过期, 请重新登录')
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
  const filename = parseDownloadFilename(disposition) || `${row.title || 'template'}`
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
  await qaApi.deleteTemplate(id)
  ElMessage.success('删除成功')
  loadData()
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
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

/* ===== 模板卡片网格 ===== */
.tpl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 14px;
  min-height: 150px;
}

.tpl-empty {
  grid-column: 1 / -1;
}

.tpl-card {
  display: flex;
  flex-direction: column;
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

/* 占位待传: 虚线琥珀边 */
.tpl-card--placeholder {
  border-style: dashed;
  border-color: #E2C58A;
  background: #FFFDF8;
}

.tpl-card__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.tpl-icon {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: var(--app-primary);
  background: var(--app-primary-light);
}

.tpl-card--placeholder .tpl-icon {
  color: var(--app-gold-deep);
  background: var(--app-gold-light);
}

.tpl-head-info {
  flex: 1;
  min-width: 0;
}

.tpl-title {
  margin: 0;
  color: var(--app-text);
  font-size: 14.5px;
  font-weight: 650;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tpl-chip {
  display: inline-block;
  margin-top: 5px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--app-primary-light);
  color: var(--app-primary);
  font-size: 12px;
}

.tpl-desc {
  flex: 1;
  margin: 11px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tpl-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  margin-top: 11px;
  color: var(--app-text-placeholder);
  font-size: 12px;
}

.tpl-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border-light);
}

.tpl-card--placeholder .tpl-actions {
  border-top-color: #F0E4CB;
}
</style>
