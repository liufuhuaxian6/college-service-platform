<template>
  <div class="app-page">
    <PageHeader title="操作日志" description="审计管理操作记录，便于追溯“谁在什么时候改了什么”。" />

    <FilterBar>
      <el-form inline>
        <el-form-item label="模块">
          <el-select
            v-model="query.module"
            clearable
            placeholder="全部"
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>

        <el-form-item label="开始时间">
          <el-date-picker
            v-model="query.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期"
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="结束时间">
          <el-date-picker
            v-model="query.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结束日期"
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <!-- 审计时间线 -->
    <DataPanel title="日志记录" description="按时间倒序展示管理操作轨迹">
      <div v-loading="loading" class="log-timeline">
        <div v-for="row in list" :key="row.id" class="log-item">
          <div class="log-time">
            <span class="log-time-main">{{ formatDateTime(row.createdAt).slice(11) }}</span>
            <span class="log-time-sub">{{ formatDateTime(row.createdAt).slice(0, 10) }}</span>
          </div>
          <div class="log-rail">
            <span class="log-dot" :style="{ background: moduleColor(row.module) }" />
            <span class="log-line" />
          </div>
          <div class="log-body">
            <div class="log-head">
              <span class="log-module" :style="{ color: moduleColor(row.module), background: moduleColor(row.module) + '14', borderColor: moduleColor(row.module) + '38' }">
                {{ row.module || '系统' }}
              </span>
              <span class="log-action">{{ row.action }}</span>
            </div>
            <div class="log-meta">
              <span>操作人 #{{ row.userId ?? '-' }}</span>
              <span v-if="row.ip">IP {{ row.ip }}</span>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && !list.length" description="该筛选条件下暂无日志" />
      </div>

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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { logApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'

const loading = ref(false)
const list = ref([])
const total = ref(0)

const modules = ['知识库', '文档管理', '党团流程', '审批管理', '学生画像', '用户管理']

// 模块 → 时间线圆点/徽标颜色
const MODULE_COLORS = {
  '知识库': '#9D2235',
  '文档管理': '#3568A8',
  '党团流程': '#B8923E',
  '审批管理': '#2F7D55',
  '学生画像': '#7A5AA8',
  '用户管理': '#C77023',
}

function moduleColor(m) {
  return MODULE_COLORS[m] || '#5B6472'
}

const query = reactive({
  page: 1,
  size: 20,
  module: '',
  startDate: '',
  endDate: '',
})

function buildQueryParams() {
  const params = {
    page: query.page,
    size: query.size,
  }

  if (query.module) {
    params.module = query.module
  }

  if (query.startDate) {
    params.startDate = query.startDate
  }

  if (query.endDate) {
    params.endDate = query.endDate
  }

  return params
}

async function loadData() {
  loading.value = true

  try {
    const res = await logApi.getPage(buildQueryParams())
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

function resetQuery() {
  query.page = 1
  query.module = ''
  query.startDate = ''
  query.endDate = ''
  loadData()
}

onMounted(loadData)
</script>

<style scoped lang="scss">
/* ===== 审计时间线 ===== */
.log-timeline {
  display: flex;
  flex-direction: column;
  min-height: 160px;
}

.log-item {
  display: flex;
  gap: 14px;

  &:last-child .log-line {
    display: none;
  }

  &:hover .log-body {
    background: var(--app-muted);
  }
}

.log-time {
  flex: 0 0 76px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding-top: 12px;
}

.log-time-main {
  color: var(--app-text);
  font-size: 13.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.log-time-sub {
  margin-top: 2px;
  color: var(--app-text-placeholder);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.log-rail {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.log-dot {
  flex: 0 0 auto;
  width: 11px;
  height: 11px;
  margin-top: 16px;
  border-radius: 50%;
  box-shadow: 0 0 0 3px var(--app-panel), 0 0 0 4px var(--app-border);
}

.log-line {
  flex: 1;
  width: 2px;
  margin-top: 4px;
  background: var(--app-border-light);
}

.log-body {
  flex: 1;
  min-width: 0;
  margin-bottom: 10px;
  padding: 11px 14px;
  border-radius: 10px;
  transition: background 0.15s var(--app-ease);
}

.log-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.log-module {
  flex: 0 0 auto;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid;
  font-size: 12px;
  font-weight: 500;
}

.log-action {
  color: var(--app-text);
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-all;
}

.log-meta {
  display: flex;
  gap: 16px;
  margin-top: 6px;
  color: var(--app-text-placeholder);
  font-size: 12px;
}
</style>
