import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(uni.getStorageSync('token') || '')
  const userId = ref(uni.getStorageSync('userId') || '')
  const name = ref(uni.getStorageSync('name') || '')
  const roleLevel = ref(parseInt(uni.getStorageSync('roleLevel') || '4'))
  const studentId = ref(uni.getStorageSync('studentId') || '')

  const isLoggedIn = computed(() => !!token.value)

  function setLoginInfo(data) {
    token.value = data.token
    userId.value = data.userId
    name.value = data.name
    roleLevel.value = data.roleLevel
    studentId.value = data.studentId

    uni.setStorageSync('token', data.token)
    uni.setStorageSync('userId', data.userId)
    uni.setStorageSync('name', data.name)
    uni.setStorageSync('roleLevel', data.roleLevel)
    uni.setStorageSync('studentId', data.studentId)
  }

  function logout() {
    token.value = ''
    userId.value = ''
    name.value = ''
    roleLevel.value = 4
    studentId.value = ''
    uni.clearStorageSync()
    uni.reLaunch({ url: '/pages/login/index' })
  }

  return { token, userId, name, roleLevel, studentId, isLoggedIn, setLoginInfo, logout }
})
