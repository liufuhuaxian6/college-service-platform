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

    <el-row :gutter="16">
      <el-col :span="16">
        <DataPanel title="待办事项" description="当前账号需要关注的审批与流程事项">
          <EmptyState v-if="!loading" title="暂无待办事项" description="待审批申请和流程提醒会显示在这里" />
        </DataPanel>
      </el-col>
      <el-col :span="8">
        <DataPanel title="平台能力" description="当前系统核心服务模块">
          <div class="service-list">
            <div v-for="item in services" :key="item.title" class="service-item">
              <el-icon><component :is="item.icon" /></el-icon>
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ item.desc }}</span>
              </div>
            </div>
          </div>
        </DataPanel>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { systemApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import MetricCard from '@/components/common/MetricCard.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const loading = ref(false)
const cards = ref([
  { label: '在校学生', value: '-', icon: 'UserFilled' },
  { label: '系统用户', value: '-', icon: 'User' },
  { label: '待审批申请', value: '-', icon: 'Tickets' },
  { label: '进行中流程', value: '-', icon: 'Connection' },
])

const services = [
  { title: '智能问答与政策知识库', desc: '政策文档、标准问答与向量检索', icon: 'ChatDotSquare' },
  { title: '电子证明审批', desc: '申请、审批、下载归档全流程', icon: 'Finished' },
  { title: '党团事务流程', desc: '流程模板、学生进度和节点追踪', icon: 'List' },
  { title: '学生信息与荣誉', desc: '学生画像、荣誉记录和关联业务', icon: 'Medal' },
]

onMounted(async () => {
  loading.value = true
  try {
    const res = await systemApi.getDashboard()
    cards.value[0].value = res.data.totalStudents || 0
    cards.value[1].value = res.data.totalUsers || 0
    cards.value[2].value = res.data.pendingApprovals || 0
    cards.value[3].value = res.data.activeProcesses || 0
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.service-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.service-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--app-muted);
  border: 1px solid var(--app-border-light);
  border-radius: 8px;

  .el-icon {
    margin-top: 2px;
    color: var(--app-primary);
    font-size: 20px;
  }

  strong {
    display: block;
    color: var(--app-text);
  }

  span {
    display: block;
    margin-top: 4px;
    color: var(--app-text-secondary);
    font-size: 12px;
  }
}
</style>
