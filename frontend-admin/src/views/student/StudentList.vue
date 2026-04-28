<template>
  <el-card>
    <template #header><span>学生信息管理</span></template>
    <el-form inline style="margin-bottom:16px">
      <el-form-item label="年级"><el-input v-model="query.grade" clearable @keyup.enter="loadData" /></el-form-item>
      <el-form-item label="专业"><el-input v-model="query.major" clearable @keyup.enter="loadData" /></el-form-item>
      <el-form-item label="班级"><el-input v-model="query.className" clearable @keyup.enter="loadData" /></el-form-item>
      <el-form-item><el-button type="primary" @click="loadData">查询</el-button></el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="studentId" label="学号" width="140" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="grade" label="年级" width="100" />
      <el-table-column prop="major" label="专业" min-width="140" />
      <el-table-column prop="className" label="班级" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
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
import { studentApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, grade: '', major: '', className: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await studentApi.getPage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function viewDetail(id) {
  // TODO: 弹窗或跳转到学生画像详情页
}

onMounted(loadData)
</script>
