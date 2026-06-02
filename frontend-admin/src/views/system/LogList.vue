<template>
  <el-card>
    <template #header>
      <span>操作日志</span>
    </template>

    <el-form inline style="margin-bottom:16px">
      <el-form-item label="模块">
        <el-select
          v-model="query.module"
          clearable
          placeholder="全部"
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>

      <el-form-item label="开始时间">
        <el-date-picker
          v-model="query.startDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择开始日期"
          style="width: 160px"
        />
      </el-form-item>

      <el-form-item label="结束时间">
        <el-date-picker
          v-model="query.endDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择结束日期"
          style="width: 160px"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="userId" label="操作人ID" width="110" />
      <el-table-column prop="module" label="模块" width="130" />
      <el-table-column prop="action" label="操作" min-width="220" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP" width="150" />
      <el-table-column label="时间" width="150" :formatter="row => formatDateTime(row.createdAt)" />
    </el-table>

    <el-pagination
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { logApi } from '@/api'
import { formatDateTime } from '@/utils/time'

const loading = ref(false)
const list = ref([])
const total = ref(0)

const modules = ['知识库', '文档管理', '党团流程', '审批管理', '学生画像', '用户管理']

const query = reactive({
  page: 1,
  size: 20,
  module: '',
  startDate: '',
  endDate: '',
})

function buildQueryParams() {
  const params = {
    page: query.page,
    size: query.size,
  }

  if (query.module) {
    params.module = query.module
  }

  if (query.startDate) {
    params.startDate = query.startDate
  }

  if (query.endDate) {
    params.endDate = query.endDate
  }

  return params
}

async function loadData() {
  loading.value = true

  try {
    const res = await logApi.getPage(buildQueryParams())
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function resetQuery() {
  query.page = 1
  query.module = ''
  query.startDate = ''
  query.endDate = ''
  loadData()
}

onMounted(loadData)
</script>