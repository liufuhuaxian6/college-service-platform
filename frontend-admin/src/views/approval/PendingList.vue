<template>
  <el-card>
    <template #header>
      <span>待审批列表</span>
    </template>

    <el-form inline style="margin-bottom:16px">
      <el-form-item label="类型ID">
        <el-input
          v-model="query.typeId"
          clearable
          placeholder="输入类型ID"
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="appNo" label="申请编号" width="200" />
      <el-table-column prop="userId" label="申请人ID" width="110" />
      <el-table-column prop="typeId" label="类型ID" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag type="warning">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="提交时间" width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="success" link @click="handleApprove(row)">通过</el-button>
          <el-button type="danger" link @click="handleReject(row)">驳回</el-button>
          <el-button link @click="viewDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />

    <el-dialog v-model="detailVisible" title="申请详情" width="620px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请编号">
          {{ detail.appNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="申请人ID">
          {{ detail.userId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="类型ID">
          {{ detail.typeId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ statusLabel(detail.status) }}
        </el-descriptions-item>
        <el-descriptions-item label="提交时间">
          {{ detail.createdAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ detail.updatedAt || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <div style="margin-top:16px">
        <div style="font-weight:600;margin-bottom:8px">申请表单内容</div>
        <el-input
          type="textarea"
          :rows="8"
          readonly
          :model-value="formatFormData(detail.formData)"
        />
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi } from '@/api'

const loading = ref(false)
const detailVisible = ref(false)

const list = ref([])
const total = ref(0)
const detail = ref({})

const query = reactive({
  page: 1,
  size: 20,
  typeId: '',
})

const statusMap = {
  draft: '草稿',
  pending: '待审批',
  approved: '已通过',
  rejected: '已驳回',
  withdrawn: '已撤回',
  downloaded: '已锁定',
}

function statusLabel(status) {
  return statusMap[status] || status || '-'
}

function buildQueryParams() {
  const params = {
    page: query.page,
    size: query.size,
  }

  if (query.typeId) {
    params.typeId = Number(query.typeId)
  }

  return params
}

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
  try {
    const { value } = await ElMessageBox.prompt(
      `确定通过申请「${row.appNo}」吗？`,
      '通过审批',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '审批意见，可不填',
      }
    )

    await approvalApi.approve(row.id, { comment: value || '' })
    ElMessage.success('已通过')
    loadData()
  } catch (error) {
    // 用户取消时不处理
  }
}

async function handleReject(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入申请「${row.appNo}」的驳回原因`,
      '驳回申请',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '驳回原因',
        inputValidator: value => !!value || '驳回原因不能为空',
      }
    )

    await approvalApi.reject(row.id, { comment: value })
    ElMessage.success('已驳回')
    loadData()
  } catch (error) {
    // 用户取消时不处理
  }
}

function viewDetail(row) {
  detail.value = row
  detailVisible.value = true
}

function formatFormData(formData) {
  if (!formData) {
    return '暂无表单内容'
  }

  if (typeof formData === 'string') {
    try {
      return JSON.stringify(JSON.parse(formData), null, 2)
    } catch (error) {
      return formData
    }
  }

  return JSON.stringify(formData, null, 2)
}

onMounted(loadData)
</script>