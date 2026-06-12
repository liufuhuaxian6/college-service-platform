<template>
  <div class="app-page">
    <!-- ===== 欢迎横幅 ===== -->
    <div class="welcome-banner">
      <RucSeal class="banner-watermark" :size="220" light />
      <div class="welcome-main">
        <span class="welcome-date">{{ todayText }}</span>
        <h1>{{ greeting }}，{{ userStore.name || '老师' }}</h1>
        <p>今日待审批 {{ cards[2].value === '-' ? 0 : cards[2].value }} 件 · 进行中党团流程 {{ cards[3].value === '-' ? 0 : cards[3].value }} 个</p>
      </div>
      <RucSeal class="welcome-seal" :size="78" disc />
    </div>

    <!-- ===== KPI ===== -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <MetricCard :label="card.label" :value="card.value" :icon="card.icon" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-spaced">
      <!-- ===== 左列: 图表 ===== -->
      <el-col :span="16">
        <DataPanel title="近 7 日活动趋势" description="审批申请与系统通知数量">
          <div v-show="hasTrendData" ref="trendLineRef" class="chart-box chart-box--trend" />
          <div v-if="!hasTrendData" class="chart-empty chart-empty--trend">
            <EmptyState title="近 7 日无活动" description="审批申请与通知数据会在产生后实时累计" />
          </div>
        </DataPanel>

        <el-row :gutter="16" class="row-spaced">
          <el-col :span="12">
            <DataPanel title="审批状态分布" description="所有证明申请按状态占比">
              <div v-show="hasApprovalData" ref="approvalPieRef" class="chart-box" />
              <div v-if="!hasApprovalData" class="chart-empty">
                <EmptyState title="暂无审批数据" description="学生提交申请后展示各状态占比" />
              </div>
            </DataPanel>
          </el-col>
          <el-col :span="12">
            <DataPanel title="党团流程分布" description="每个模板下的实例数量">
              <div v-show="hasPartyData" ref="partyBarRef" class="chart-box" />
              <div v-if="!hasPartyData" class="chart-empty">
                <EmptyState title="暂无流程实例" description="创建入党/入团流程后展示分布" />
              </div>
            </DataPanel>
          </el-col>
        </el-row>
      </el-col>

      <!-- ===== 右列: 待办 + 快捷入口 ===== -->
      <el-col :span="8">
        <DataPanel title="待办审批" description="可直接处理, 或点击进入审批中心">
          <template #actions>
            <el-button link type="primary" @click="goCenter">全部 ›</el-button>
          </template>
          <div v-if="todo.length" class="todo-list">
            <div v-for="item in todo" :key="item.id" class="todo-item">
              <div class="todo-info" @click="goPendingDetail(item.id)">
                <div class="todo-line1">
                  <strong>{{ item.appNo }}</strong>
                  <el-tag size="small" type="warning" effect="plain">L{{ item.currentApproverLevel || '-' }}</el-tag>
                </div>
                <span class="todo-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <div class="todo-actions">
                <el-button size="small" type="success" plain @click="quickApprove(item)">通过</el-button>
                <el-button size="small" type="danger" plain @click="quickReject(item)">驳回</el-button>
              </div>
            </div>
          </div>
          <EmptyState v-else title="暂无待审批" description="新提交的申请会在这里实时显示" />
        </DataPanel>

        <DataPanel title="快捷入口" class="row-spaced">
          <div class="quick-grid">
            <div v-for="q in quickLinks" :key="q.path" class="quick-tile" @click="$router.push(q.path)">
              <el-icon class="quick-icon"><component :is="q.icon" /></el-icon>
              <span>{{ q.title }}</span>
            </div>
          </div>
        </DataPanel>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { systemApi, approvalApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatRelativeTime } from '@/utils/time'
import MetricCard from '@/components/common/MetricCard.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import RucSeal from '@/components/common/RucSeal.vue'

const router = useRouter()
const userStore = useUserStore()

const greeting = (() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})()

