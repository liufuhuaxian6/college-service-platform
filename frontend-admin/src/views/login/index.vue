<template>
  <div class="login-page">
    <section class="login-intro">
      <div class="brand-mark">综</div>
      <h1>学院学生综合服务与党团管理平台</h1>
      <p>面向学院师生的一体化服务入口，支撑政策问答、证明审批、党团流程和学生信息管理。</p>
      <div class="intro-list">
        <span>智能问答</span>
        <span>多级审批</span>
        <span>党团流程</span>
        <span>数据治理</span>
      </div>
    </section>

    <section class="login-card">
      <div class="login-card__header">
        <h2>管理端登录</h2>
        <p>请使用学院统一账号登录</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="studentId">
          <el-input v-model="form.studentId" placeholder="请输入账号/学号" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="login-btn" native-type="submit">
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ studentId: '', password: '' })
const rules = {
  studentId: [{ required: true, message: '请输入账号/学号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await authApi.login(form)
    userStore.setLoginInfo(res.data)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) { /* error handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 460px;
  align-items: center;
  gap: 56px;
  padding: 64px 9vw;
  background:
    linear-gradient(90deg, rgba(155,44,54,.08), transparent 42%),
    var(--app-bg);
}

.login-intro {
  max-width: 620px;
}

.brand-mark {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  margin-bottom: 22px;
  border-radius: 10px;
  background: var(--app-primary);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
}

h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 34px;
  line-height: 1.25;
  font-weight: 700;
}

.login-intro p {
  margin: 16px 0 0;
  max-width: 520px;
  color: var(--app-text-regular);
  font-size: 16px;
  line-height: 1.8;
}

.intro-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;

  span {
    padding: 8px 12px;
    color: var(--app-primary);
    background: var(--app-primary-light);
    border: 1px solid var(--app-primary-soft);
    border-radius: 999px;
    font-size: 13px;
  }
}

.login-card {
  padding: 34px;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  box-shadow: 0 12px 36px rgba(31, 35, 41, .08);
}

.login-card__header {
  margin-bottom: 24px;

  h2 {
    margin: 0;
    color: var(--app-text);
    font-size: 22px;
  }

  p {
    margin: 8px 0 0;
    color: var(--app-text-secondary);
  }
}

.login-btn {
  width: 100%;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
    padding: 32px;
  }
}
</style>
