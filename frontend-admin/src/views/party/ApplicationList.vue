<template>
  <div class="app-page">
    <PageHeader title="流程申请" description="审核学生提交的入党 / 入团流程申请，通过后自动创建学生流程。">
      <template #actions>
        <el-button :icon="'Refresh'" @click="reload">刷新</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px" @change="handleSearch">
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已撤回" value="withdrawn" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程">
          <el-select v-model="query.templateId" clearable placeholder="全部流程" style="width: 190px" @change="handleSearch">
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生">
          <el-select
            v-model="query.userId"
            clearable
            filterable
            placeholder="学号 / 姓名"
            style="width: 230px"
            @change="handleSearch"
          >
            <el-option
              v-for="s in students"
              :key="s.id"
              :label="`${s.studentId} ${s.name}${s.className ? ' · ' + s.className : ''}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel>
      <el-table :data="list" v-loading="loading" stripe @row-click="openDetail">
        <el-table-column prop="appNo" label="申请编号" min-width="170" show-overflow-tooltip />
        <el-table-column label="申请人" width="160">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
            <span class="cell-sub">{{ row.studentId || row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="流程模板" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.templateName || getTemplateName(row.templateId) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="申请说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="提交时间" width="150" :formatter="row => formatDateTime(row.createdAt)" />
        <el-table-column label="审核人" width="120">
          <template #default="{ row }">{{ row.reviewerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">
              {{ row.status === 'pending' ? '去审核' : '详情' }}
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

    <el-drawer v-model="detailVisible" title="流程申请详情" size="560px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="申请编号">{{ detail.appNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">
          {{ detail.userName || '-' }}
          <span class="cell-sub">{{ detail.studentId || detail.userId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="流程模板">{{ detail.templateName || getTemplateName(detail.templateId) }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewedAt" label="审核时间">{{ formatDateTime(detail.reviewedAt) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewerName" label="审核人">{{ detail.reviewerName }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.instanceId" label="已创建流程">#{{ detail.instanceId }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-section">
        <h3>申请说明</h3>
        <div class="reason-box">{{ detail.reason || '学生未填写补充说明。' }}</div>
      </div>

      <div v-if="detail.reviewComment" class="detail-section">
        <h3>审核意见</h3>
        <div class="reason-box">{{ detail.reviewComment }}</div>
      </div>

      <div v-if="canReview" class="action-panel">
        <h3>审核操作</h3>
        <el-input
          v-model="reviewComment"
          type="textarea"
          :rows="4"
          placeholder="审核意见；驳回时必填"
        />
        <div class="action-buttons">
          <el-button type="success" :loading="acting" @click="doApprove">通过并创建流程</el-button>
          <el-button type="danger" :loading="acting" @click="doReject">驳回</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { partyApi, systemApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const acting = ref(false)
const detailVisible = ref(false)
const list = ref([])
const total = ref(0)
const templates = ref([])
const students = ref([])
const detail = ref({})
const reviewComment = ref('')
const query = reactive({ page: 1, size: 20, status: 'pending', templateId: null, userId: null })

const canReview = computed(() => detail.value.status === 'pending')

function buildParams() {
  const params = { page: query.page, size: query.size }
  if (query.status) params.status = query.status
  if (query.templateId) params.templateId = Number(query.templateId)
  if (query.userId) params.userId = Number(query.userId)
  return params
}

function getTemplateName(id) {
  return templates.value.find((item) => item.id === id)?.name || id || '-'
}

async function loadTemplates() {
  try {
    const res = await partyApi.getTemplatePage({ page: 1, size: 100 })
    templates.value = res.data.records || []
  } catch (e) {
    templates.value = []
  }
}

async function loadStudents() {
  try {
    const res = await systemApi.getUserPage({ page: 1, size: 500 })
    students.value = (res.data?.records || []).filter((u) => u.roleLevel === 3 || u.roleLevel === 4)
  } catch (e) {
    students.value = []
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await partyApi.getApplicationPage(buildParams())
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

function reload() {
  loadData()
}

function openDetail(row) {
  detail.value = row
  reviewComment.value = ''
  detailVisible.value = true
}

async function doApprove() {
  acting.value = true
  try {
    await partyApi.approveApplication(detail.value.id, { remark: reviewComment.value || '' })
    ElMessage.success('已通过申请并创建学生流程')
    detailVisible.value = false
    loadData()
  } finally {
    acting.value = false
  }
}

async function doReject() {
  if (!reviewComment.value.trim()) {
    ElMessage.warning('驳回时请填写审核意见')
    return
  }
  acting.value = true
  try {
    await partyApi.rejectApplication(detail.value.id, { remark: reviewComment.value.trim() })
    ElMessage.success('已驳回申请')
    detailVisible.value = false
    loadData()
  } finally {
    acting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadTemplates(), loadStudents()])
  loadData()
})
</script>

<style scoped lang="scss">
.cell-sub {
  display: block;
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.detail-section {
  margin-top: 18px;

  h3 {
    margin: 0 0 10px;
    color: var(--app-text-main);
    font-size: 15px;
  }
}

.reason-box {
  min-height: 72px;
  padding: 12px 14px;
  color: var(--app-text-main);
  line-height: 1.7;
  white-space: pre-wrap;
  background: var(--app-bg-soft);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.action-panel {
  margin-top: 20px;
  padding: 16px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);

  h3 {
    margin: 0 0 12px;
    font-size: 15px;
  }
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
}
</style>
