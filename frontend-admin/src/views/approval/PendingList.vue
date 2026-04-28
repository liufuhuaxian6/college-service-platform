<template>
  <el-card>
    <template #header><span>待审批列表</span></template>
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="appNo" label="申请编号" width="200" />
      <el-table-column prop="userId" label="申请人" width="100" />
      <el-table-column prop="typeId" label="类型" width="120" />
      <el-table-column prop="createdAt" label="提交时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="success" link @click="handleApprove(row.id)">通过</el-button>
          <el-button type="danger" link @click="handleReject(row.id)">驳回</el-button>
          <el-button link @click="viewDetail(row)">详情</el-button>
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
import { approvalApi } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await approvalApi.getPendingPage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function handleApprove(id) {
  const { value } = await ElMessageBox.prompt('审批意见(可选)', '通过审批')
  await approvalApi.approve(id, { comment: value })
  ElMessage.success('已通过')
  loadData()
}

async function handleReject(id) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回申请', {
    inputValidator: v => !!v || '驳回原因不能为空'
  })
  await approvalApi.reject(id, { comment: value })
  ElMessage.success('已驳回')
  loadData()
}

function viewDetail(row) {
  // TODO: 弹窗查看详情
}

onMounted(loadData)
</script>
