<template>
  <view class="login-page">
    <!-- 背景装饰: 巨型校徽水印 + 同心圆环 -->
    <RucSeal :size="620" tone="light" class="bg-seal" />
    <view class="bg-ring bg-ring-1" />
    <view class="bg-ring bg-ring-2" />

    <!-- 品牌区 -->
    <view class="brand">
      <RucSeal :size="150" tone="light" class="brand-seal" />
      <text class="brand-cn">中国人民大学</text>
      <text class="brand-en">RENMIN UNIVERSITY OF CHINA</text>
      <view class="brand-divider">
        <view class="divider-line" />
        <text class="divider-text">信息学院</text>
        <view class="divider-line" />
      </view>
      <text class="brand-app">学院学生综合服务与党团管理平台</text>
    </view>

    <!-- 登录卡 -->
    <view class="login-card">
      <view class="card-head">
        <text class="card-title">学生登录</text>
        <text class="card-subtitle">使用学号登录（学生 / 班团骨干）</text>
      </view>

      <view class="field">
        <text class="field-label">账号</text>
        <view class="input-wrap">
          <input
            class="input"
            v-model="form.studentId"
            placeholder="请输入学号/账号"
            placeholder-class="placeholder"
            confirm-type="next"
          />
        </view>
      </view>

      <view class="field">
        <text class="field-label">密码</text>
        <view class="input-wrap">
          <input
            class="input"
            v-model="form.password"
            :password="!showPassword"
            placeholder="请输入密码"
            placeholder-class="placeholder"
            confirm-type="done"
            @confirm="handleLogin"
          />
          <!-- 密码可见性切换 (CSS 眼睛图标) -->
          <view class="eye-toggle" @click="showPassword = !showPassword">
            <view class="eye" :class="{ open: showPassword }">
              <view class="eye-pupil" />
              <view v-if="!showPassword" class="eye-slash" />
            </view>
          </view>
        </view>
      </view>

      <button class="btn-login" :class="{ disabled: !canSubmit }" :loading="loading" :disabled="loading" @click="handleLogin">
        登　录
      </button>

      <view class="hint">
        <text>登录后可使用政策问答、党团流程、证明申请等服务{{ '\n' }}教师 / 院领导请使用 PC 管理端</text>
      </view>
    </view>

    <view class="page-foot">
      <text class="foot-motto">实 事 求 是</text>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import RucSeal from '@/components/RucSeal.vue'

const userStore = useUserStore()
const loading = ref(false)
const showPassword = ref(false)
const form = reactive({ studentId: '', password: '' })

const canSubmit = computed(() => form.studentId.trim() && form.password.trim())

