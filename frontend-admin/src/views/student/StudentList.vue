<template>
  <div class="app-page">
    <PageHeader title="学生信息" description="查看学生基本信息、荣誉记录及关联的党团流程和证明申请。" />

    <FilterBar>
      <el-form inline>
        <el-form-item label="身份">
          <el-select v-model="query.roleLevel" clearable placeholder="全部" style="width: 130px" @change="handleSearch">
            <el-option label="普通学生" :value="4" />
            <el-option label="学生骨干" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="query.grade" clearable filterable placeholder="全部年级" style="width: 140px" @change="handleSearch">
            <el-option v-for="g in dimensions.grades" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="query.major" clearable filterable placeholder="全部专业" style="width: 200px" @change="handleSearch">
            <el-option v-for="m in dimensions.majors" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="query.className" clearable filterable placeholder="全部班级" style="width: 170px" @change="handleSearch">
            <el-option v-for="c in dimensions.classNames" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel title="学生列表">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="studentId" label="学号" width="140" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="grade" label="年级" width="100" />
        <el-table-column prop="major" label="专业" min-width="160" show-overflow-tooltip />
        <el-table-column prop="className" label="班级" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="210" show-overflow-tooltip />
        <el-table-column prop="roleLevel" label="身份" width="100">
          <template #default="{ row }">
            <el-tag :type="row.roleLevel === 3 ? 'warning' : 'info'">
              {{ row.roleLevel === 3 ? '学生骨干' : '普通学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
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

    <el-drawer v-model="detailVisible" title="学生画像详情" size="760px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户ID">{{ detail.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ detail.studentId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ detail.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年级">{{ detail.grade || '-' }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{ detail.major || '-' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ detail.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detail.idCard || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="section-title">
        <span>荣誉记录</span>
        <el-button type="primary" size="small" @click="showHonorDialog">新增荣誉</el-button>
      </div>
      <el-table :data="honors" stripe>
        <el-table-column prop="honorName" label="荣誉名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="honorLevel" label="级别" width="120" />
        <el-table-column prop="awardDate" label="获奖日期" width="130" />
        <el-table-column prop="certFile" label="证书文件" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该荣誉记录吗？" @confirm="deleteHonor(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="section-title"><span>党团流程</span></div>
      <el-table :data="processes" stripe>
        <el-table-column prop="id" label="流程ID" width="90" />
        <el-table-column prop="templateName" label="流程名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="100" />
        <el-table-column prop="startDate" label="开始日期" width="120" />
      </el-table>

      <div class="section-title"><span>电子证明申请</span></div>
      <el-table :data="approvals" stripe>
        <el-table-column prop="appNo" label="申请编号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="typeName" label="类型" width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column prop="currentApproverLevel" label="审批层级" width="120" />
      </el-table>
    </el-drawer>

    <el-dialog v-model="honorVisible" title="新增荣誉" width="520px">
      <el-form :model="honorForm" label-width="90px">
        <el-form-item label="荣誉名称" required><el-input v-model="honorForm.honorName" placeholder="请输入荣誉名称" /></el-form-item>
        <el-form-item label="荣誉级别" required>
          <el-select v-model="honorForm.honorLevel" placeholder="请选择荣誉级别" style="width: 100%">
            <el-option label="国家级" value="国家级" />
            <el-option label="省部级" value="省部级" />
            <el-option label="校级" value="校级" />
            <el-option label="院级" value="院级" />
            <el-option label="班级" value="班级" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="获奖日期" required>
          <el-date-picker v-model="honorForm.awardDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择获奖日期" style="width: 100%" :disabled-date="disableFutureDate" />
        </el-form-item>
        <el-form-item label="证书文件"><el-input v-model="honorForm.certFile" placeholder="可填写证书文件路径或编号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="honorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="addHonor">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { studentApi, systemApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const submitting = ref(false)
const detailVisible = ref(false)
const honorVisible = ref(false)
const list = ref([])
const total = ref(0)
const detail = ref({})
const honors = ref([])
const processes = ref([])
const approvals = ref([])
const currentStudentId = ref(null)
const query = reactive({ page: 1, size: 20, grade: '', major: '', className: '', roleLevel: null })
const dimensions = reactive({ grades: [], majors: [], classNames: [] })
const honorForm = reactive({ honorName: '', honorLevel: '', awardDate: '', certFile: '' })

function buildQueryParams() {
  const params = { page: query.page, size: query.size }
  if (query.grade) params.grade = query.grade
  if (query.major) params.major = query.major
  if (query.className) params.className = query.className
  if (query.roleLevel != null) params.roleLevel = query.roleLevel
  return params
}

async function loadData() {
  loading.value = true
  try {
    const res = await studentApi.getPage(buildQueryParams())
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadDimensions() {
  try {
    const res = await systemApi.getDimensions()
    dimensions.grades = res.data?.grades || []
    dimensions.majors = res.data?.majors || []
    dimensions.classNames = res.data?.classNames || []
  } catch (e) { /* 拉取失败时下拉为空, 不影响主流程 */ }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function resetQuery() {
  query.grade = ''
  query.major = ''
  query.className = ''
  query.roleLevel = null
  query.page = 1
  loadData()
}

async function viewDetail(id) {
  currentStudentId.value = id
  const res = await studentApi.getDetail(id)
  detail.value = res.data || {}
  honors.value = detail.value.honors || []
  processes.value = detail.value.processes || []
  approvals.value = detail.value.approvals || []
  detailVisible.value = true
}

function showHonorDialog() {
  honorForm.honorName = ''
  honorForm.honorLevel = ''
  honorForm.awardDate = ''
  honorForm.certFile = ''
  honorVisible.value = true
}

// 禁止选择未来日期 (获奖日期不能晚于今天)
function disableFutureDate(date) {
  return date.getTime() > Date.now()
}

function validateHonorForm() {
  if (!honorForm.honorName.trim()) {
    ElMessage.warning('请输入荣誉名称')
    return false
  }
  if (!honorForm.honorLevel) {
    ElMessage.warning('请选择荣誉级别')
    return false
  }
  if (!honorForm.awardDate) {
    ElMessage.warning('请选择获奖日期')
    return false
  }
  return true
}

async function addHonor() {
  if (!validateHonorForm()) return
  if (!currentStudentId.value) {
    ElMessage.error('当前学生信息不存在')
    return
  }
  submitting.value = true
  try {
    await studentApi.addHonor(currentStudentId.value, {
      honorName: honorForm.honorName.trim(),
      honorLevel: honorForm.honorLevel,
      awardDate: honorForm.awardDate,
      certFile: honorForm.certFile.trim(),
    })
    ElMessage.success('新增荣誉成功')
    honorVisible.value = false
    viewDetail(currentStudentId.value)
  } finally {
    submitting.value = false
  }
}

async function deleteHonor(id) {
  await studentApi.deleteHonor(id)
  ElMessage.success('删除荣誉成功')
  viewDetail(currentStudentId.value)
}

onMounted(() => {
  loadData()
  loadDimensions()
})
</script>

<style scoped>
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 22px 0 12px;
  font-weight: 650;
}
</style>
