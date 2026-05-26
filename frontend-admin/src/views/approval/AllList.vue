<template>
  <div class="app-page">
    <PageHeader title="全部申请" description="查看证明申请全生命周期状态。已下载后自动锁定归档，禁止撤回或重新审批。" />

    <FilterBar>
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px" @change="handleSearch">
            <el-option label="草稿" value="draft" />
            <el-option label="待审批" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已撤回" value="withdrawn" />
            <el-option label="已锁定归档" value="downloaded" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请人ID">
          <el-input v-model="query.userId" clearable placeholder="输入用户ID" style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel title="申请记录">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="appNo" label="申请编号" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请人" width="160">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
            <span class="cell-sub">{{ row.studentId || row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="typeName" label="证明类型" width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button
              link
              type="warning"
              :disabled="!canWithdraw(row)"
              @click="handleWithdraw(row)"
            >
              撤回重批
            </el-button>
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

    <el-drawer v-model="detailVisible" title="申请详情" size="580px">
      <el-alert
        v-if="detail.status === 'downloaded'"
        title="该申请已下载并锁定归档，不能撤回或重新审批。"
        type="info"
        show-icon
        :closable="false"
        class="lock-alert"
      />

      <el-descriptions :column="1" border>
        <el-descriptions-item label="申请编号">{{ detail.appNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ detail.userName || '-' }}
          <span class="cell-sub">{{ detail.studentId || detail.userId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="证明类型">{{ detail.typeName || `类型 ${detail.typeId}` }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="当前审批层级">L{{ detail.currentApproverLevel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下载时间">{{ detail.downloadedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
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
const query = reactive({ page: 1, size: 20, status: '', userId: '' })

function canWithdraw(row) {
  return row.status === 'approved' && !row.downloadedAt
}

function buildQueryParams() {
  const params = { page: query.page, size: query.size }
  if (query.status) params.status = query.status
  if (query.userId) params.userId = Number(query.userId)
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await approvalApi.getAllPage(buildQueryParams())
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

function viewDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function handleWithdraw(row) {
  if (!canWithdraw(row)) return
  try {
    const { value } = await ElMessageBox.prompt(`请输入撤回申请「${row.appNo}」的原因`, '撤回重批', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '撤回原因',
      inputValidator: value => !!value || '撤回原因不能为空',
    })
    await approvalApi.adminWithdraw(row.id, { comment: value })
    ElMessage.success('已撤回，可重新审批')
    loadData()
  } catch (error) { /* user cancelled */ }
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
.lock-alert {
  margin-bottom: 14px;
}

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
