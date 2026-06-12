import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器: 注入 Token
request.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 响应拦截器: 统一处理错误
request.interceptors.response.use(
  response => {
    // blob 文件下载: 直接返回, 不要按 JSON 解析
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    // HTTP 层 401 (token 过期/无效): 清登录态并跳回登录页
    const status = error.response?.status
    const bizCode = error.response?.data?.code
    if (status === 401 || bizCode === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error('登录已过期, 请重新登录')
      router.push('/login')
      return Promise.reject(error)
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
