<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>知识库管理</span>
          <el-button type="primary" @click="showDialog()">新增条目</el-button>
        </div>
      </template>
      <el-form inline style="margin-bottom:16px">
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable placeholder="全部" @change="loadData">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable @keyup.enter="loadData" placeholder="搜索问题或关键词" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="question" label="标准问题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="answer" label="标准答案" min-width="200" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
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
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑条目' : '新增条目'" width="600px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="标准问题"><el-input v-model="form.question" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="标准答案"><el-input v-model="form.answer" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="关键词"><el-input v-model="form.keywords" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="官方链接"><el-input v-model="form.sourceUrl" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { qaApi } from '@/api'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const list = ref([])
const total = ref(0)
const categories = ['入党', '入团', '奖学金', '日常事务', '其他']
const query = reactive({ page: 1, size: 20, category: '', keyword: '' })
const form = reactive({ id: null, category: '', question: '', answer: '', keywords: '', sourceUrl: '' })

function showDialog(row) {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, { id: null, category: '', question: '', answer: '', keywords: '', sourceUrl: '' })
  }
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const res = await qaApi.getKnowledgePage(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

async function handleSubmit() {
  submitting.value = true
  try {
    if (form.id) {
      await qaApi.updateKnowledge(form.id, form)
      ElMessage.success('修改成功')
    } else {
      await qaApi.addKnowledge(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } finally { submitting.value = false }
}

async function handleDelete(id) {
  await qaApi.deleteKnowledge(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
