import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') || '')
  const name = ref(localStorage.getItem('name') || '')
  const roleLevel = ref(parseInt(localStorage.getItem('roleLevel') || '4'))
  const studentId = ref(localStorage.getItem('studentId') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => roleLevel.value <= 2)
  const isLeader = computed(() => roleLevel.value === 1)

  function setLoginInfo(data) {
    token.value = data.token
    userId.value = data.userId
    name.value = data.name
    roleLevel.value = data.roleLevel
    studentId.value = data.studentId

    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', data.userId)
    localStorage.setItem('name', data.name)
    localStorage.setItem('roleLevel', data.roleLevel)
    localStorage.setItem('studentId', data.studentId)
  }

  function logout() {
    token.value = ''
    userId.value = ''
    name.value = ''
    roleLevel.value = 4
    studentId.value = ''

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('name')
    localStorage.removeItem('roleLevel')
    localStorage.removeItem('studentId')
  }

  return {
    token, userId, name, roleLevel, studentId,
    isLoggedIn, isAdmin, isLeader,
    setLoginInfo, logout,
  }
})