async function handleLogin() {
  if (!canSubmit.value) {
    uni.showToast({ title: '请填写账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await authApi.login({
      studentId: form.studentId.trim(),
      password: form.password,
    })
    // 小程序只面向学生(含学生骨干): roleLevel 4=普通学生 / 3=学生骨干 放行;
    // 1=院领导 / 2=老师 属管理端角色, 拒绝并提示去管理端 (不写登录态)
    if (Number(res.data?.roleLevel) < 3) {
      uni.showModal({
        title: '无法登录',
        content: '该账号为教师/院领导角色, 请使用 PC 管理端登录, 小程序仅限学生使用',
        showCancel: false,
        confirmText: '知道了',
      })
      return
    }
    userStore.setLoginInfo(res.data)
    uni.showToast({ title: '登录成功', icon: 'success' })
    uni.switchTab({ url: '/pages/index/index' })
  } catch (e) {
    // request 层已提示错误
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ===== 全屏人大红沉浸式 ===== */
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 90rpx 40rpx 40rpx;
  box-sizing: border-box;
  overflow: hidden;
  background:
    radial-gradient(circle at 85% -10%, rgba(255, 255, 255, 0.12), transparent 44%),
    radial-gradient(circle at -10% 108%, rgba(0, 0, 0, 0.28), transparent 50%),
    var(--mp-red-gradient);
}

.bg-seal {
  position: absolute;
  right: -240rpx;
  bottom: -260rpx;
  opacity: 0.06;
  pointer-events: none;
}

.bg-ring {
  position: absolute;
  border: 2rpx solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  pointer-events: none;
}

.bg-ring-1 {
  top: -160rpx;
  left: -160rpx;
  width: 460rpx;
  height: 460rpx;
}

.bg-ring-2 {
  top: -90rpx;
  left: -90rpx;
  width: 320rpx;
  height: 320rpx;
  border-color: rgba(255, 255, 255, 0.05);
}

/* ===== 品牌区 ===== */
.brand {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-seal {
  filter: drop-shadow(0 12rpx 30rpx rgba(0, 0, 0, 0.25));
}

.brand-cn {
  margin-top: 30rpx;
  color: #fff;
  font-family: var(--mp-font-display);
  font-size: 52rpx;
  font-weight: 800;
  letter-spacing: 10rpx;
  text-indent: 10rpx;
}

.brand-en {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.6);
  font-size: 19rpx;
  letter-spacing: 5rpx;
}

.brand-divider {
  display: flex;
  align-items: center;
  gap: 18rpx;
  margin-top: 30rpx;
}

.divider-line {
  width: 80rpx;
  height: 1rpx;
  background: rgba(255, 255, 255, 0.3);
}

.divider-text {
  color: var(--mp-gold);
  font-family: var(--mp-font-display);
  font-size: 26rpx;
  font-weight: 700;
  letter-spacing: 6rpx;
  text-indent: 6rpx;
}

.brand-app {
  margin-top: 18rpx;
  color: rgba(255, 255, 255, 0.82);
  font-size: 25rpx;
  letter-spacing: 2rpx;
}

/* ===== 登录卡 ===== */
.login-card {
  position: relative;
  z-index: 2;
  margin-top: 60rpx;
  padding: 38rpx 34rpx 32rpx;
  border-radius: 30rpx;
  border-top: 6rpx solid var(--mp-gold);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 30rpx 80rpx rgba(0, 0, 0, 0.3);
}

.card-head {
  margin-bottom: 30rpx;
}

.card-title {
  display: block;
  color: var(--mp-text-main);
  font-family: var(--mp-font-display);
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: 3rpx;
}

.card-subtitle {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 24rpx;
}

.field + .field {
  margin-top: 22rpx;
}

.field-label {
  display: block;
  margin-bottom: 10rpx;
  color: var(--mp-text-regular);
  font-size: 24rpx;
  font-weight: 650;
}

.input-wrap {
  height: 94rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 0 24rpx;
  border: 1rpx solid var(--mp-border);
  border-radius: 20rpx;
  background: var(--mp-bg-warm);
  box-sizing: border-box;
}

/* ===== 密码可见性切换 (CSS 眼睛) ===== */
.eye-toggle {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-right: -10rpx;
}

.eye {
  position: relative;
  width: 40rpx;
  height: 24rpx;
  border: 3rpx solid #A8A2A4;
  border-radius: 50%;
  box-sizing: border-box;
}

.eye.open {
  border-color: var(--mp-primary);
}

.eye-pupil {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #A8A2A4;
}

.eye.open .eye-pupil {
  background: var(--mp-primary);
}

/* 闭眼斜杠 */
.eye-slash {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 48rpx;
  height: 3rpx;
  border-radius: 2rpx;
  background: #A8A2A4;
  transform: translate(-50%, -50%) rotate(-30deg);
}

.input {
  flex: 1;
  min-width: 0;
  height: 90rpx;
  color: var(--mp-text-main);
  font-size: 28rpx;
}

.placeholder {
  color: #A8AFBA;
}

.btn-login {
  height: 94rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 36rpx 0 0;
  color: #fff;
  font-size: 31rpx;
  font-weight: 760;
  letter-spacing: 8rpx;
  text-indent: 8rpx;
  border-radius: 22rpx;
  background: var(--mp-red-gradient);
  box-shadow: 0 16rpx 34rpx rgba(157, 34, 53, .3);
}

.btn-login::after {
  border: none;
}

.btn-login.disabled {
  color: rgba(255, 255, 255, .82);
  background: #C8A3A8;
  box-shadow: none;
}

.hint {
  margin-top: 24rpx;
  padding: 18rpx 22rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
  line-height: 1.7;
  text-align: center;
  white-space: pre-line;
  border-radius: 18rpx;
  background: var(--mp-bg-warm);
}

/* ===== 页脚校训 ===== */
.page-foot {
  position: relative;
  z-index: 1;
  margin-top: auto;
  padding-top: 50rpx;
  display: flex;
  justify-content: center;
}

.foot-motto {
  color: rgba(255, 255, 255, 0.45);
  font-family: var(--mp-font-display);
  font-size: 26rpx;
  letter-spacing: 14rpx;
  text-indent: 14rpx;
}
</style>
