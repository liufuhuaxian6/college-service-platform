<template>
  <div class="app-page">
    <PageHeader
      title="数据概览"
      description="集中查看学生服务、证明审批、党团流程和系统运行情况。"
    />

    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <MetricCard :label="card.label" :value="card.value" :icon="card.icon" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-spaced">
      <el-col :span="12">
        <DataPanel title="审批状态分布" description="所有证明申请按状态的占比">
          <div v-show="hasApprovalData" ref="approvalPieRef" class="chart-box" />
          <div v-if="!hasApprovalData" class="chart-empty">
            <EmptyState title="暂无审批数据" description="学生提交申请后, 这里会展示各状态占比" />
          </div>
        </DataPanel>
      </el-col>
      <el-col :span="12">
        <DataPanel title="党团流程模板分布" description="每个模板下的实例数量">
          <div v-show="hasPartyData" ref="partyBarRef" class="chart-box" />
          <div v-if="!hasPartyData" class="chart-empty">
            <EmptyState title="暂无流程实例" description="为学生创建入党/入团流程后, 这里会展示分布" />
          </div>
        </DataPanel>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-spaced">
      <el-col :span="16">
        <DataPanel title="近 7 日活动趋势" description="审批申请与系统通知数量">
          <div v-show="hasTrendData" ref="trendLineRef" class="chart-box" />
          <div v-if="!hasTrendData" class="chart-empty">
            <EmptyState title="近 7 日无活动" description="审批申请与通知数据会在产生后实时累计" />
          </div>
        </DataPanel>
      </el-col>
      <el-col :span="8">
        <DataPanel title="待审批 (最新 5 条)" description="点击进入审批管理处理">
          <div v-if="todo.length" class="todo-list">
            <div v-for="item in todo" :key="item.id" class="todo-item" @click="goPendingDetail(item.id)">
              <div class="todo-line1">
                <strong>{{ item.appNo }}</strong>
                <el-tag size="small" type="warning">L{{ item.currentApproverLevel || '-' }}</el-tag>
              </div>
              <span class="todo-time">{{ formatTime(item.createdAt) }}</span>
            </div>
          </div>
          <EmptyState v-else title="暂无待审批" description="新提交的申请会在这里实时显示" />
        </DataPanel>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { systemApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const router = useRouter()

const cards = ref([
  { label: '在校学生', value: '-', icon: 'UserFilled' },
  { label: '系统用户', value: '-', icon: 'User' },
  { label: '待审批申请', value: '-', icon: 'Tickets' },
  { label: '进行中流程', value: '-', icon: 'Connection' },
])

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
    itemStyle: { color: STATUS_COLOR[k] || '#9B2C36' },
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
            { offset: 0, color: '#9B2C36' },
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
        itemStyle: { color: '#9B2C36' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(155,44,54,0.28)' },
            { offset: 1, color: 'rgba(155,44,54,0.02)' },
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

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t)
  const now = new Date()
  const mins = Math.floor((now - d) / 60000)
  if (mins < 60) return `${mins} 分钟前`
  if (mins < 1440) return `${Math.floor(mins / 60)} 小时前`
  return d.toLocaleDateString()
}

function goPending() {
  router.push('/approval/pending')
}

function goPendingDetail(id) {
  router.push({ path: '/approval/pending', query: { openId: id } })
}

onMounted(async () => {
  try {
    const res = await systemApi.getDashboard()
    const d = res.data || {}
    cards.value[0].value = d.totalStudents ?? 0
    cards.value[1].value = d.totalUsers ?? 0
    cards.value[2].value = d.pendingApprovals ?? 0
    cards.value[3].value = d.activeProcesses ?? 0
    todo.value = d.pendingTodo || []

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
.row-spaced {
  margin-top: 16px;
}

.chart-box {
  height: 320px;
  width: 100%;
}

.chart-empty {
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-item {
  padding: 12px 14px;
  background: var(--app-muted);
  border: 1px solid var(--app-border-light);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;

  &:hover {
    background: rgba(155, 44, 54, 0.06);
    border-color: rgba(155, 44, 54, 0.25);
  }
}

.todo-line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;

  strong {
    color: var(--app-text);
    font-size: 14px;
  }
}

.todo-time {
  display: block;
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 12px;
}
</style>
