<template>
  <div class="app-page">
    <PageHeader title="学生信息" description="查看学生基本信息、荣誉记录及关联的党团流程和证明申请。">
      <template #actions>
        <el-button type="primary" plain :loading="exporting" @click="handleExport">导出学生名单</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="身份">
          <el-select v-model="query.roleLevels" multiple collapse-tags collapse-tags-tooltip clearable placeholder="全部" style="width: 200px" @change="handleSearch">
            <el-option label="普通学生" :value="4" />
            <el-option label="学生骨干" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="query.grades" multiple collapse-tags collapse-tags-tooltip clearable filterable placeholder="全部年级" style="width: 200px" @change="handleSearch">
            <el-option v-for="g in dimensions.grades" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="query.majors" multiple collapse-tags collapse-tags-tooltip clearable filterable placeholder="全部专业" style="width: 240px" @change="handleSearch">
            <el-option v-for="m in dimensions.majors" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="query.classNames" multiple collapse-tags collapse-tags-tooltip clearable filterable placeholder="全部班级" style="width: 220px" @change="handleSearch">
            <el-option v-for="c in dimensions.classNames" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel :title="`学生列表 (共 ${total} 人)`">
      <el-table :data="list" v-loading="loading" @row-click="row => viewDetail(row.id)" class="person-table">
        <el-table-column label="学生" min-width="220">
          <template #default="{ row }">
            <div class="person-info">
              <span class="person-name">
                {{ row.name || '-' }}
                <el-tag v-if="row.roleLevel === 3" type="warning" size="small" effect="plain">骨干</el-tag>
              </span>
              <span class="person-sub">{{ row.studentId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="班级信息" min-width="220">
          <template #default="{ row }">
            <div class="stack-cell">
              <span class="stack-main">{{ row.major || '-' }}</span>
              <span class="stack-sub">{{ [row.grade, row.className].filter(Boolean).join(' · ') || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="230">
          <template #default="{ row }">
            <div class="stack-cell">
              <span class="stack-main">{{ row.phone || '-' }}</span>
              <span class="stack-sub">{{ row.email || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="身份" width="110">
          <template #default="{ row }">
            <el-tag :type="row.roleLevel === 3 ? 'warning' : 'info'" effect="light" round>
              {{ row.roleLevel === 3 ? '学生骨干' : '普通学生' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="viewDetail(row.id)">查看画像</el-button>
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
      <!-- 画像头卡: 基本信息 + 汇总数字 -->
      <div class="profile-hero">
        <div class="profile-head">
          <div class="profile-name-row">
            <strong>{{ detail.name || '-' }}</strong>
            <span class="profile-id">{{ detail.studentId || '-' }}</span>
          </div>
          <span class="profile-class">{{ [detail.grade, detail.major, detail.className].filter(Boolean).join(' · ') || '-' }}</span>
        </div>
        <div class="profile-stats">
          <div class="profile-stat"><strong>{{ honors.length }}</strong><span>荣誉</span></div>
          <div class="profile-stat"><strong>{{ processes.length }}</strong><span>流程</span></div>
          <div class="profile-stat"><strong>{{ approvals.length }}</strong><span>申请</span></div>
        </div>
      </div>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户ID">{{ detail.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detail.idCard || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '-' }}</el-descriptions-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentApi, systemApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const submitting = ref(false)
const exporting = ref(false)
const detailVisible = ref(false)
const honorVisible = ref(false)
const list = ref([])
const total = ref(0)
const detail = ref({})
const honors = ref([])
const processes = ref([])
const approvals = ref([])
const currentStudentId = ref(null)
const query = reactive({ page: 1, size: 20, grades: [], majors: [], classNames: [], roleLevels: [] })
const dimensions = reactive({ grades: [], majors: [], classNames: [] })
const honorForm = reactive({ honorName: '', honorLevel: '', awardDate: '', certFile: '' })

// 多选数组以逗号拼接传给后端 (后端按 IN 查询)
function buildQueryParams() {
  const params = { page: query.page, size: query.size }
  if (query.grades.length) params.grade = query.grades.join(',')
  if (query.majors.length) params.major = query.majors.join(',')
  if (query.classNames.length) params.className = query.classNames.join(',')
  if (query.roleLevels.length) params.roleLevel = query.roleLevels.join(',')
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
  query.grades = []
  query.majors = []
  query.classNames = []
  query.roleLevels = []
  query.page = 1
  loadData()
}

// 导出学生名单: 按当前多选筛选导出 (后端只导学生 3/4, 骨干受数据隔离只导本班)
async function handleExport() {
  const studentRoles = query.roleLevels.filter(r => r === 3 || r === 4)
  let identityText = '普通学生 + 学生骨干'
  if (studentRoles.length === 1 && studentRoles[0] === 4) identityText = '仅普通学生'
  else if (studentRoles.length === 1 && studentRoles[0] === 3) identityText = '仅学生骨干'
  const dims = []
  if (query.grades.length) dims.push(`年级=${query.grades.join('/')}`)
  if (query.majors.length) dims.push(`专业=${query.majors.join('/')}`)
  if (query.classNames.length) dims.push(`班级=${query.classNames.join('/')}`)
  const dimsText = dims.length ? dims.join('，') : '不限'
  try {
    await ElMessageBox.confirm(
      `<div style="line-height:1.9">
        <p>将导出一份 Excel 学生名单：</p>
        <p>· <b>对象</b>：${identityText}，仅<b>启用状态</b></p>
        <p>· <b>筛选</b>：${dimsText}</p>
        <p style="color:#909399;font-size:13px;margin-top:6px">表格含"身份"列区分普通学生与学生骨干；支持一次选多个年级/专业/班级。</p>
      </div>`,
      '导出学生名单',
      { confirmButtonText: '确认导出', cancelButtonText: '取消', dangerouslyUseHTMLString: true },
    )
  } catch { return }

  exporting.value = true
  try {
    const params = {}
    if (query.grades.length) params.grade = query.grades.join(',')
    if (query.majors.length) params.major = query.majors.join(',')
    if (query.classNames.length) params.className = query.classNames.join(',')
    if (studentRoles.length) params.roleLevel = studentRoles.join(',')
    const res = await studentApi.exportStudents(params)
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const filename = parseFilename(res.headers['content-disposition']) || `学生名单_${todayStr()}.xlsx`
    triggerDownload(blob, filename)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

function parseFilename(cd) {
  if (!cd) return null
  const m = /filename\*=UTF-8''([^;]+)/i.exec(cd) || /filename="?([^";]+)"?/.exec(cd)
  return m ? decodeURIComponent(m[1]) : null
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
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

<style scoped lang="scss">
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 22px 0 12px;
  font-weight: 650;
}

/* ===== 人员行 ===== */
.person-table :deep(.el-table__row) {
  cursor: pointer;
}

.person-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 4px 0;
}

.person-name {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.person-sub {
  margin-top: 2px;
  color: var(--app-text-secondary);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.stack-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stack-main {
  color: var(--app-text-regular);
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stack-sub {
  margin-top: 2px;
  color: var(--app-text-secondary);
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 画像头卡 ===== */
.profile-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 18px 20px;
  color: #fff;
  border-radius: 12px;
  background:
    radial-gradient(circle at 92% -40%, rgba(255, 255, 255, 0.16), transparent 46%),
    var(--app-red-gradient);
  box-shadow: var(--app-shadow-red);
}

.profile-head {
  flex: 1;
  min-width: 0;
}

.profile-name-row {
  display: flex;
  align-items: baseline;
  gap: 10px;

  strong {
    font-family: var(--app-font-display);
    font-size: 19px;
    letter-spacing: 1px;
  }
}

.profile-id {
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.profile-class {
  display: block;
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 12.5px;
}

.profile-stats {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
}

.profile-stat {
  min-width: 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.18);

  strong {
    font-size: 17px;
    font-weight: 700;
  }

  span {
    margin-top: 2px;
    color: rgba(255, 255, 255, 0.72);
    font-size: 11.5px;
  }
}
</style>
