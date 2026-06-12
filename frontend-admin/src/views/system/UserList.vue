<template>
  <div class="app-page">
    <PageHeader title="用户管理" description="维护学生与教师账号。支持 Excel 批量导入；“导出学生名单”导出启用状态的学生（含普通学生与学生骨干，不含老师 / 院领导），可按年级 / 专业 / 班级筛选。">
      <template #actions>
        <el-button @click="downloadTemplate">下载导入模板</el-button>
        <el-upload action="" :before-upload="handleImport" :show-file-list="false" accept=".xlsx,.xls">
          <el-button type="primary" :loading="importing">Excel 导入</el-button>
        </el-upload>
        <el-button type="success" :loading="exporting" @click="handleExport">导出学生名单</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="身份">
          <el-select v-model="query.roleLevel" clearable placeholder="全部身份" style="width: 130px" @change="handleSearch">
            <el-option label="院领导" :value="1" />
            <el-option label="管理老师" :value="2" />
            <el-option label="学生骨干" :value="3" />
            <el-option label="普通学生" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-select v-model="query.grade" clearable filterable placeholder="全部年级" style="width: 130px" @change="handleSearch">
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

    <DataPanel :title="`用户列表 (共 ${total} 人)`">
      <el-table :data="list" v-loading="loading">
        <el-table-column label="用户" min-width="210">
          <template #default="{ row }">
            <div class="person-info">
              <span class="person-name">{{ row.name || '-' }}</span>
              <span class="person-sub">{{ row.studentId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleType(row.roleLevel)" effect="light" round>{{ roleLabel(row.roleLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="班级信息" min-width="200">
          <template #default="{ row }">
            <div class="stack-cell">
              <span class="stack-main">{{ row.major || '—' }}</span>
              <span class="stack-sub">{{ [row.grade, row.className].filter(Boolean).join(' · ') || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="220">
          <template #default="{ row }">
            <div class="stack-cell">
              <span class="stack-main">{{ row.phone || '—' }}</span>
              <span class="stack-sub">{{ row.email || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <span class="status-dot" :class="row.status === 1 ? 'on' : 'off'" />
            {{ row.status === 1 ? '启用' : '禁用' }}
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
        <el-form-item label="邮箱"><el-input v-model="editForm.email" placeholder="为空时默认使用 学号@ruc.edu.cn" /></el-form-item>
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
const query = reactive({ page: 1, size: 20, grade: '', major: '', className: '', roleLevel: null })
const dimensions = reactive({ grades: [], majors: [], classNames: [] })
const editForm = reactive({
  id: null, studentId: '', name: '', grade: '', major: '',
  className: '', phone: '', email: '', tutor: '', status: 1,
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
  if (query.className) p.className = query.className
  if (query.roleLevel != null) p.roleLevel = query.roleLevel
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
    email: res.data.email || '',
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
      email: editForm.email,
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
    loadDimensions()   // 导入可能引入新的年级/专业/班级, 刷新下拉项
  } finally {
    importing.value = false
  }
  return false   // 阻止 el-upload 默认上传
}

async function handleExport() {
  // 导出仅覆盖学生(普通学生4 + 学生骨干3), 不含老师/院领导, 不含已禁用账号.
  // 身份筛选若选了 3/4 则只导对应那类; 选了老师/院领导则给出提示(导出不支持).
  const isStudentRole = query.roleLevel === 3 || query.roleLevel === 4
  const isStaffRole = query.roleLevel === 1 || query.roleLevel === 2

  // 身份维度文案
  let identityText
  if (query.roleLevel === 4) identityText = '仅普通学生'
  else if (query.roleLevel === 3) identityText = '仅学生骨干'
  else identityText = '普通学生 + 学生骨干'

  // 其它筛选维度文案
  const dims = []
  if (query.grade) dims.push(`年级=${query.grade}`)
  if (query.major) dims.push(`专业=${query.major}`)
  if (query.className) dims.push(`班级=${query.className}`)
  const dimsText = dims.length ? dims.join('，') : '不限'

  const staffWarn = isStaffRole
    ? `<p style="color:#E6A23C;margin-top:6px">⚠ 当前“身份”筛选选了老师/院领导，但导出只支持学生，<b>该身份筛选将被忽略</b>，仍导出普通学生 + 学生骨干。</p>`
    : ''

  try {
    await ElMessageBox.confirm(
      `<div style="line-height:1.9">
        <p>将导出一份 Excel 学生名单，范围如下：</p>
        <p>· <b>对象</b>：${isStudentRole ? identityText : '学生（普通学生 + 学生骨干）'}，仅<b>启用状态</b>账号</p>
        <p>· <b>筛选</b>：${dimsText}</p>
        <p>· <b>不含</b>：老师、院领导、已禁用账号</p>
        <p style="color:#909399;font-size:13px;margin-top:6px">表格含“身份”列，可区分普通学生与学生骨干；如需缩小范围，请先在上方设置筛选条件。</p>
        ${staffWarn}
      </div>`,
      '导出学生名单',
      { confirmButtonText: '确认导出', cancelButtonText: '取消', dangerouslyUseHTMLString: true },
    )
  } catch {
    return // 用户取消
  }

  exporting.value = true
  try {
    const params = {}
    if (query.grade) params.grade = query.grade
    if (query.major) params.major = query.major
    if (query.className) params.className = query.className
    if (isStudentRole) params.roleLevel = query.roleLevel   // 老师/院领导身份不传, 后端默认导学生
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

onMounted(() => {
  loadData()
  loadDimensions()
})
</script>

<style scoped lang="scss">
/* ===== 人员行 ===== */
.person-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 4px 0;
}

.person-name {
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

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;

  &.on {
    background: var(--app-success);
    box-shadow: 0 0 0 3px var(--app-success-bg);
  }

  &.off {
    background: var(--app-danger);
    box-shadow: 0 0 0 3px var(--app-danger-bg);
  }
}

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
