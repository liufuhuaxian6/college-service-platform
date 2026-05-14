<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>政策文档管理</span>
        <el-button type="primary" @click="showUploadDialog">上传文档</el-button>
      </div>
    </template>

    <el-form inline style="margin-bottom:16px">
      <el-form-item label="分类">
        <el-select
          v-model="query.category"
          clearable
          placeholder="全部"
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="title" label="文档标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="140" />
      <el-table-column prop="fileSize" label="大小" width="120">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column prop="downloadCount" label="下载次数" width="120" />
      <el-table-column prop="createdAt" label="上传时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">下载</el-button>
          <el-popconfirm title="确定删除该文档吗？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="上传政策文档" width="520px">
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
          <el-upload
            action=""
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
          >
            <el-button>选择文件</el-button>
            <template #tip>
              <div style="font-size:12px;color:#909399;margin-top:6px">
                文件大小不超过 30MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          上传
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { qaApi, fileApi } from '@/api'

const loading = ref(false)
const uploading = ref(false)
const dialogVisible = ref(false)
const list = ref([])
const fileList = ref([])
const uploadFile = ref(null)

const categories = ['入党', '入团', '奖学金', '日常事务', '其他']

const query = reactive({
  category: '',
})

const form = reactive({
  title: '',
  category: '',
})

function buildQueryParams() {
  const params = {}

  if (query.category) {
    params.category = query.category
  }

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
  if (!file.raw) {
    return
  }

  if (file.raw.size > 30 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 30MB')
    fileList.value = []
    uploadFile.value = null
    return
  }

  fileList.value = files.slice(-1)
  uploadFile.value = file.raw

  if (!form.title) {
    form.title = file.name.replace(/\.[^.]+$/, '')
  }
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

function download(row) {
  window.open(qaApi.getDocumentDownloadUrl(row.id))
}

async function handleDelete(id) {
  await qaApi.deleteDocument(id)
  ElMessage.success('删除成功')
  loadData()
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

onMounted(loadData)
</script>