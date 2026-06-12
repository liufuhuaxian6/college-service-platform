<template>
  <div class="app-page">
    <PageHeader
      title="通知群发"
      description="按年级 / 专业 / 班级精准推送, 支持站内消息 + RUC 邮箱发送, 群发后 24 小时内可撤回"
    />

    <el-tabs v-model="activeTab" class="broadcast-tabs">
      <!-- ============== 群发新通知: 左内容 / 右发送设置 双栏 ============== -->
      <el-tab-pane label="群发新通知" name="compose">
        <el-row :gutter="16">
          <!-- 左栏: 通知内容 -->
          <el-col :span="14">
            <DataPanel title="通知内容" description="标题与正文为必填，标签用于学生端筛选">
              <el-form :model="form" label-position="top">
                <el-form-item label="通知标题" required>
                  <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如:计科 2024 级实习宣讲会" />
                </el-form-item>
                <el-form-item label="正文" required>
                  <el-input
                    v-model="form.content"
                    type="textarea"
                    :rows="9"
                    maxlength="1000"
                    show-word-limit
                    placeholder="详细说明时间地点要求,可包含报名链接"
                  />
                </el-form-item>
                <el-form-item label="标签">
                  <el-select
                    v-model="form.tags"
                    multiple
                    filterable
                    allow-create
                    default-first-option
                    placeholder="按回车添加,如 就业 / 实习 / 计算机类"
                    style="width: 100%"
                  >
                    <el-option v-for="t in commonTags" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
                <el-row :gutter="14">
                  <el-col :span="9">
                    <el-form-item label="来源">
                      <el-select v-model="form.source" placeholder="可选" clearable style="width: 100%">
                        <el-option v-for="s in sourceOptions" :key="s" :label="s" :value="s" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="15">
                    <el-form-item label="原文链接">
                      <el-input v-model="form.sourceUrl" placeholder="公众号文章地址,选填" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </DataPanel>
          </el-col>

          <!-- 右栏: 接收对象 + 渠道 + 预览 + 提交 -->
          <el-col :span="10">
            <DataPanel title="发送设置" description="选定接收对象后可先预览人数">
              <el-form :model="form.filter" label-position="top">
                <el-form-item label="接收对象" required>
                  <el-checkbox-group v-model="form.filter.roles" class="role-group">
                    <div class="role-col">
                      <div class="role-col__title">学生</div>
                      <el-checkbox :value="4">普通学生</el-checkbox>
                      <el-checkbox :value="3">学生骨干</el-checkbox>
                    </div>
                    <div class="role-col">
                      <div class="role-col__title">教职工</div>
                      <el-checkbox :value="2">老师 / 辅导员</el-checkbox>
                      <el-checkbox :value="1">院领导</el-checkbox>
                    </div>
                  </el-checkbox-group>
                </el-form-item>
                <el-alert
                  v-if="hasStaffRole && hasStudentDimFilter"
                  type="info"
                  show-icon
                  :closable="false"
                  class="dim-alert"
                  title="年级 / 专业 / 班级仅对学生生效; 勾选的老师与院领导整组接收本次通知"
                />

                <template v-if="hasStudentRole">
                  <el-form-item label="年级">
                    <el-select v-model="form.filter.grades" multiple filterable allow-create placeholder="留空 = 不限 (作用于学生)" style="width: 100%">
                      <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="专业">
                    <el-select v-model="form.filter.majors" multiple filterable allow-create placeholder="留空 = 不限 (作用于学生)" style="width: 100%">
                      <el-option v-for="m in majorOptions" :key="m" :label="m" :value="m" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="班级">
                    <el-select v-model="form.filter.classNames" multiple filterable allow-create placeholder="留空 = 不限 (作用于学生)" style="width: 100%">
                      <el-option v-for="c in classOptions" :key="c" :label="c" :value="c" />
                    </el-select>
                  </el-form-item>
                </template>
                <el-form-item v-else label="年级 / 专业 / 班级">
                  <span class="dim-disabled-tip">当前只选了教职工, 该筛选不适用</span>
                </el-form-item>

                <el-form-item label="发送渠道">
                  <el-checkbox-group v-model="form.channels" class="channel-group">
                    <el-checkbox value="system" disabled>站内消息 (必发)</el-checkbox>
                    <el-checkbox value="email">邮件 (RUC 邮箱)</el-checkbox>
                    <el-checkbox value="sms_sim">短信 (演示模拟)</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </el-form>

              <!-- 人数预览 -->
              <div class="preview-box">
                <div class="preview-head">
                  <span class="preview-label">预计送达</span>
                  <el-button size="small" @click="previewCount" :loading="previewing">预览人数</el-button>
                </div>
                <div v-if="previewResult" class="preview-result">
                  <span class="preview-num">{{ previewResult.targetCount }}</span>
                  <span class="preview-unit">人</span>
                  <span class="preview-breakdown">
                    <template v-if="previewResult.studentCount">普通学生 {{ previewResult.studentCount }}</template>
                    <template v-if="previewResult.cadreCount"> · 骨干 {{ previewResult.cadreCount }}</template>
                    <template v-if="previewResult.teacherCount"> · 老师 {{ previewResult.teacherCount }}</template>
                    <template v-if="previewResult.leadershipCount"> · 院领导 {{ previewResult.leadershipCount }}</template>
                  </span>
                </div>
                <div v-else class="preview-empty">调整筛选后点击预览, 确认覆盖范围再群发</div>
              </div>

              <div class="action-row">
                <el-button @click="resetForm">重置</el-button>
                <el-button type="primary" :loading="submitting" @click="handleSubmit">确认群发</el-button>
              </div>
            </DataPanel>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ============== 广播历史 ============== -->
      <el-tab-pane label="广播历史" name="history">
        <DataPanel title="历史记录">
          <el-table :data="historyList" v-loading="historyLoading" stripe>
            <el-table-column label="时间" width="150" :formatter="row => formatDateTime(row.createdAt)" />
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column label="标签" width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag v-for="t in splitTags(row.tags)" :key="t" size="small" style="margin-right: 4px">{{ t }}</el-tag>
                <span v-if="!row.tags">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="100" />
            <el-table-column label="目标 / 已读" width="120">
              <template #default="{ row }">
                {{ row.targetCount || 0 }} / {{ row.sentCount || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="邮件" width="80">
              <template #default="{ row }">{{ row.emailSent || 0 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <StatusTag v-if="row.withdrawn" type="danger" text="已撤回" />
                <StatusTag v-else-if="canWithdraw(row)" type="warning" text="可撤回" />
                <StatusTag v-else type="success" text="生效中" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-popconfirm
                  v-if="canWithdraw(row)"
                  title="撤回会删除目标用户中所有未读的该通知, 确定?"
                  @confirm="handleWithdraw(row.id)"
                >
                  <template #reference>
                    <el-button link type="danger">撤回</el-button>
                  </template>
                </el-popconfirm>
                <span v-else style="color: var(--app-text-secondary)">—</span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            v-model:current-page="historyQuery.page"
            v-model:page-size="historyQuery.size"
            :total="historyTotal"
            layout="total, prev, pager, next"
            @current-change="loadHistory"
          />
        </DataPanel>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { notifyApi, systemApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import PageHeader from '@/components/common/PageHeader.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const activeTab = ref('compose')

// ===== Compose =====
const commonTags = ['就业', '实习', '奖学金', '入党', '入团', '校历', '后勤', '安全', '学籍', '计算机类']
const sourceOptions = ['学院', '就业办', '后勤处', '保卫处', '教务处', '研究生院', '团委', '其他']
// 年级/专业/班级选项: onMounted 时从真实学生数据里抽 distinct, 而不是硬编码
// 这样不会出现"前端写了'计算机科学'但 DB 里是'计算机科学与技术'对不上"导致预览 0 人的坑
const gradeOptions = ref([])
// combos: [{ grade, major, className }] 真实组合, 用于年级/专业级联过滤班级
const combos = ref([])

async function loadDimensions() {
  try {
    const res = await systemApi.getDimensions()
    gradeOptions.value = res.data?.grades || []
    combos.value = res.data?.combos || []
  } catch (e) {
    // 失败时留空, 用户仍可手动 allow-create
  }
}

const form = reactive({
  title: '',
  content: '',
  tags: [],
  source: '',
  sourceUrl: '',
  channels: ['system'],
  filter: {
    roles: [4],         // 角色等级: 4=普通学生, 3=学生骨干, 2=老师, 1=院领导
    grades: [],
    majors: [],
    classNames: [],
  },
})

const previewResult = ref(null)
const previewing = ref(false)
const submitting = ref(false)

// 学生类角色 (普通学生/学生骨干) 才有年级/专业/班级属性
const hasStudentRole = computed(() => form.filter.roles.some(r => r === 3 || r === 4))
const hasStaffRole = computed(() => form.filter.roles.some(r => r === 1 || r === 2))
const hasStudentDimFilter = computed(() =>
  (form.filter.grades?.length || 0) > 0 ||
  (form.filter.majors?.length || 0) > 0 ||
  (form.filter.classNames?.length || 0) > 0
)

// 专业选项: 选了年级后只显示这些年级下的专业 (没选年级则全部)
const majorOptions = computed(() => {
  const grades = form.filter.grades || []
  const pool = grades.length ? combos.value.filter(c => grades.includes(c.grade)) : combos.value
  return [...new Set(pool.map(c => c.major).filter(Boolean))].sort()
})
// 班级选项: 受已选年级 + 专业级联约束 (这样选了 2024 级后, 班级里不会再冒出 2023 级 1 班)
const classOptions = computed(() => {
  const grades = form.filter.grades || []
  const majors = form.filter.majors || []
  let pool = combos.value
  if (grades.length) pool = pool.filter(c => grades.includes(c.grade))
  if (majors.length) pool = pool.filter(c => majors.includes(c.major))
  return [...new Set(pool.map(c => c.className).filter(Boolean))].sort()
})

// 年级/专业变化后, 把已选但已不在可选项里的专业/班级剔除, 避免残留无效筛选.
// 注意: 仅在确有项被剔除时才赋值 —— classOptions 依赖 form.filter.majors,
// 若每次都赋新数组会反复触发该 watch 形成死循环 (表现为页面卡死).
watch([majorOptions, classOptions], () => {
  const majors = (form.filter.majors || []).filter(m => majorOptions.value.includes(m))
  if (majors.length !== (form.filter.majors?.length || 0)) form.filter.majors = majors
  const classes = (form.filter.classNames || []).filter(c => classOptions.value.includes(c))
  if (classes.length !== (form.filter.classNames?.length || 0)) form.filter.classNames = classes
})

watch(
  () => [form.filter.roles, form.filter.grades, form.filter.majors, form.filter.classNames],
  () => { previewResult.value = null },
  { deep: true },
)

// 选了纯教职工时, 清掉残留的学生维度筛选, 避免随请求误发
watch(hasStudentRole, (has) => {
  if (!has) {
    form.filter.grades = []
    form.filter.majors = []
    form.filter.classNames = []
  }
})

async function previewCount() {
  if (!form.filter.roles.length) { ElMessage.warning('请至少选择一个接收对象'); return }
  previewing.value = true
  try {
    const res = await notifyApi.previewTargets({
      roles: form.filter.roles,
      grades: form.filter.grades,
      majors: form.filter.majors,
      classNames: form.filter.classNames,
    })
    const d = res.data || {}
    previewResult.value = {
      targetCount: d.targetCount || 0,
      studentCount: d.studentCount || 0,
      cadreCount: d.cadreCount || 0,
      teacherCount: d.teacherCount || 0,
      leadershipCount: d.leadershipCount || 0,
    }
  } finally {
    previewing.value = false
  }
}

async function handleSubmit() {
  if (!form.title.trim()) { ElMessage.warning('请填写通知标题'); return }
  if (!form.content.trim()) { ElMessage.warning('请填写通知正文'); return }
  if (!form.filter.roles.length) { ElMessage.warning('请至少选择一个接收对象'); return }

  submitting.value = true
  try {
    const res = await notifyApi.broadcast({
      title: form.title.trim(),
      content: form.content.trim(),
      tags: form.tags,
      source: form.source || null,
      sourceUrl: form.sourceUrl || null,
      channels: form.channels,
      filter: form.filter,
    })
    const d = res.data || {}
    ElMessage.success(`已群发 ${d.sentCount || 0}/${d.targetCount || 0} 人, 邮件 ${d.emailSent || 0} 封`)
    resetForm()
    activeTab.value = 'history'
    loadHistory()
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.tags = []
  form.source = ''
  form.sourceUrl = ''
  form.channels = ['system']
  form.filter.roles = [4]
  form.filter.grades = []
  form.filter.majors = []
  form.filter.classNames = []
  previewResult.value = null
}

// ===== History =====
const historyList = ref([])
const historyTotal = ref(0)
const historyLoading = ref(false)
const historyQuery = reactive({ page: 1, size: 20 })

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await notifyApi.getBroadcastPage(historyQuery)
    historyList.value = res.data?.records || []
    historyTotal.value = res.data?.total || 0
  } finally {
    historyLoading.value = false
  }
}

function canWithdraw(row) {
  if (row.withdrawn) return false
  if (!row.createdAt) return false
  const created = new Date(row.createdAt).getTime()
  return Date.now() - created < 24 * 3600 * 1000
}

function splitTags(tags) {
  if (!tags) return []
  return tags.split(',').map(s => s.trim()).filter(Boolean)
}

async function handleWithdraw(id) {
  const res = await notifyApi.withdrawBroadcast(id)
  ElMessage.success(`已撤回, 删除 ${res.data?.removedCount || 0} 条未读通知`)
  loadHistory()
}

onMounted(() => {
  loadHistory()
  loadDimensions()
})
</script>

<style scoped>
.broadcast-tabs {
  margin-top: 8px;
}

.role-group {
  display: flex;
  gap: 40px;
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--app-border-light);
  border-radius: 10px;
  background: var(--app-muted);
  box-sizing: border-box;
}

.role-col {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.role-col__title {
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-bottom: 2px;
}

.channel-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 8px 14px;
  border: 1px solid var(--app-border-light);
  border-radius: 10px;
  background: var(--app-muted);
  box-sizing: border-box;
}

.dim-alert {
  margin-bottom: 16px;
}

.dim-disabled-tip {
  color: var(--app-text-secondary);
  font-size: 13px;
}

/* 人数预览块 */
.preview-box {
  margin-top: 4px;
  padding: 14px 16px;
  border: 1px dashed var(--app-primary-soft);
  border-radius: 10px;
  background: var(--app-primary-light);
}

.preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.preview-label {
  color: var(--app-primary-deep);
  font-size: 13px;
  font-weight: 600;
}

.preview-result {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 10px;
}

.preview-num {
  color: var(--app-primary);
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.preview-unit {
  color: var(--app-primary-deep);
  font-size: 13px;
}

.preview-breakdown {
  margin-left: 8px;
  color: var(--app-text-secondary);
  font-size: 12.5px;
}

.preview-empty {
  margin-top: 10px;
  color: var(--app-text-secondary);
  font-size: 12.5px;
}

.action-row {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--app-border-light);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
