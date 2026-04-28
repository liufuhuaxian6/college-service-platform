<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>政策文档管理</span>
        <el-upload action="" :before-upload="handleUpload" :show-file-list="false">
          <el-button type="primary">上传文档</el-button>
        </el-upload>
      </div>
    </template>
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="title" label="文档标题" min-width="200" />
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="fileSize" label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="downloadCount" label="下载次数" width="100" />
      <el-table-column prop="createdAt" label="上传时间" width="180" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">下载</el-button>
          <el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { qaApi, fileApi } from '@/api'

const loading = ref(false)
const list = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await qaApi.getDocumentList({})
    list.value = res.data || []
  } finally { loading.value = false }
}

function handleUpload(file) {
  if (file.size > 30 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过30MB')
    return false
  }
  // TODO: 先调 fileApi.upload 获取路径, 再调 qaApi.addDocument
  ElMessage.info('文档上传功能待完善')
  return false
}

function download(row) {
  window.open(fileApi.getDownloadUrl(row.id))
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
