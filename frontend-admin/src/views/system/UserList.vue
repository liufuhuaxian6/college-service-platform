<template>
  <div class="app-page">
    <PageHeader title="用户管理" description="维护学生与教师账号。支持 Excel 批量导入、按筛选条件导出名单。">
      <template #actions>
        <el-button @click="downloadTemplate">下载导入模板</el-button>
        <el-upload action="" :before-upload="handleImport" :show-file-list="false" accept=".xlsx,.xls">
          <el-button type="primary" :loading="importing">Excel 导入</el-button>
        </el-upload>
        <el-button type="success" :loading="exporting" @click="handleExport">导出名单</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="年级">
          <el-input v-model="query.grade" clearable placeholder="如 2024" style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="query.major" clearable placeholder="输入专业" style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel :title="`用户列表 (共 ${total} 人)`">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="studentId" label="学号" width="140" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="roleLevel" label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleType(row.roleLevel)">{{ roleLabel(row.roleLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" width="100" />
        <el-table-column prop="major" label="专业" min-width="140" show-overflow-tooltip />
        <el-table-column prop="className" label="班级" width="130" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="editUser(row)">编辑</el-button>
            <el-button link type="warning" @click="setRole(row)">设置角色</el-button>
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

    <!-- 编辑用户 -->
    <el-dialog v-model="editVisible" title="编辑用户" width="520px">
      <el-form :model="editForm" label-width="86px">
        <el-form-item label="学号"><el-input v-model="editForm.studentId" disabled /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="年级"><el-input v-model="editForm.grade" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="editForm.major" /></el-form-item>
        <el-form-item label="班级"><el-input v-model="editForm.className" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="editForm.phone" /></el-form-item>
        <el-form-item label="导师"><el-input v-model="editForm.tutor" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果 -->
    <el-dialog v-model="importResultVisible" title="导入结果" width="600px">
      <el-result
        :icon="importResult.fail > 0 ? 'warning' : 'success'"
        :title="`成功 ${importResult.success} 条, 失败 ${importResult.fail} 条`"
        :sub-title="importResult.fail > 0 ? '以下行未导入, 修正后可重新提交' : '全部导入成功'"
      />
      <div v-if="importResult.errors?.length" class="error-list">
        <el-alert v-for="(err, idx) in importResult.errors" :key="idx" :title="err" type="warning" :closable="false" />
      </div>
      <p class="hint">默认密码为 <strong>123456</strong>，登录后请提醒用户立即修改。</p>
      <template #footer>
        <el-button type="primary" @click="importResultVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { systemApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const exporting = ref(false)
const editVisible = ref(false)
const importResultVisible = ref(false)
const importResult = ref({ success: 0, fail: 0, errors: [] })
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, grade: '', major: '' })
const editForm = reactive({
  id: null, studentId: '', name: '', grade: '', major: '',
  className: '', phone: '', tutor: '', status: 1,
})

const ROLE_LABELS = { 1: '院领导', 2: '管理老师', 3: '班团骨干', 4: '学生' }
function roleLabel(lv) { return ROLE_LABELS[lv] || `级别${lv}` }
function roleType(lv) {
  return { 1: 'danger', 2: 'warning', 3: '', 4: 'info' }[lv] || ''
}

function buildQueryParams() {
  const p = { page: query.page, size: query.size }
  if (query.grade) p.grade = query.grade
  if (query.major) p.major = query.major
  return p
}

async function loadData() {
  loading.value = true
  try {
    const res = await systemApi.getUserPage(buildQueryParams())
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

function handleSearch() {
  query.page = 1
  loadData()
}

async function editUser(row) {
  const res = await systemApi.getUserDetail(row.id)
  Object.assign(editForm, {
    id: res.data.id,
    studentId: res.data.studentId || '',
    name: res.data.name || '',
    grade: res.data.grade || '',
    major: res.data.major || '',
    className: res.data.className || '',
    phone: res.data.phone || '',
    tutor: res.data.tutor || '',
    status: res.data.status ?? 1,
  })
  editVisible.value = true
}

async function saveUser() {
  if (!editForm.name) { ElMessage.warning('姓名不能为空'); return }
  saving.value = true
  try {
    await systemApi.updateUser(editForm.id, {
      name: editForm.name,
      grade: editForm.grade,
      major: editForm.major,
      className: editForm.className,
      phone: editForm.phone,
      tutor: editForm.tutor,
      status: editForm.status,
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadData()
  } finally { saving.value = false }
}

async function setRole(row) {
  const { value } = await ElMessageBox.prompt(`设置 ${row.name} 的角色等级 (1-4)`, '设置角色', {
    inputValue: String(row.roleLevel),
    inputValidator: v => /^[1-4]$/.test(v) || '请输入 1-4',
  })
  await systemApi.setUserRole(row.id, { roleLevel: parseInt(value) })
  ElMessage.success('设置成功')
  loadData()
}

async function handleImport(file) {
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await systemApi.importUsers(formData)
    importResult.value = {
      success: res.data?.success || 0,
      fail: res.data?.fail || 0,
      errors: res.data?.errors || [],
    }
    importResultVisible.value = true
    loadData()
  } finally {
    importing.value = false
  }
  return false   // 阻止 el-upload 默认上传
}

async function handleExport() {
  exporting.value = true
  try {
    const params = {}
    if (query.grade) params.grade = query.grade
    if (query.major) params.major = query.major
    const res = await systemApi.exportStudents(params)
    // res 是完整 axios response (拦截器特判 blob)
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

function downloadTemplate() {
  // 前端用 xlsx 库生成空模板, 列名与后端 StudentImportRow 的 @ExcelProperty 完全一致
  const headers = ['学号', '姓名', '年级', '专业', '班级', '手机号', '身份证号']
  const sample = [['2024999999', '示例同学', '2024', '计算机科学', '2024级1班', '13800000000', '110101200001011234']]
  const ws = XLSX.utils.aoa_to_sheet([headers, ...sample])
  ws['!cols'] = [{ wch: 14 }, { wch: 10 }, { wch: 8 }, { wch: 16 }, { wch: 14 }, { wch: 14 }, { wch: 22 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '学生名单')
  XLSX.writeFile(wb, '学生名单导入模板.xlsx')
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

onMounted(loadData)
</script>

<style scoped>
.error-list {
  margin: 12px 0;
  max-height: 240px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hint {
  margin-top: 12px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
</style>
