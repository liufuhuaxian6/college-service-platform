<template>
  <div class="app-page">
    <PageHeader title="流程模板" description="维护党团事务流程模板和办理步骤。">
      <template #actions>
        <el-button type="primary" @click="showCreateDialog">新建模板</el-button>
      </template>
    </PageHeader>

    <DataPanel title="模板列表">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="流程名称" min-width="170" />
        <el-table-column prop="totalSteps" label="步骤数" width="100" />
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="editTemplate(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑流程模板' : '新建流程模板'" width="780px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="流程名称" required>
          <el-input v-model="form.name" placeholder="例如：入党流程、入团流程" />
        </el-form-item>
        <el-form-item label="流程描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入流程描述" />
        </el-form-item>

        <div class="steps-header">
          <span>流程步骤</span>
          <el-button type="primary" plain @click="addStep">添加步骤</el-button>
        </div>

        <div v-for="(step, index) in steps" :key="index" class="step-card">
          <div class="step-card__header">
            <strong>第 {{ index + 1 }} 步</strong>
            <el-button v-if="steps.length > 1" link type="danger" @click="removeStep(index)">删除步骤</el-button>
          </div>
          <el-form-item label="步骤名称" required>
            <el-input v-model="step.name" placeholder="例如：递交入党申请书" />
          </el-form-item>
          <el-form-item label="步骤描述">
            <el-input v-model="step.description" type="textarea" :rows="2" placeholder="请输入步骤说明" />
          </el-form-item>
          <el-form-item label="预计天数">
            <el-input-number v-model="step.durationDays" :min="0" :max="9999" style="width: 180px" />
          </el-form-item>
          <el-form-item label="所需材料">
            <el-input v-model="step.requiredMaterials" type="textarea" :rows="2" placeholder="例如：申请书、思想汇报等" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { partyApi } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import DataPanel from '@/components/common/DataPanel.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20 })
const form = reactive({ name: '', description: '' })
const steps = ref([])

function createEmptyStep() {
  return { name: '', description: '', durationDays: 0, requiredMaterials: '' }
}

async function loadData() {
  loading.value = true
  try {
    const res = await partyApi.getTemplatePage({ page: query.page, size: query.size })
    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingId.value = null
  form.name = ''
  form.description = ''
  steps.value = [createEmptyStep()]
  dialogVisible.value = true
}

function addStep() {
  steps.value.push(createEmptyStep())
}

function removeStep(index) {
  steps.value.splice(index, 1)
}

function validateForm() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入流程名称')
    return false
  }
  if (steps.value.length === 0) {
    ElMessage.warning('请至少添加一个流程步骤')
    return false
  }
  for (let i = 0; i < steps.value.length; i++) {
    if (!steps.value[i].name.trim()) {
      ElMessage.warning(`请输入第 ${i + 1} 步的步骤名称`)
      return false
    }
  }
  return true
}

async function handleSubmit() {
  if (!validateForm()) return
  const payload = {
    template: { name: form.name.trim(), description: form.description.trim() },
    steps: steps.value.map((step, index) => ({
      stepOrder: index + 1,
      name: step.name.trim(),
      description: step.description.trim(),
      durationDays: step.durationDays || 0,
      requiredMaterials: step.requiredMaterials.trim(),
    })),
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await partyApi.updateTemplate(editingId.value, payload)
      ElMessage.success('修改模板成功')
    } else {
      await partyApi.createTemplate(payload)
      ElMessage.success('新建模板成功')
    }
    dialogVisible.value = false
    loadData()
  } finally {
    submitting.value = false
  }
}

async function editTemplate(row) {
  try {
    loading.value = true
    const res = await partyApi.getTemplateDetail(row.id)
    const { template, steps: stepList } = res.data
    editingId.value = row.id
    form.name = template.name || ''
    form.description = template.description || ''
    steps.value = stepList.map(s => ({
      name: s.name || '',
      description: s.description || '',
      durationDays: s.durationDays || 0,
      requiredMaterials: s.requiredMaterials || '',
    }))
    if (steps.value.length === 0) steps.value = [createEmptyStep()]
    dialogVisible.value = true
  } catch (e) {
    ElMessage.error('加载模板详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.steps-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 18px 0 12px;
  font-weight: 650;
}

.step-card {
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-muted);
}

.step-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
</style>
