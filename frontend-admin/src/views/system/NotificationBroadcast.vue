<template>
  <div class="app-page">
    <PageHeader
      title="通知群发"
      description="按年级 / 专业 / 班级精准推送, 支持站内消息 + RUC 邮箱发送, 群发后 24 小时内可撤回"
    />

    <el-tabs v-model="activeTab" class="broadcast-tabs">
      <!-- ============== 群发新通知 ============== -->
      <el-tab-pane label="群发新通知" name="compose">
        <DataPanel title="通知内容">
          <el-form :model="form" label-width="100px" label-position="right">
            <el-form-item label="通知标题" required>
              <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如:计科 2024 级实习宣讲会" />
            </el-form-item>
            <el-form-item label="正文" required>
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="5"
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
            <el-form-item label="来源">
              <el-select v-model="form.source" placeholder="可选" clearable style="width: 220px">
                <el-option v-for="s in sourceOptions" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item label="原文链接">
              <el-input v-model="form.sourceUrl" placeholder="公众号文章地址,选填" />
            </el-form-item>
          </el-form>
        </DataPanel>

        <DataPanel title="目标受众" style="margin-top: 16px">
          <el-form :model="form.filter" label-width="100px" label-position="right">
            <el-form-item label="角色">
              <el-radio-group v-model="form.filter.roleLevel">
                <el-radio :value="4">仅学生</el-radio>
                <el-radio :value="null">全部 (含老师/骨干)</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="年级">
              <el-select v-model="form.filter.grades" multiple filterable allow-create placeholder="留空 = 不限" style="width: 100%">
                <el-option v-for="g in gradeOptions" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="专业">
              <el-select v-model="form.filter.majors" multiple filterable allow-create placeholder="留空 = 不限" style="width: 100%">
                <el-option v-for="m in majorOptions" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="班级">
              <el-select v-model="form.filter.classNames" multiple filterable allow-create placeholder="留空 = 不限" style="width: 100%">
                <el-option v-for="c in classOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="发送渠道">
              <el-checkbox-group v-model="form.channels">
                <el-checkbox value="system" disabled>站内消息 (必发)</el-checkbox>
                <el-checkbox value="email">邮件 (RUC 邮箱)</el-checkbox>
                <el-checkbox value="sms_sim">短信 (演示模拟)</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item>
              <div class="preview-row">
                <el-button @click="previewCount" :loading="previewing">预览目标人数</el-button>
                <span v-if="previewResult !== null" class="preview-tip">
                  按当前筛选, 共匹配 <b>{{ previewResult }}</b> 人
                </span>
              </div>
            </el-form-item>
          </el-form>
        </DataPanel>

        <div class="action-row">
          <el-button @click="resetForm">重置</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">确认群发</el-button>
        </div>
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
import { ref, reactive, onMounted, watch } from 'vue'
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
const majorOptions = ref([])
const classOptions = ref([])

async function loadDimensions() {
  try {
    const res = await systemApi.getUserPage({ page: 1, size: 500 })
    const students = (res.data?.records || []).filter(u => u.roleLevel === 4)
    gradeOptions.value = [...new Set(students.map(s => s.grade).filter(Boolean))].sort()
    majorOptions.value = [...new Set(students.map(s => s.major).filter(Boolean))].sort()
    classOptions.value = [...new Set(students.map(s => s.className).filter(Boolean))].sort()
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
    roleLevel: 4,
    grades: [],
    majors: [],
    classNames: [],
  },
})

const previewResult = ref(null)
const previewing = ref(false)
const submitting = ref(false)

watch(
  () => [form.filter.roleLevel, form.filter.grades, form.filter.majors, form.filter.classNames],
  () => { previewResult.value = null },
  { deep: true },
)

async function previewCount() {
  previewing.value = true
  try {
    const res = await notifyApi.previewTargets({
      roleLevel: form.filter.roleLevel,
      grades: form.filter.grades,
      majors: form.filter.majors,
      classNames: form.filter.classNames,
    })
    previewResult.value = res.data?.targetCount || 0
  } finally {
    previewing.value = false
  }
}

async function handleSubmit() {
  if (!form.title.trim()) { ElMessage.warning('请填写通知标题'); return }
  if (!form.content.trim()) { ElMessage.warning('请填写通知正文'); return }

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
  form.filter.roleLevel = 4
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
.preview-row {
  display: flex;
  align-items: center;
  gap: 16px;
}
.preview-tip {
  color: var(--app-text-secondary);
  font-size: 13px;
}
.preview-tip b {
  color: var(--app-primary);
  font-size: 16px;
  font-weight: 700;
  margin: 0 2px;
}
.action-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
