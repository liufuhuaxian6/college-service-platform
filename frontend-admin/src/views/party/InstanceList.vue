<template>
  <el-card>
    <template #header><span>学生流程管理</span></template>
    <el-form inline style="margin-bottom:16px">
      <el-form-item label="流程">
        <el-select v-model="query.templateId" clearable placeholder="全部" @change="loadData">
          <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" @change="loadData">
          <el-option label="进行中" value="active" />
          <el-option label="已完成" value="completed" />
          <el-option label="已暂停" value="suspended" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="loadData">查询</el-button></el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="userId" label="学生ID" width="100" />
      <el-table-column prop="templateId" label="流程模板" width="120" />
      <el-table-column prop="currentStep" label="当前步骤" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'primary' : row.status === 'completed' ? 'success' : 'warning'">
            {{ { active: '进行中', completed: '已完成', suspended: '已暂停' }[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始日期" width="120" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="advance(row.id)" :disabled="row.status !== 'active'">推进</el-button>
          <el-button link type="warning" @click="suspend(row.id)" :disabled="row.status !== 'active'">暂停</el-button>
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
import { partyApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const templates = ref([])
const query = reactive({ page: 1, size: 20, templateId: null, status: null })

async function loadData() {
  loading.value = true
  try {
    const res = await partyApi.getInstancePage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function advance(id) {
  const { value } = await ElMessageBox.prompt('请输入备注(可选)', '推进步骤')
  await partyApi.advanceStep(id, { remark: value })
  ElMessage.success('推进成功')
  loadData()
}

async function suspend(id) {
  await ElMessageBox.confirm('确定暂停该流程?')
  await partyApi.suspendInstance(id, {})
  ElMessage.success('已暂停')
  loadData()
}

onMounted(async () => {
  try {
    const res = await partyApi.getTemplatePage({ page: 1, size: 100 })
    templates.value = res.data.records
  } catch (e) { /* ignore */ }
  loadData()
})
</script>
