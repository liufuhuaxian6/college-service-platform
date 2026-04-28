<template>
  <el-card>
    <template #header><span>操作日志</span></template>
    <el-form inline style="margin-bottom:16px">
      <el-form-item label="模块">
        <el-select v-model="query.module" clearable placeholder="全部" @change="loadData">
          <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始时间"><el-date-picker v-model="query.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="结束时间"><el-date-picker v-model="query.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      <el-form-item><el-button type="primary" @click="loadData">查询</el-button></el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="userId" label="操作人ID" width="100" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="action" label="操作" min-width="200" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="createdAt" label="时间" width="180" />
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
import { logApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const modules = ['知识库', '文档管理', '党团流程', '审批管理', '学生画像', '用户管理']
const query = reactive({ page: 1, size: 20, module: null, startDate: null, endDate: null })

async function loadData() {
  loading.value = true
  try {
    const res = await logApi.getPage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

onMounted(loadData)
</script>