const todayText = (() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 星期${week}`
})()

const cards = ref([
  { label: '在校学生', value: '-', icon: 'UserFilled' },
  { label: '系统用户', value: '-', icon: 'User' },
  { label: '待审批申请', value: '-', icon: 'Tickets' },
  { label: '进行中流程', value: '-', icon: 'Connection' },
])

const quickLinks = [
  { path: '/qa/knowledge', title: '知识库', icon: 'ChatDotSquare' },
  { path: '/qa/document', title: '政策文档', icon: 'Document' },
  { path: '/party/instance', title: '学生流程', icon: 'Connection' },
  { path: '/system/notify', title: '通知群发', icon: 'BellFilled' },
  { path: '/student/list', title: '学生信息', icon: 'UserFilled' },
  { path: '/system/user', title: '用户管理', icon: 'Setting' },
]

const todo = ref([])
const hasApprovalData = ref(false)
const hasPartyData = ref(false)
const hasTrendData = ref(false)

const approvalPieRef = ref(null)
const partyBarRef = ref(null)
const trendLineRef = ref(null)
const chartInstances = []

const STATUS_LABEL = {
  draft: '草稿',
  pending: '待审批',
  approved: '已通过',
  rejected: '已驳回',
  withdrawn: '已撤回',
  downloaded: '已归档',
}

const STATUS_COLOR = {
  draft: '#9aa1a8',
  pending: '#E0A85D',
  approved: '#4F9D72',
  rejected: '#C76060',
  withdrawn: '#7E7E7E',
  downloaded: '#3F73A1',
}

function renderApprovalPie(dist) {
  const chart = echarts.init(approvalPieRef.value)
  chartInstances.push(chart)
  const data = Object.entries(dist || {}).map(([k, v]) => ({
    name: STATUS_LABEL[k] || k,
    value: v,
    itemStyle: { color: STATUS_COLOR[k] || '#9D2235' },
  }))
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        type: 'pie',
        radius: ['38%', '68%'],
        center: ['50%', '46%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{c}' },
        data,
      },
    ],
  })
}

function renderPartyBar(rows) {
  const chart = echarts.init(partyBarRef.value)
  chartInstances.push(chart)
  const names = (rows || []).map((r) => r.name)
  const counts = (rows || []).map((r) => r.count)
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 56, right: 24, top: 30, bottom: 32 },
    xAxis: { type: 'category', data: names, axisLabel: { interval: 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: counts,
        barMaxWidth: 60,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#9D2235' },
            { offset: 1, color: '#C76268' },
          ]),
          borderRadius: [6, 6, 0, 0],
        },
        label: { show: true, position: 'top', color: '#1F2329', fontWeight: 600 },
      },
    ],
  })
}

function renderTrend(approvalTrend, notifyTrend) {
  const chart = echarts.init(trendLineRef.value)
  chartInstances.push(chart)
  const dates = (approvalTrend || []).map((r) => r.date)
  const approvalSeries = (approvalTrend || []).map((r) => r.count)
  const notifySeries = (notifyTrend || []).map((r) => r.count)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 8 },
    grid: { left: 48, right: 24, top: 40, bottom: 32 },
    xAxis: { type: 'category', boundaryGap: false, data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '审批申请',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: approvalSeries,
        itemStyle: { color: '#9D2235' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(157, 34, 53,0.28)' },
            { offset: 1, color: 'rgba(157, 34, 53,0.02)' },
          ]),
        },
      },
      {
        name: '系统通知',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: notifySeries,
        itemStyle: { color: '#3F73A1' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(63,115,161,0.22)' },
            { offset: 1, color: 'rgba(63,115,161,0.02)' },
          ]),
        },
      },
    ],
  })
}

function handleResize() {
  chartInstances.forEach((c) => c && c.resize())
}

const formatTime = formatRelativeTime

function goCenter() {
  router.push('/approval/center')
}

function goPendingDetail(id) {
  router.push({ path: '/approval/center', query: { tab: 'pending', openId: id } })
}

// ===== 待办内联审批 =====
async function quickApprove(item) {
  try {
    const { value } = await ElMessageBox.prompt(`确定通过申请「${item.appNo}」吗？`, '通过审批', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '审批意见，可不填',
    })
    await approvalApi.approve(item.id, { comment: value || '' })
    ElMessage.success('已通过')
    loadDashboard()
  } catch (e) { /* user cancelled */ }
}

async function quickReject(item) {
  try {
    const { value } = await ElMessageBox.prompt(`请输入申请「${item.appNo}」的驳回原因`, '驳回申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因',
      inputValidator: value => !!value || '驳回原因不能为空',
    })
    await approvalApi.reject(item.id, { comment: value })
    ElMessage.success('已驳回')
    loadDashboard()
  } catch (e) { /* user cancelled */ }
}

async function loadDashboard() {
  const res = await systemApi.getDashboard()
  const d = res.data || {}
  cards.value[0].value = d.totalStudents ?? 0
  cards.value[1].value = d.totalUsers ?? 0
  cards.value[2].value = d.pendingApprovals ?? 0
  cards.value[3].value = d.activeProcesses ?? 0
  todo.value = d.pendingTodo || []
  return d
}

onMounted(async () => {
  try {
    const d = await loadDashboard()

    // 判定每张图是否有可视化数据
    const dist = d.approvalStatusDist || {}
    hasApprovalData.value = Object.values(dist).some((v) => Number(v) > 0)

    const partyRows = d.partyTemplateDist || []
    hasPartyData.value = partyRows.some((r) => Number(r.count) > 0)

    const apt = d.approvalTrend7d || []
    const ntt = d.notifyTrend7d || []
    hasTrendData.value =
      apt.some((r) => Number(r.count) > 0) || ntt.some((r) => Number(r.count) > 0)

    await nextTick()
    if (hasApprovalData.value) renderApprovalPie(dist)
    if (hasPartyData.value) renderPartyBar(partyRows)
    if (hasTrendData.value) renderTrend(apt, ntt)
    window.addEventListener('resize', handleResize)
  } catch (e) {
    /* api 层已提示 */
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach((c) => c && c.dispose())
})
</script>

<style scoped lang="scss">
.welcome-banner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 26px 30px;
  color: #fff;
  border-radius: var(--app-radius-lg);
  background:
    radial-gradient(circle at 92% -30%, rgba(255, 255, 255, 0.14), transparent 46%),
    var(--app-red-gradient);
  box-shadow: var(--app-shadow-red);
  overflow: hidden;

  h1 {
    margin: 10px 0 0;
    font-family: var(--app-font-display);
    font-size: 25px;
    font-weight: 700;
    letter-spacing: 1.5px;
  }

  p {
    margin: 10px 0 0;
    color: rgba(255, 255, 255, 0.78);
    font-size: 13px;
    letter-spacing: 0.5px;
  }
}

.banner-watermark {
  position: absolute;
  right: 120px;
  top: -56px;
  opacity: 0.07;
  pointer-events: none;
}

.welcome-main {
  position: relative;
  z-index: 1;
}

.welcome-date {
  display: inline-block;
  padding: 5px 13px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  letter-spacing: 1px;
}

.welcome-seal {
  position: relative;
  z-index: 1;
  margin-right: 6px;
}

.row-spaced {
  margin-top: 16px;
}

.chart-box {
  height: 280px;
  width: 100%;
}

.chart-box--trend {
  height: 300px;
}

.chart-empty {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-empty--trend {
  height: 300px;
}

/* ===== 待办 ===== */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  background: var(--app-muted);
  border: 1px solid var(--app-border-light);
  border-radius: 10px;
  transition: background 0.15s, border-color 0.15s;

  &:hover {
    background: rgba(157, 34, 53, 0.05);
    border-color: rgba(157, 34, 53, 0.22);
  }
}

.todo-info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.todo-line1 {
  display: flex;
  align-items: center;
  gap: 8px;

  strong {
    color: var(--app-text);
    font-size: 13.5px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.todo-time {
  display: block;
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.todo-actions {
  display: flex;
  flex-shrink: 0;

  :deep(.el-button + .el-button) {
    margin-left: 6px;
  }
}

/* ===== 快捷入口 ===== */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.quick-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 6px;
  border: 1px solid var(--app-border-light);
  border-radius: 10px;
  background: var(--app-muted);
  color: var(--app-text-regular);
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.18s var(--app-ease);

  .quick-icon {
    font-size: 22px;
    color: var(--app-primary);
  }

  &:hover {
    background: var(--app-primary-light);
    border-color: var(--app-primary-soft);
    color: var(--app-primary);
    transform: translateY(-2px);
  }
}
</style>
