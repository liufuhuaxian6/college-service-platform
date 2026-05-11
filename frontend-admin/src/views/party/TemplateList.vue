<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>流程模板管理</span>
        <el-button type="primary" @click="showCreateDialog">新建模板</el-button>
      </div>
    </template>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="流程名称" min-width="160" />
      <el-table-column prop="totalSteps" label="步骤数" width="100" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success">启用</el-tag>
          <el-tag v-else type="info">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="editTemplate(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="loadData"
    />

    <el-dialog v-model="dialogVisible" title="新建流程模板" width="760px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="流程名称" required>
          <el-input v-model="form.name" placeholder="例如：入党流程、入团流程" />
        </el-form-item>

        <el-form-item label="流程描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="请输入流程描述"
          />
        </el-form-item>

        <el-divider content-position="left">流程步骤</el-divider>

        <div
          v-for="(step, index) in steps"
          :key="index"
          style="border:1px solid #ebeef5;border-radius:4px;padding:12px;margin-bottom:12px"
        >
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
            <strong>第 {{ index + 1 }} 步</strong>
            <el-button
              v-if="steps.length > 1"
              link
              type="danger"
              @click="removeStep(index)"
            >
              删除步骤
            </el-button>
          </div>

          <el-form-item label="步骤名称" required>
            <el-input v-model="step.name" placeholder="例如：递交入党申请书" />
          </el-form-item>

          <el-form-item label="步骤描述">
            <el-input
              v-model="step.description"
              type="textarea"
              :rows="2"
              placeholder="请输入步骤说明"
            />
          </el-form-item>

          <el-form-item label="预计天数">
            <el-input-number
              v-model="step.durationDays"
              :min="0"
              :max="9999"
              style="width: 180px"
            />
          </el-form-item>

          <el-form-item label="所需材料">
            <el-input
              v-model="step.requiredMaterials"
              type="textarea"
              :rows="2"
              placeholder="例如：申请书、思想汇报等"
            />
          </el-form-item>
        </div>

        <el-button type="primary" plain @click="addStep">添加步骤</el-button>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { partyApi } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)

const list = ref([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
})

const form = reactive({
  name: '',
  description: '',
})

const steps = ref([])

function createEmptyStep() {
  return {
    name: '',
    description: '',
    durationDays: 0,
    requiredMaterials: '',
  }
}

async function loadData() {
  loading.value = true

  try {
    const res = await partyApi.getTemplatePage({
      page: query.page,
      size: query.size,
    })

    list.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
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
  if (!validateForm()) {
    return
  }

  const payload = {
    template: {
      name: form.name.trim(),
      description: form.description.trim(),
    },
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
    await partyApi.createTemplate(payload)
    ElMessage.success('新建模板成功')
    dialogVisible.value = false
    query.page = 1
    loadData()
  } finally {
    submitting.value = false
  }
}

function editTemplate(row) {
  ElMessage.warning(
    `暂不支持编辑「${row.name}」。后端当前未提供模板步骤详情接口，直接编辑可能覆盖原有步骤。`
  )
}

onMounted(loadData)
</script>