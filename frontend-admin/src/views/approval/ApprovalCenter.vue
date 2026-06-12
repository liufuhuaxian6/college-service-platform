<template>
  <div class="app-page">
    <PageHeader title="审批中心" description="集中处理学生电子证明申请：待办审批与全量申请记录一处完成。">
      <template #actions>
        <el-button :icon="'Refresh'" @click="reload">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 顶部 Tab 切换: 待审批 / 全部申请 -->
    <div class="seg-bar">
      <button
        class="seg-item"
        :class="{ active: tab === 'pending' }"
        @click="switchTab('pending')"
      >
        待审批
        <span v-if="pendingTotal" class="seg-count">{{ pendingTotal }}</span>
      </button>
      <button
        class="seg-item"
        :class="{ active: tab === 'all' }"
        @click="switchTab('all')"
      >
        全部申请
      </button>

      <div class="seg-filters">
        <template v-if="tab === 'pending'">
          <el-select v-model="pendingQuery.typeId" clearable placeholder="全部证明类型" style="width: 190px" @change="searchPending">
            <el-option v-for="t in typeOptions" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </template>
        <template v-else>
          <el-select v-model="allQuery.status" clearable placeholder="全部状态" style="width: 150px" @change="searchAll">
            <el-option label="草稿" value="draft" />
            <el-option label="待审批" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已撤回" value="withdrawn" />
            <el-option label="已锁定归档" value="downloaded" />
          </el-select>
          <el-input v-model="allQuery.userId" clearable placeholder="申请人用户ID" style="width: 160px" @keyup.enter="searchAll" />
          <el-button type="primary" @click="searchAll">查询</el-button>
        </template>
      </div>
    </div>

    <DataPanel>
      <el-table :data="tab === 'pending' ? pendingList : allList" v-loading="loading" stripe @row-click="openDetail">
        <el-table-column prop="appNo" label="申请编号" min-width="180" show-overflow-tooltip />
        <el-table-column label="申请人" width="160">
          <template #default="{ row }">
            <span>{{ row.userName || '-' }}</span>
            <span class="cell-sub">{{ row.studentId || row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="证明类型 / 模板" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.templateName || row.typeName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column v-if="tab === 'pending'" label="当前层级" width="95">
          <template #default="{ row }">L{{ row.currentApproverLevel || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="150" :formatter="row => formatDateTime(row.createdAt)" />
        <el-table-column v-if="tab === 'all'" label="更新时间" width="150" :formatter="row => formatDateTime(row.updatedAt)" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">
              {{ tab === 'pending' && row.status === 'pending' ? '去审批' : '详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-if="tab === 'pending'"
          v-model:current-page="pendingQuery.page"
          v-model:page-size="pendingQuery.size"
          :total="pendingTotal"
          layout="total, prev, pager, next"
          @current-change="loadPending"
        />
        <el-pagination
          v-else
          v-model:current-page="allQuery.page"
          v-model:page-size="allQuery.size"
          :total="allTotal"
          layout="total, prev, pager, next"
          @current-change="loadAll"
        />
      </div>
    </DataPanel>

    <!-- 详情 + 在抽屉内直接审批 -->
    <el-drawer v-model="detailVisible" title="申请详情" size="620px">
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
        <el-descriptions-item label="证明类型 / 模板">{{ detail.templateName || detail.typeName || `类型 ${detail.typeId}` }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
        <el-descriptions-item label="当前审批层级">L{{ detail.currentApproverLevel || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.downloadedAt" label="下载时间">{{ formatDateTime(detail.downloadedAt) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedAt) }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-section">
        <h3>申请表单内容</h3>
        <el-input type="textarea" :rows="7" readonly :model-value="formatFormData(detail.formData)" />
      </div>

      <!-- 操作区: 按状态显示可用动作 -->
      <div v-if="canApprove || canAdminWithdraw" class="action-panel">
        <h3>{{ canApprove ? '审批操作' : '管理操作' }}</h3>
        <el-input
          v-model="actionComment"
          type="textarea"
          :rows="3"
          :placeholder="canApprove ? '审批意见（驳回时必填）' : '撤回原因（必填）'"
        />
        <div class="action-buttons">
          <template v-if="canApprove">
            <el-button type="success" :loading="acting" @click="doApprove">通过</el-button>
            <el-button type="danger" :loading="acting" @click="doReject">驳回</el-button>
          </template>
          <el-button v-if="canAdminWithdraw" type="warning" :loading="acting" @click="doAdminWithdraw">
            撤回重批
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { approvalApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import PageHeader from '@/components/common/PageHeader.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const route = useRoute()
const router = useRouter()

const tab = ref(route.query.tab === 'all' ? 'all' : 'pending')
const loading = ref(false)

const typeOptions = ref([])
const pendingList = ref([])
const pendingTotal = ref(0)
const pendingQuery = reactive({ page: 1, size: 20, typeId: '' })

const allList = ref([])
const allTotal = ref(0)
const allLoaded = ref(false)
const allQuery = reactive({ page: 1, size: 20, status: '', userId: '' })

// ===== 详情抽屉 + 抽屉内审批 =====
const detailVisible = ref(false)
const detail = ref({})
const actionComment = ref('')
const acting = ref(false)

const canApprove = computed(() => detail.value.status === 'pending')
const canAdminWithdraw = computed(
  () => detail.value.status === 'approved' && !detail.value.downloadedAt,
)

function openDetail(row) {
  detail.value = row
  actionComment.value = ''
  detailVisible.value = true
}

async function doApprove() {
  acting.value = true
  try {
    await approvalApi.approve(detail.value.id, { comment: actionComment.value || '' })
    ElMessage.success(`已通过「${detail.value.appNo}」`)
    detailVisible.value = false
    reload()
  } finally {
    acting.value = false
  }
}

async function doReject() {
  if (!actionComment.value.trim()) {
    ElMessage.warning('驳回时请填写审批意见')
    return
  }
  acting.value = true
  try {
    await approvalApi.reject(detail.value.id, { comment: actionComment.value.trim() })
    ElMessage.success(`已驳回「${detail.value.appNo}」`)
    detailVisible.value = false
    reload()
  } finally {
    acting.value = false
  }
}

async function doAdminWithdraw() {
  if (!actionComment.value.trim()) {
    ElMessage.warning('请填写撤回原因')
    return
  }
  acting.value = true
  try {
    await approvalApi.adminWithdraw(detail.value.id, { comment: actionComment.value.trim() })
    ElMessage.success('已撤回，可重新审批')
    detailVisible.value = false
    reload()
  } finally {
    acting.value = false
  }
}

// ===== 数据加载 =====
async function loadTypes() {
  try {
    const res = await approvalApi.getTypes()
    typeOptions.value = res.data || []
  } catch (e) { /* ignore */ }
}

async function loadPending() {
  loading.value = true
  try {
    const params = { page: pendingQuery.page, size: pendingQuery.size }
    if (pendingQuery.typeId) params.typeId = Number(pendingQuery.typeId)
    const res = await approvalApi.getPendingPage(params)
    pendingList.value = res.data.records || []
    pendingTotal.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    const params = { page: allQuery.page, size: allQuery.size }
    if (allQuery.status) params.status = allQuery.status
    if (allQuery.userId) params.userId = Number(allQuery.userId)
    const res = await approvalApi.getAllPage(params)
    allList.value = res.data.records || []
    allTotal.value = res.data.total || 0
    allLoaded.value = true
  } finally {
    loading.value = false
  }
}

function searchPending() {
  pendingQuery.page = 1
  loadPending()
}

function searchAll() {
  allQuery.page = 1
  loadAll()
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  router.replace({ path: '/approval/center', query: { tab: t } })
  if (t === 'all' && !allLoaded.value) loadAll()
}

function reload() {
  if (tab.value === 'pending') loadPending()
  else loadAll()
  // 待审批数同时驱动 Tab 角标, 在全部申请页操作后也刷新一次
  if (tab.value === 'all') loadPending()
}

onMounted(async () => {
  loadTypes()
  await loadPending()
  if (tab.value === 'all') await loadAll()

  // 由工作台跳来时, URL 带 ?openId=X 自动打开该条详情抽屉
  const openId = Number(route.query.openId)
  if (openId) {
    const target = pendingList.value.find(r => r.id === openId)
    if (target) {
      openDetail(target)
    } else {
      ElMessage.info('该申请已不在待审批列表 (可能已处理), 可切到"全部申请"查看')
    }
    router.replace({ path: '/approval/center', query: { tab: tab.value } })
  }
})
</script>

<style scoped lang="scss">
/* ===== 分段切换条 ===== */
.seg-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
}

.seg-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 18px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--app-text-regular);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s var(--app-ease);

  &:hover {
    background: var(--app-muted);
  }

  &.active {
    color: #fff;
    background: var(--app-red-gradient);
    box-shadow: 0 3px 10px rgba(157, 34, 53, 0.28);
    font-weight: 600;
  }
}

.seg-count {
  min-width: 20px;
  height: 20px;
  display: inline-grid;
  place-items: center;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.24);
  font-size: 12px;
}

.seg-item:not(.active) .seg-count {
  background: var(--app-primary-light);
  color: var(--app-primary);
}

.seg-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* ===== 表格行可点击 ===== */
:deep(.el-table__row) {
  cursor: pointer;
}

/* ===== 抽屉内 ===== */
.lock-alert {
  margin-bottom: 14px;
}

.detail-section,
.action-panel {
  margin-top: 18px;

  h3 {
    margin: 0 0 10px;
    font-size: 15px;
    color: var(--app-text);
  }
}

.action-panel {
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-muted);
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
