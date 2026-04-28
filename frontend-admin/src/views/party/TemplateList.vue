<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>流程模板管理</span>
        <el-button type="primary" @click="$router.push('/party/template')">新建模板</el-button>
      </div>
    </template>
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="流程名称" min-width="160" />
      <el-table-column prop="totalSteps" label="步骤数" width="100" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editTemplate(row)">编辑</el-button>
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
import { partyApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await partyApi.getTemplatePage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function editTemplate(row) {
  // TODO: 打开编辑弹窗
}

onMounted(loadData)
</script>
