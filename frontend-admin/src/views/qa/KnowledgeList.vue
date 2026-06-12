<template>
  <div class="app-page">
    <PageHeader title="知识库管理" description="维护标准问答条目，为智能问答提供人工校准答案。">
      <template #actions>
        <el-button type="primary" @click="showDialog()">新增条目</el-button>
      </template>
    </PageHeader>

    <!-- 分类标签条 + 关键词搜索 -->
    <div class="filter-strip">
      <div class="cat-chips">
        <button class="cat-chip" :class="{ active: !query.category }" @click="pickCategory('')">全部</button>
        <button
          v-for="c in categories"
          :key="c"
          class="cat-chip"
          :class="{ active: query.category === c }"
          @click="pickCategory(c)"
        >
          {{ c }}
        </button>
      </div>
      <el-input
        v-model="query.keyword"
        clearable
        class="kw-input"
        placeholder="搜索问题或关键词"
        prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
    </div>

    <!-- 问答卡片列表: 每条知识为一张 Q/A 卡 -->
    <div v-loading="loading" class="qa-list">
      <div v-for="row in list" :key="row.id" class="qa-card">
        <div class="qa-card__head">
          <span class="qa-mark">Q</span>
          <h3 class="qa-question">{{ row.question }}</h3>
          <span v-if="row.category" class="qa-category">{{ row.category }}</span>
          <div class="qa-actions">
            <el-button link type="primary" @click="showDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该条目吗？" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </div>
        </div>
        <div class="qa-card__body">
          <span class="qa-mark qa-mark--a">A</span>
          <p class="qa-answer">{{ row.answer }}</p>
        </div>
        <div v-if="row.keywords || row.sourceUrl" class="qa-card__foot">
          <div class="qa-keywords">
            <span v-for="k in splitKeywords(row.keywords)" :key="k" class="qa-keyword">{{ k }}</span>
          </div>
          <a v-if="row.sourceUrl" class="qa-source" :href="row.sourceUrl" target="_blank" rel="noopener">官方链接 ↗</a>
        </div>
      </div>

      <EmptyState
        v-if="!loading && !list.length"
        title="暂无知识条目"
        description="点击右上角「新增条目」录入标准问答, 学生端问答会优先命中这里的答案。"
      />
    </div>

    <div v-if="total > query.size" class="table-footer">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
      />
    </div>

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
import EmptyState from '@/components/common/EmptyState.vue'

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

function pickCategory(c) {
  query.category = c
  handleSearch()
}

function splitKeywords(s) {
  if (!s) return []
  return String(s).split(/[,，;；]/).map(k => k.trim()).filter(Boolean)
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

<style scoped lang="scss">
.filter-strip {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
}

.cat-chips {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-chip {
  height: 30px;
  padding: 0 14px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: transparent;
  color: var(--app-text-regular);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.16s var(--app-ease);

  &:hover {
    border-color: var(--app-primary-soft);
    color: var(--app-primary);
  }

  &.active {
    color: #fff;
    background: var(--app-primary);
    border-color: var(--app-primary);
    box-shadow: 0 2px 8px rgba(157, 34, 53, 0.26);
  }
}

.kw-input {
  width: 260px;
  flex-shrink: 0;
}

/* ===== 问答卡片 ===== */
.qa-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
}

.qa-card {
  padding: 16px 18px;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  transition: box-shadow 0.2s var(--app-ease), border-color 0.2s var(--app-ease);

  &:hover {
    border-color: var(--app-primary-soft);
    box-shadow: var(--app-shadow);

    .qa-actions {
      opacity: 1;
    }
  }
}

.qa-card__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.qa-mark {
  flex: 0 0 auto;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  margin-top: 1px;
  border-radius: 7px;
  color: #fff;
  background: var(--app-primary);
  font-size: 13px;
  font-weight: 700;
}

.qa-mark--a {
  color: var(--app-gold-deep);
  background: var(--app-gold-light);
}

.qa-question {
  margin: 2px 0 0;
  flex: 1;
  min-width: 0;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.5;
}

.qa-category {
  flex: 0 0 auto;
  padding: 3px 11px;
  border-radius: 999px;
  background: var(--app-primary-light);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 500;
}

.qa-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity 0.18s var(--app-ease);
}

.qa-card__body {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 10px;
}

.qa-answer {
  margin: 3px 0 0;
  flex: 1;
  color: var(--app-text-regular);
  font-size: 13.5px;
  line-height: 1.75;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-line;
}

.qa-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--app-border-light);
}

.qa-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.qa-keyword {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--app-muted);
  border: 1px solid var(--app-border-light);
  color: var(--app-text-secondary);
  font-size: 12px;
}

.qa-source {
  flex-shrink: 0;
  color: var(--app-primary);
  font-size: 13px;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}
</style>
