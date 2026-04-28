import request from './request'

// ==================== 认证 ====================
export const authApi = {
  login: (data) => request.post('/auth/login', data),
  changePassword: (data) => request.put('/auth/password', data),
}

// ==================== 系统管理 ====================
export const systemApi = {
  getUserPage: (params) => request.get('/system/user/page', { params }),
  getUserDetail: (id) => request.get(`/system/user/${id}`),
  updateUser: (id, data) => request.put(`/system/user/${id}`, data),
  setUserRole: (id, data) => request.put(`/system/user/${id}/role`, data),
  importUsers: (formData) => request.post('/system/user/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }),
  getDashboard: () => request.get('/system/dashboard'),
}

// ==================== 操作日志 ====================
export const logApi = {
  getPage: (params) => request.get('/log/page', { params }),
}

// ==================== 通知消息 ====================
export const notifyApi = {
  getPage: (params) => request.get('/notify/page', { params }),
  getUnreadCount: () => request.get('/notify/unread-count'),
  markRead: (id) => request.put(`/notify/${id}/read`),
  markAllRead: () => request.put('/notify/read-all'),
}

// ==================== 智能问答 ====================
export const qaApi = {
  getKnowledgePage: (params) => request.get('/qa/knowledge/page', { params }),
  getKnowledge: (id) => request.get(`/qa/knowledge/${id}`),
  addKnowledge: (data) => request.post('/qa/knowledge', data),
  updateKnowledge: (id, data) => request.put(`/qa/knowledge/${id}`, data),
  deleteKnowledge: (id) => request.delete(`/qa/knowledge/${id}`),
  getDocumentList: (params) => request.get('/qa/document/list', { params }),
  addDocument: (data) => request.post('/qa/document', data),
  deleteDocument: (id) => request.delete(`/qa/document/${id}`),
}

// ==================== 党团流程 ====================
export const partyApi = {
  getTemplatePage: (params) => request.get('/party/template/page', { params }),
  createTemplate: (data) => request.post('/party/template', data),
  updateTemplate: (id, data) => request.put(`/party/template/${id}`, data),
  getInstancePage: (params) => request.get('/party/instance/page', { params }),
  createInstance: (data) => request.post('/party/instance', data),
  advanceStep: (id, data) => request.put(`/party/instance/${id}/advance`, data),
  suspendInstance: (id, data) => request.put(`/party/instance/${id}/suspend`, data),
}

// ==================== 审批管理 ====================
export const approvalApi = {
  getPendingPage: (params) => request.get('/approval/pending/page', { params }),
  getAllPage: (params) => request.get('/approval/all/page', { params }),
  approve: (id, data) => request.put(`/approval/${id}/approve`, data),
  reject: (id, data) => request.put(`/approval/${id}/reject`, data),
  adminWithdraw: (id, data) => request.put(`/approval/${id}/admin-withdraw`, data),
}

// ==================== 学生管理 ====================
export const studentApi = {
  getPage: (params) => request.get('/student/page', { params }),
  getDetail: (id) => request.get(`/student/${id}/detail`),
  addHonor: (id, data) => request.post(`/student/${id}/honor`, data),
  updateHonor: (id, data) => request.put(`/student/honor/${id}`, data),
  deleteHonor: (id) => request.delete(`/student/honor/${id}`),
}

// ==================== 文件 ====================
export const fileApi = {
  upload: (formData) => request.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }),
  getDownloadUrl: (fileId) => `/api/file/download/${fileId}`,
}
