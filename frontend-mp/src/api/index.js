// 小程序环境没有代理，必须用完整地址。
// 可通过 VITE_API_BASE_URL 覆盖；生产构建默认指向部署服务器。
const DEFAULT_API_BASE_URL = import.meta.env.PROD
  ? 'http://10.10.0.27/api'
  : 'http://localhost:8080/api'

export const BASE_URL = (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/$/, '')

function getToken() {
  return uni.getStorageSync('token') || ''
}

export function request(options) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': getToken() ? `Bearer ${getToken()}` : '',
        ...options.header,
      },
      success: (res) => {
        const data = res.data
        if (data.code === 200) {
          resolve(data)
        } else if (data.code === 401) {
          uni.removeStorageSync('token')
          uni.reLaunch({ url: '/pages/login/index' })
          reject(new Error(data.message))
        } else {
          uni.showToast({ title: data.message || '请求失败', icon: 'none' })
          reject(new Error(data.message))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      },
    })
  })
}

// ==================== 认证 ====================
export const authApi = {
  login: (data) => request({ url: '/auth/login', method: 'POST', data }),
  getProfile: () => request({ url: '/auth/profile' }),
  updateProfile: (data) => request({ url: '/auth/profile', method: 'PUT', data }),
}

// ==================== 问答 ====================
export const qaApi = {
  chat: (data) => request({ url: '/qa/chat', method: 'POST', data }),
  getChatHistory: (params) => request({ url: '/qa/chat/history', data: params }),
  getDocumentList: (params) => request({ url: '/qa/document/list', data: { docType: 'policy', ...(params || {}) } }),
  getTemplateList: (params) => request({ url: '/qa/document/list', data: { docType: 'template', ...(params || {}) } }),
}

// ==================== 党团 ====================
export const partyApi = {
  getTemplates: () => request({ url: '/party/templates' }),
  getMyProgress: () => request({ url: '/party/my-progress' }),
  getProgressDetail: (id) => request({ url: `/party/my-progress/${id}` }),
}

// ==================== 审批 ====================
export const approvalApi = {
  getTypes: () => request({ url: '/approval/types' }),
  getTemplates: () => request({ url: '/approval/templates' }),
  getTemplateFields: (id) => request({ url: `/approval/templates/${id}/fields` }),
  apply: (data) => request({ url: '/approval/apply', method: 'POST', data }),
  getMyPage: (params) => request({ url: '/approval/my/page', data: params }),
  getMyDetail: (id) => request({ url: `/approval/my/${id}` }),
  withdraw: (id) => request({ url: `/approval/my/${id}/withdraw`, method: 'PUT' }),
  download: (id) => request({ url: `/approval/my/${id}/download` }),
  /** preview=true 仅预览不锁定; 默认 false 下载并锁定 */
  downloadFileUrl: (id, preview = false) =>
    `${BASE_URL}/approval/my/${id}/download-file${preview ? '?preview=true' : ''}`,
}

// ==================== 学生 ====================
export const studentApi = {
  getProfile: () => request({ url: '/student/profile' }),
  getHonors: () => request({ url: '/student/honors' }),
}

// ==================== 通知 ====================
export const notifyApi = {
  getPage: (params) => request({ url: '/notify/page', data: params }),
  getUnreadCount: () => request({ url: '/notify/unread-count' }),
  getTags: () => request({ url: '/notify/tags' }),
  markRead: (id) => request({ url: `/notify/${id}/read`, method: 'PUT' }),
  markAllRead: () => request({ url: '/notify/read-all', method: 'PUT' }),
}
