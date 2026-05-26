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

    <DataPanel title="模板列表">
      <el-table :data="filteredList" v-loading="loading" stripe>
        <el-table-column prop="title" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="description" label="适用范围" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="color: var(--app-text-secondary)">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusTag v-if="isPlaceholder(row)" type="warning" text="待补传" />
            <StatusTag v-else type="success" text="已上线" />
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">{{ row.filePath ? formatSize(row.fileSize) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="downloadCount" label="下载次数" width="100" />
        <el-table-column prop="createdAt" label="录入时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="isPlaceholder(row)"
              @click="download(row)"
            >
              下载
            </el-button>
            <el-button
              v-if="isPlaceholder(row)"
              link
              type="success"
              @click="showFillDialog(row)"
            >
              补传文件
            </el-button>
            <el-button
              v-else
              link
              type="warning"
              @click="showFillDialog(row)"
            >
              替换
            </el-button>
            <el-popconfirm title="确定删除该模板吗？" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </DataPanel>

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
import { useUserStore } from '@/stores/user'
import router from '@/router'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'
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

<style scoped>
.upload-tip {
  margin-top: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
}
</style>
