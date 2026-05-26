<template>
  <div class="app-page">
    <PageHeader title="知识库管理" description="维护标准问答条目，为智能问答提供人工校准答案。">
      <template #actions>
        <el-button type="primary" @click="showDialog()">新增条目</el-button>
      </template>
    </PageHeader>

    <FilterBar>
      <el-form inline>
        <el-form-item label="分类">
          <el-select v-model="query.category" clearable placeholder="全部" style="width: 170px" @change="handleSearch">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable style="width: 280px" placeholder="搜索问题或关键词" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </FilterBar>

    <DataPanel title="标准问答">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="category" label="分类" width="140" />
        <el-table-column prop="question" label="标准问题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answer" label="标准答案" min-width="260" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" width="190" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该条目吗？" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑条目' : '新增条目'" width="620px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="分类">
          <el-select v-model="form.category" filterable allow-create default-first-option style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标准问题">
          <el-input v-model="form.question" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="标准答案">
          <el-input v-model="form.answer" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="多个关键词用逗号分隔" />
        </el-form-item>
        <el-form-item label="官方链接">
          <el-input v-model="form.sourceUrl" />
        </el-form-item>
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
import PageHeader from '@/components/common/PageHeader.vue'
import FilterBar from '@/components/common/FilterBar.vue'
import DataPanel from '@/components/common/DataPanel.vue'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const list = ref([])
const total = ref(0)
const categories = ['党团流程', '学籍管理', '纪律处分', '校历安排', '入党', '入团', '奖学金', '日常事务', '其他']
const query = reactive({ page: 1, size: 20, category: '', keyword: '' })
const form = reactive({ id: null, category: '', question: '', answer: '', keywords: '', sourceUrl: '' })

function showDialog(row) {
  if (row) Object.assign(form, row)
  else Object.assign(form, { id: null, category: '', question: '', answer: '', keywords: '', sourceUrl: '' })
  dialogVisible.value = true
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.category) params.category = query.category
    if (query.keyword) params.keyword = query.keyword
    const res = await qaApi.getKnowledgePage(params)
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
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id) {
  await qaApi.deleteKnowledge(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
