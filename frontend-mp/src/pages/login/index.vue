<template>
  <view class="login-page">
    <view class="hero">
      <view class="brand-row">
        <view class="brand-mark">综</view>
        <view class="brand-copy">
          <text class="eyebrow">学院综合服务</text>
          <text class="brand-title">学院综合服务平台</text>
        </view>
      </view>
      <text class="hero-text">学生服务、党团事务与证明审批统一入口</text>
    </view>

    <view class="login-card">
      <view class="card-head">
        <text class="card-title">账号登录</text>
        <text class="card-subtitle">使用学号或管理员账号进入系统</text>
      </view>

      <view class="field">
        <text class="field-label">账号</text>
        <view class="input-wrap">
          <text class="input-icon">号</text>
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
          <text class="input-icon">密</text>
          <input
            class="input"
            v-model="form.password"
            password
            placeholder="请输入密码"
            placeholder-class="placeholder"
            confirm-type="done"
            @confirm="handleLogin"
          />
        </view>
      </view>

      <button class="btn-login" :class="{ disabled: !canSubmit }" :loading="loading" :disabled="loading" @click="handleLogin">
        登录
      </button>

      <view class="hint">
        <text>登录后可使用政策问答、党团流程、证明申请等服务</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

const userStore = useUserStore()
const loading = ref(false)
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
.login-page {
  min-height: 100vh;
  padding: 34rpx 30rpx 46rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 12% 0%, rgba(155, 44, 54, .12), transparent 34%),
    linear-gradient(180deg, #FBF7F5 0%, #F6F4F2 58%, #FFFFFF 100%);
}

.hero {
  margin-top: 80rpx;
  padding: 34rpx 32rpx 92rpx;
  color: #fff;
  border-radius: 30rpx;
  background: linear-gradient(135deg, #9B2C36 0%, #76242D 100%);
  box-shadow: 0 22rpx 50rpx rgba(155, 44, 54, .2);
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.brand-mark {
  width: 84rpx;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  font-size: 36rpx;
  font-weight: 800;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, .16);
}

.brand-copy {
  min-width: 0;
}

.eyebrow {
  display: block;
  color: rgba(255, 255, 255, .76);
  font-size: 22rpx;
}

.brand-title {
  display: block;
  margin-top: 6rpx;
  color: #fff;
  font-size: 38rpx;
  font-weight: 800;
  line-height: 1.25;
}

.hero-text {
  display: block;
  margin-top: 26rpx;
  color: rgba(255, 255, 255, .82);
  font-size: 25rpx;
  line-height: 1.5;
}

.login-card {
  position: relative;
  z-index: 2;
  margin: -58rpx 12rpx 0;
  padding: 34rpx 30rpx 30rpx;
  border: 1rpx solid rgba(155, 44, 54, .08);
  border-radius: 28rpx;
  background: rgba(255, 255, 255, .98);
  box-shadow: 0 22rpx 56rpx rgba(31, 35, 41, .08);
}

.card-head {
  margin-bottom: 30rpx;
}

.card-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 34rpx;
  font-weight: 760;
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
  height: 92rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 0 24rpx;
  border: 1rpx solid var(--mp-border);
  border-radius: 20rpx;
  background: #FAFAFA;
  box-sizing: border-box;
}

.input-icon {
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--mp-primary);
  font-size: 21rpx;
  font-weight: 760;
  border-radius: 14rpx;
  background: var(--mp-primary-light);
}

.input {
  flex: 1;
  min-width: 0;
  height: 88rpx;
  color: var(--mp-text-main);
  font-size: 28rpx;
}

.placeholder {
  color: #A8AFBA;
}

.btn-login {
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 34rpx 0 0;
  color: #fff;
  font-size: 30rpx;
  font-weight: 760;
  border-radius: 22rpx;
  background: #9B2C36;
  box-shadow: 0 16rpx 34rpx rgba(155, 44, 54, .22);
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
  padding: 20rpx 22rpx;
  color: var(--mp-text-sub);
  font-size: 23rpx;
  line-height: 1.45;
  border-radius: 18rpx;
  background: #F7F8FA;
}
</style>
