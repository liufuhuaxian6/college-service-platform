<template>
  <div class="app-page">
    <PageHeader title="学生流程" description="跟踪学生党团事务办理进度，支持流程创建、推进和暂停。">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog">创建学生流程</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="流程">
          <el-select v-model="query.templateId" clearable placeholder="全部" style="width: 180px" @change="handleSearch">
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px" @change="handleSearch">
            <el-option label="进行中" value="active" />
            <el-option label="已完成" value="completed" />
            <el-option label="已暂停" value="suspended" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生">
          <el-select
            v-model="query.userId"
            clearable
            filterable
            placeholder="输入学号 / 姓名筛选"
            style="width: 260px"
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

    <DataPanel title="流程列表">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="学生" min-width="200">
          <template #default="{ row }">
            <span>{{ getStudentLabel(row.userId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="流程模板" min-width="170">
          <template #default="{ row }">{{ getTemplateName(row.templateId) }}</template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="100" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="140" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'active'">
              <el-button link type="primary" @click="advance(row)">推进</el-button>
              <el-button link type="warning" @click="suspend(row)">暂停</el-button>
            </template>
            <template v-else-if="row.status === 'suspended'">
              <el-button link type="success" @click="resume(row)">恢复</el-button>
            </template>
            <el-button link type="danger" @click="removeInstance(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" title="创建学生流程" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="学生" required>
          <el-select
            v-model="form.userId"
            filterable
            placeholder="输入学号 / 姓名搜索"
            style="width: 100%"
          >
            <el-option
              v-for="s in students"
              :key="s.id"
              :label="`${s.studentId} ${s.name}${s.className ? ' · ' + s.className : ''}${s.major ? ' · ' + s.major : ''}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="流程模板" required>
          <el-select v-model="form.templateId" placeholder="请选择流程模板" style="width: 100%">
            <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择开始日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { partyApi, systemApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const list = ref([])
const total = ref(0)
const templates = ref([])
const students = ref([])
const query = reactive({ page: 1, size: 20, templateId: null, status: '', userId: null })
const form = reactive({ userId: null, templateId: null, startDate: '' })

async function loadStudents() {
  // 一次拉所有 4 级学生 (一般 < 500), 由 el-select filterable 在前端过滤学号/姓名
  try {
    const res = await systemApi.getUserPage({ page: 1, size: 500 })
    students.value = (res.data?.records || []).filter(u => u.roleLevel === 4)
  } catch (e) {
    students.value = []
  }
}

function getStudentLabel(userId) {
  const s = students.value.find(x => x.id === userId)
  return s ? `${s.studentId} ${s.name}` : `用户 ${userId}`
}

function buildQueryParams() {
  const params = { page: query.page, size: query.size }
  if (query.templateId) params.templateId = query.templateId
  if (query.status) params.status = query.status
  if (query.userId) params.userId = Number(query.userId)
  return params
}

async function loadTemplates() {
  const res = await partyApi.getTemplatePage({ page: 1, size: 100 })
  templates.value = res.data.records || []
}

async function loadData() {
  loading.value = true
  try {
    const res = await partyApi.getInstancePage(buildQueryParams())
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

function showCreateDialog() {
  form.userId = null
  form.templateId = null
  form.startDate = new Date().toISOString().slice(0, 10)
  dialogVisible.value = true
}

function validateCreateForm() {
  if (!form.userId) {
    ElMessage.warning('请选择学生')
    return false
  }
  if (!form.templateId) {
    ElMessage.warning('请选择流程模板')
    return false
  }
  if (!form.startDate) {
    ElMessage.warning('请选择开始日期')
    return false
  }
  return true
}

async function handleCreate() {
  if (!validateCreateForm()) return
  submitting.value = true
  try {
    await partyApi.createInstance({
      userId: Number(form.userId),
      templateId: form.templateId,
      startDate: form.startDate,
    })
    ElMessage.success('创建学生流程成功')
    dialogVisible.value = false
    query.page = 1
    loadData()
  } finally {
    submitting.value = false
  }
}

async function advance(row) {
  try {
    const { value } = await ElMessageBox.prompt(`确定推进流程「${getTemplateName(row.templateId)}」到下一步吗？`, '推进步骤', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '备注，可不填',
    })
    await partyApi.advanceStep(row.id, { remark: value || '' })
    ElMessage.success('推进成功')
    loadData()
  } catch (error) { /* user cancelled */ }
}

async function suspend(row) {
  try {
    const { value } = await ElMessageBox.prompt(`确定暂停流程「${getTemplateName(row.templateId)}」吗？`, '暂停流程', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '暂停原因，可不填',
    })
    await partyApi.suspendInstance(row.id, { remark: value || '' })
    ElMessage.success('已暂停')
    loadData()
  } catch (error) { /* user cancelled */ }
}

async function removeInstance(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${getStudentLabel(row.userId)}」的流程「${getTemplateName(row.templateId)}」吗?\n此操作会同时清空该流程的所有步骤记录, 不可恢复.`,
      '删除流程',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await partyApi.deleteInstance(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (error) { /* user cancelled */ }
}

async function resume(row) {
  try {
    const { value } = await ElMessageBox.prompt(`确定恢复流程「${getTemplateName(row.templateId)}」吗？恢复后可继续推进步骤。`, '恢复流程', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '恢复备注，可不填',
    })
    await partyApi.resumeInstance(row.id, { remark: value || '' })
    ElMessage.success('已恢复, 当前步骤可继续推进')
    loadData()
  } catch (error) { /* user cancelled */ }
}

function getTemplateName(templateId) {
  const template = templates.value.find(item => item.id === templateId)
  return template ? template.name : templateId
}

onMounted(async () => {
  try {
    await Promise.all([loadTemplates(), loadStudents()])
  } catch (error) {
    ElMessage.warning('基础数据加载失败')
  }
  loadData()
})
</script>
