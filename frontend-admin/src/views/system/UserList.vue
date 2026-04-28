<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>用户管理</span>
        <el-upload action="" :before-upload="handleImport" :show-file-list="false" accept=".xlsx,.xls">
          <el-button type="primary">Excel导入</el-button>
        </el-upload>
      </div>
    </template>
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="studentId" label="学号" width="140" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="roleLevel" label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.roleLevel === 1 ? 'danger' : row.roleLevel === 2 ? 'warning' : row.roleLevel === 3 ? '' : 'info'">
            {{ { 1:'院领导', 2:'管理老师', 3:'班团骨干', 4:'学生' }[row.roleLevel] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="grade" label="年级" width="100" />
      <el-table-column prop="major" label="专业" min-width="140" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editUser(row)">编辑</el-button>
          <el-button link type="warning" @click="setRole(row)">设置角色</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="query.page"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await systemApi.getUserPage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function editUser(row) {
  // TODO: 编辑弹窗
}

async function setRole(row) {
  const { value } = await ElMessageBox.prompt(`设置 ${row.name} 的角色等级 (1-4)`, '设置角色', {
    inputValue: String(row.roleLevel),
    inputValidator: v => /^[1-4]$/.test(v) || '请输入1-4',
  })
  await systemApi.setUserRole(row.id, { roleLevel: parseInt(value) })
  ElMessage.success('设置成功')
  loadData()
}

function handleImport(file) {
  const formData = new FormData()
  formData.append('file', file)
  systemApi.importUsers(formData).then(() => {
    ElMessage.success('导入成功')
    loadData()
  })
  return false
}

onMounted(loadData)
</script>
