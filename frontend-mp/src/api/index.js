const BASE_URL = '/api'

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
}

// ==================== 问答 ====================
export const qaApi = {
  chat: (data) => request({ url: '/qa/chat', method: 'POST', data }),
  getChatHistory: (params) => request({ url: '/qa/chat/history', data: params }),
  getDocumentList: (params) => request({ url: '/qa/document/list', data: params }),
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
  apply: (data) => request({ url: '/approval/apply', method: 'POST', data }),
  getMyPage: (params) => request({ url: '/approval/my/page', data: params }),
  getMyDetail: (id) => request({ url: `/approval/my/${id}` }),
  withdraw: (id) => request({ url: `/approval/my/${id}/withdraw`, method: 'PUT' }),
  download: (id) => request({ url: `/approval/my/${id}/download` }),
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
  markRead: (id) => request({ url: `/notify/${id}/read`, method: 'PUT' }),
  markAllRead: () => request({ url: '/notify/read-all', method: 'PUT' }),
}
