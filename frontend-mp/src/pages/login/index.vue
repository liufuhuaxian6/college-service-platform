<template>
  <view class="login-page">
    <view class="logo-section">
      <text class="title">学院综合服务平台</text>
      <text class="subtitle">中国人民大学信息学院</text>
    </view>
    <view class="form">
      <input class="input" v-model="form.studentId" placeholder="请输入学号" />
      <input class="input" v-model="form.password" type="password" placeholder="请输入密码" />
      <button class="btn-login" :loading="loading" @click="handleLogin">登 录</button>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ studentId: '', password: '' })

async function handleLogin() {
  if (!form.studentId || !form.password) {
    uni.showToast({ title: '请填写完整', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await authApi.login(form)
    userStore.setLoginInfo(res.data)
    uni.showToast({ title: '登录成功', icon: 'success' })
    uni.switchTab({ url: '/pages/index/index' })
  } catch (e) { /* handled */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; flex-direction: column; justify-content: center; padding: 60rpx; background: linear-gradient(180deg, #1a3a5c 0%, #2d5f8a 40%, #f0f2f5 40%); }
.logo-section { text-align: center; margin-bottom: 60rpx; }
.title { font-size: 40rpx; font-weight: bold; color: #fff; display: block; }
.subtitle { font-size: 26rpx; color: #ffffffb3; display: block; margin-top: 12rpx; }
.form { background: #fff; border-radius: 16rpx; padding: 40rpx; box-shadow: 0 4rpx 20rpx rgba(0,0,0,.1); }
.input { border: 1rpx solid #ddd; border-radius: 8rpx; padding: 20rpx 24rpx; margin-bottom: 24rpx; font-size: 28rpx; }
.btn-login { background: #1a3a5c; color: #fff; border: none; border-radius: 8rpx; padding: 20rpx; font-size: 30rpx; margin-top: 12rpx; }
</style>
