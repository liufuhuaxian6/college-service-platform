<template>
  <div class="app-page">
    <PageHeader title="用户管理" description="维护学生与教师账号。支持新增 / Excel 批量导入（模板可指定身份）；“导出用户”按当前身份筛选导出（含老师 / 院领导 / 状态列）。">
      <template #actions>
        <el-button @click="downloadTemplate">下载导入模板</el-button>
        <el-upload action="" :before-upload="handleImport" :show-file-list="false" accept=".xlsx,.xls">
          <el-button :loading="importing">Excel 导入</el-button>
        </el-upload>
        <el-button type="primary" plain :loading="exporting" @click="handleExport">导出用户</el-button>
        <el-button type="primary" @click="showCreateDialog">新增用户</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="身份">
          <el-select v-model="query.roleLevels" multiple collapse-tags collapse-tags-tooltip clearable placeholder="全部身份" style="width: 220px" @change="handleSearch">
            <el-option label="院领导" :value="1" />
            <el-option label="管理老师" :value="2" />
            <el-option label="学生骨干" :value="3" />
            <el-option label="普通学生" :value="4" />
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="canManage(row)">
              <el-button link type="primary" @click="editUser(row)">编辑</el-button>
              <el-button v-if="isLeader" link type="warning" @click="setRole(row)">设置角色</el-button>
            </template>
            <el-tooltip v-else content="该账号权限不低于你，无法管理" placement="top">
              <span class="no-perm">不可管理</span>
            </el-tooltip>
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

    <!-- 新增用户 -->
    <el-dialog v-model="createVisible" title="新增用户" width="520px">
      <el-form :model="createForm" label-width="86px">
        <el-form-item label="学号" required><el-input v-model="createForm.studentId" placeholder="登录账号 / 学号" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="身份" required>
          <el-select v-model="createForm.roleLevel" style="width: 100%">
            <el-option v-for="r in creatableRoles" :key="r" :label="roleLabel(r)" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级"><el-input v-model="createForm.grade" placeholder="学生填写，如 2024" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="createForm.major" /></el-form-item>
        <el-form-item label="班级"><el-input v-model="createForm.className" /></el-form-item>
        <el-form-item label="导师"><el-input v-model="createForm.tutor" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="createForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" placeholder="为空时默认 学号@ruc.edu.cn" /></el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" title="初始密码为 123456，登录后请提醒用户尽快修改。" style="margin-top: 4px" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { systemApi } from '@/api'
import { useUserStore } from '@/stores/user'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'

const userStore = useUserStore()
// 只能管理权限严格低于自己的账号 (角色等级数字越小权限越高)
const isLeader = computed(() => userStore.roleLevel === 1)
function canManage(row) {
  return row.roleLevel != null && userStore.roleLevel < row.roleLevel
}
// 可创建的身份: 仅权限严格低于操作者的角色
const creatableRoles = computed(() => [1, 2, 3, 4].filter(r => r > userStore.roleLevel))

// ===== 新增用户 =====
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  studentId: '', name: '', roleLevel: 4, grade: '', major: '', className: '', tutor: '', phone: '', email: '',
})

function showCreateDialog() {
  Object.assign(createForm, {
    studentId: '', name: '', grade: '', major: '', className: '', tutor: '', phone: '', email: '',
    roleLevel: creatableRoles.value[creatableRoles.value.length - 1] || 4, // 默认普通学生
  })
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.studentId.trim()) { ElMessage.warning('请输入学号'); return }
  if (!createForm.name.trim()) { ElMessage.warning('请输入姓名'); return }
  creating.value = true
  try {
    await systemApi.createUser({
      studentId: createForm.studentId.trim(),
      name: createForm.name.trim(),
      roleLevel: createForm.roleLevel,
      grade: createForm.grade || null,
      major: createForm.major || null,
      className: createForm.className || null,
      tutor: createForm.tutor || null,
      phone: createForm.phone || null,
      email: createForm.email || null,
    })
    ElMessage.success('新增成功，初始密码 123456')
    createVisible.value = false
    loadData()
    loadDimensions()
  } finally {
    creating.value = false
  }
}

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const exporting = ref(false)
const editVisible = ref(false)
const importResultVisible = ref(false)
const importResult = ref({ success: 0, fail: 0, errors: [] })
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, grades: [], majors: [], classNames: [], roleLevels: [] })
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

