<template>
  <div class="app-page">
    <PageHeader title="待审批" description="处理学生提交的电子证明申请，审批操作会写入流转记录。" />

    <FilterBar>
      <el-form inline>
        <el-form-item label="证明类型">
          <el-select v-model="query.typeId" clearable placeholder="全部" style="width: 200px">
            <el-option v-for="t in typeOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel title="待审批列表">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="appNo" label="申请编号" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请人" width="160">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
            <span class="cell-sub">{{ row.studentId || row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="证明类型 / 模板" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.templateName || row.typeName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="当前层级" width="100">
          <template #default="{ row }">L{{ row.currentApproverLevel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewDetail(row)">详情</el-button>
            <el-button type="success" link :disabled="row.status !== 'pending'" @click="handleApprove(row)">通过</el-button>
            <el-button type="danger" link :disabled="row.status !== 'pending'" @click="handleReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </DataPanel>

    <el-drawer v-model="detailVisible" title="申请详情" size="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="申请编号">{{ detail.appNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ detail.userName || '-' }}
          <span class="cell-sub">{{ detail.studentId || detail.userId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="证明类型 / 模板">{{ detail.templateName || detail.typeName || `类型 ${detail.typeId}` }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="当前审批层级">L{{ detail.currentApproverLevel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updatedAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-section">
        <h3>申请表单内容</h3>
        <el-input type="textarea" :rows="8" readonly :model-value="formatFormData(detail.formData)" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const detailVisible = ref(false)
const list = ref([])
const total = ref(0)
const detail = ref({})
const typeOptions = ref([])
const query = reactive({ page: 1, size: 20, typeId: '' })

async function loadTypes() {
  try {
    const res = await approvalApi.getTypes()
    typeOptions.value = res.data || []
  } catch (e) { /* ignore */ }
}

function buildQueryParams() {
  const params = { page: query.page, size: query.size }
  if (query.typeId) params.typeId = Number(query.typeId)
  return params
}

onMounted(loadTypes)

async function loadData() {
  loading.value = true
  try {
    const res = await approvalApi.getPendingPage(buildQueryParams())
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

async function handleApprove(row) {
  if (row.status !== 'pending') return
  try {
    const { value } = await ElMessageBox.prompt(`确定通过申请「${row.appNo}」吗？`, '通过审批', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '审批意见，可不填',
    })
    await approvalApi.approve(row.id, { comment: value || '' })
    ElMessage.success('已通过')
    loadData()
  } catch (error) { /* user cancelled */ }
}

async function handleReject(row) {
  if (row.status !== 'pending') return
  try {
    const { value } = await ElMessageBox.prompt(`请输入申请「${row.appNo}」的驳回原因`, '驳回申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因',
      inputValidator: value => !!value || '驳回原因不能为空',
    })
    await approvalApi.reject(row.id, { comment: value })
    ElMessage.success('已驳回')
    loadData()
  } catch (error) { /* user cancelled */ }
}

function viewDetail(row) {
  detail.value = row
  detailVisible.value = true
}

function formatFormData(formData) {
  if (!formData) return '暂无表单内容'
  if (typeof formData === 'string') {
    try { return JSON.stringify(JSON.parse(formData), null, 2) } catch (error) { return formData }
  }
  return JSON.stringify(formData, null, 2)
}

onMounted(loadData)
</script>

<style scoped>
.cell-sub {
  display: block;
  margin-top: 2px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.2;
}

.detail-section {
  margin-top: 18px;
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
}
</style>
