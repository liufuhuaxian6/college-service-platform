<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 20px">
      <template #header><span>待办事项</span></template>
      <el-empty v-if="!loading" description="暂无待办" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { systemApi } from '@/api'

const loading = ref(false)
const cards = ref([
  { label: '在校学生', value: '-' },
  { label: '总用户数', value: '-' },
  { label: '待审批', value: '-' },
  { label: '进行中流程', value: '-' },
])

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
.stat-card {
  text-align: center;
  .stat-value { font-size: 32px; font-weight: bold; color: #1a3a5c; }
  .stat-label { color: #999; margin-top: 8px; }
}
</style>