// 多选数组以逗号拼接传给后端 (后端按 IN 查询), 兼容单值
function buildQueryParams() {
  const p = { page: query.page, size: query.size }
  if (query.grades.length) p.grade = query.grades.join(',')
  if (query.majors.length) p.major = query.majors.join(',')
  if (query.classNames.length) p.className = query.classNames.join(',')
  if (query.roleLevels.length) p.roleLevel = query.roleLevels.join(',')
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
  query.grades = []
  query.majors = []
  query.classNames = []
  query.roleLevels = []
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
  // 导出全部角色的用户(按当前身份/年级/专业/班级筛选), 含角色与状态列.
  const roleText = query.roleLevels.length
    ? query.roleLevels.map(roleLabel).join(' / ')
    : '全部角色'
  const dims = []
  if (query.grades.length) dims.push(`年级=${query.grades.join('/')}`)
  if (query.majors.length) dims.push(`专业=${query.majors.join('/')}`)
  if (query.classNames.length) dims.push(`班级=${query.classNames.join('/')}`)
  const dimsText = dims.length ? dims.join('，') : '不限'

  try {
    await ElMessageBox.confirm(
      `<div style="line-height:1.9">
        <p>将导出一份 Excel 用户名单，范围如下：</p>
        <p>· <b>身份</b>：${roleText}</p>
        <p>· <b>筛选</b>：${dimsText}</p>
        <p style="color:#909399;font-size:13px;margin-top:6px">表格含“角色 / 邮箱 / 状态”列，启用与禁用账号都会导出；如只想导学生名单，请到“学生信息”页导出。</p>
      </div>`,
      '导出用户名单',
      { confirmButtonText: '确认导出', cancelButtonText: '取消', dangerouslyUseHTMLString: true },
    )
  } catch {
    return // 用户取消
  }

  exporting.value = true
  try {
    const params = {}
    if (query.grades.length) params.grade = query.grades.join(',')
    if (query.majors.length) params.major = query.majors.join(',')
    if (query.classNames.length) params.className = query.classNames.join(',')
    if (query.roleLevels.length) params.roleLevel = query.roleLevels.join(',')   // 全角色
    const res = await systemApi.exportUsers(params)
    // res 是完整 axios response (拦截器特判 blob)
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const filename = parseFilename(res.headers['content-disposition']) || `用户名单_${todayStr()}.xlsx`
    triggerDownload(blob, filename)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

function downloadTemplate() {
  // 前端用 xlsx 库生成空模板, 列名与后端 StudentImportRow 的 @ExcelProperty 完全一致
  // "身份"列可留空(默认普通学生), 支持 普通学生/学生骨干/老师/院领导 (受导入者权限约束)
  const headers = ['学号', '姓名', '身份', '年级', '专业', '班级', '手机号', '身份证号']
  const sample = [
    ['2024999999', '示例学生', '普通学生', '2024', '计算机科学', '2024级1班', '13800000000', '110101200001011234'],
    ['T2024999', '示例老师', '老师', '', '', '', '13900000000', ''],
  ]
  const ws = XLSX.utils.aoa_to_sheet([headers, ...sample])
  ws['!cols'] = [{ wch: 14 }, { wch: 10 }, { wch: 10 }, { wch: 8 }, { wch: 16 }, { wch: 14 }, { wch: 14 }, { wch: 22 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '用户名单')
  XLSX.writeFile(wb, '用户导入模板.xlsx')
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

.no-perm {
  color: var(--app-text-placeholder);
  font-size: 13px;
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
