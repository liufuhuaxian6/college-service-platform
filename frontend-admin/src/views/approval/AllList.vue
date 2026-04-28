<template>
  <el-card>
    <template #header><span>全部申请</span></template>
    <el-form inline style="margin-bottom:16px">
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" @change="loadData">
          <el-option label="草稿" value="draft" />
          <el-option label="待审批" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
          <el-option label="已撤回" value="withdrawn" />
          <el-option label="已锁定" value="downloaded" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="loadData">查询</el-button></el-form-item>
    </el-form>
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="appNo" label="申请编号" width="200" />
      <el-table-column prop="userId" label="申请人" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="提交时间" width="180" />
      <el-table-column prop="updatedAt" label="更新时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" v-if="row.status === 'approved' && !row.downloadedAt" @click="handleWithdraw(row.id)">撤回</el-button>
          <el-tag type="info" v-if="row.status === 'downloaded'">已锁定</el-tag>
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
const query = reactive({ page: 1, size: 20, status: null })

const statusMap = {
  draft: { label: '草稿', type: 'info' },
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  withdrawn: { label: '已撤回', type: 'info' },
  downloaded: { label: '已锁定', type: '' },
}
const statusLabel = (s) => statusMap[s]?.label || s
const statusType = (s) => statusMap[s]?.type || ''

async function loadData() {
  loading.value = true
  try {
    const res = await approvalApi.getAllPage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function handleWithdraw(id) {
  const { value } = await ElMessageBox.prompt('请输入撤回原因', '撤回审批')
  await approvalApi.adminWithdraw(id, { comment: value })
  ElMessage.success('已撤回')
  loadData()
}

onMounted(loadData)
</script>
