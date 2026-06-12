<template>
  <div class="login-page">
    <!-- 背景装饰: 同心圆环 + 大尺寸校徽水印 -->
    <div class="bg-deco">
      <span class="ring ring-1" />
      <span class="ring ring-2" />
      <RucSeal class="bg-seal" :size="560" light />
    </div>

    <header class="login-top">
      <div class="login-top__brand">
        <RucSeal :size="44" disc />
        <div class="login-top__name">
          <span class="cn">中国人民大学</span>
          <span class="en">RENMIN UNIVERSITY OF CHINA</span>
        </div>
      </div>
      <span class="login-top__motto">实事求是</span>
    </header>

    <main class="login-stage">
      <section class="login-intro">
        <p class="intro-eyebrow">信息学院 · 学生工作数字化</p>
        <h1 class="intro-title">学院学生综合服务<br />与党团管理平台</h1>
        <p class="intro-desc">
          政策智能问答 · 党团流程管理 · 电子证明审批<br />
          学生画像 · 信息精准推送
        </p>
        <div class="intro-feats">
          <span v-for="f in feats" :key="f" class="feat-chip">{{ f }}</span>
        </div>
      </section>

      <section class="login-card">
        <div class="login-card__head">
          <h2>管理端登录</h2>
          <p>请使用学院统一账号登录（教师 / 院领导 / 班团骨干）</p>
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
              登 录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="login-card__split">
          <span class="split-line" /><span class="split-text">学生入口</span><span class="split-line" />
        </div>
        <p class="login-tip">学生请使用微信小程序「学院综合服务」登录</p>
      </section>
    </main>

    <footer class="login-footer">中国人民大学信息学院 · 学院学生综合服务与党团管理平台</footer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import RucSeal from '@/components/common/RucSeal.vue'

const router = useRouter()
const route = useRoute()

const feats = ['智能问答', '党团流程', '电子审批', '精准推送', '学生画像']

onMounted(() => {
  if (route.query.reason === 'role') {
    ElMessage.warning('该账号为学生角色, 请使用微信小程序登录')
  }
})
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
    // 4 级学生不允许进管理端 (路由守卫会一直拦, 这里提前拒绝并不写登录态)
    if (Number(res.data?.roleLevel) >= 4) {
      ElMessage.error('该账号为学生角色, 请使用微信小程序登录, 管理端仅限教师/院领导/骨干')
      return
    }
    userStore.setLoginInfo(res.data)
    ElMessage.success('登录成功')
    // 班团骨干(3级)只开放"学生信息"页, 直接落到可访问页面
    router.push(Number(res.data?.roleLevel) === 3 ? '/student/list' : '/dashboard')
  } catch (e) { /* error handled by interceptor */ }
  finally { loading.value = false }
}
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  color: #fff;
  background:
    radial-gradient(circle at 84% -12%, rgba(255, 255, 255, 0.12), transparent 42%),
    radial-gradient(circle at -8% 112%, rgba(0, 0, 0, 0.26), transparent 48%),
    var(--app-red-gradient);
  overflow: hidden;
}

/* ===== 背景装饰 ===== */
.bg-deco {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.ring {
  position: absolute;
  border: 1.5px solid rgba(255, 255, 255, 0.07);
  border-radius: 50%;
}

.ring-1 {
  right: -140px;
  bottom: -160px;
  width: 480px;
  height: 480px;
}

.ring-2 {
  right: -60px;
  bottom: -80px;
  width: 320px;
  height: 320px;
  border-color: rgba(255, 255, 255, 0.05);
}

.bg-seal {
  position: absolute;
  left: -130px;
  bottom: -150px;
  opacity: 0.05;
}

/* ===== 顶部品牌条 ===== */
.login-top {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26px 5vw 0;
}

.login-top__brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.login-top__name {
  display: flex;
  flex-direction: column;

  .cn {
    font-family: var(--app-font-display);
    font-size: 20px;
    font-weight: 700;
    letter-spacing: 4px;
  }

  .en {
    margin-top: 3px;
    color: rgba(255, 255, 255, 0.62);
    font-size: 10px;
    letter-spacing: 2px;
  }
}

.login-top__motto {
  font-family: var(--app-font-display);
  color: rgba(255, 255, 255, 0.55);
  font-size: 14px;
  letter-spacing: 10px;
  text-indent: 10px;
}

/* ===== 中部舞台: 左介绍 + 右登录卡 ===== */
.login-stage {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(48px, 8vw, 140px);
  padding: 40px 5vw;
  flex-wrap: wrap;
}

.login-intro {
  max-width: 520px;
  animation: rise-in 0.6s var(--app-ease) both;
}

.intro-eyebrow {
  display: inline-block;
  margin: 0 0 22px;
  padding: 7px 16px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.88);
  font-size: 13px;
  letter-spacing: 2px;
}

.intro-title {
  margin: 0;
  font-family: var(--app-font-display);
  font-size: clamp(30px, 3.4vw, 42px);
  font-weight: 700;
  line-height: 1.42;
  letter-spacing: 3px;
}

.intro-desc {
  margin: 22px 0 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 15px;
  line-height: 2;
  letter-spacing: 1px;
}

.intro-feats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 30px;
}

.feat-chip {
  padding: 7px 16px;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  font-size: 13px;
  letter-spacing: 1px;
}

/* ===== 登录卡 ===== */
.login-card {
  width: 400px;
  max-width: 92vw;
  padding: 38px 36px 28px;
  color: var(--app-text);
  background: rgba(255, 255, 255, 0.97);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
  border-top: 4px solid var(--app-gold);
  animation: rise-in 0.6s 0.08s var(--app-ease) both;
}

.login-card__head {
  margin-bottom: 26px;

  h2 {
    margin: 0;
    color: var(--app-text);
    font-family: var(--app-font-display);
    font-size: 23px;
    font-weight: 700;
    letter-spacing: 2px;
  }

  p {
    margin: 9px 0 0;
    color: var(--app-text-secondary);
    font-size: 13px;
  }
}

.login-btn {
  width: 100%;
  letter-spacing: 8px;
  font-weight: 600;
}

.login-card__split {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 6px 0 10px;
}

.split-line {
  flex: 1;
  height: 1px;
  background: var(--app-border);
}

.split-text {
  color: var(--app-text-placeholder);
  font-size: 12px;
  letter-spacing: 2px;
}

.login-tip {
  margin: 0;
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 12.5px;
}

/* ===== 页脚 ===== */
.login-footer {
  position: relative;
  z-index: 1;
  padding: 0 5vw 24px;
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  letter-spacing: 1.5px;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 980px) {
  .login-top__motto {
    display: none;
  }

  .login-stage {
    padding-top: 24px;
  }

  .login-intro {
    text-align: center;

    .intro-feats {
      justify-content: center;
    }
  }
}
</style>
