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
  exportStudents: (params) => request.get('/system/user/export', {
    params,
    responseType: 'blob',
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
  getTags: () => request.get('/notify/tags'),
  // ---- 群发管理 ----
  previewTargets: (filter) => request.post('/notify/broadcast/preview', filter || {}),
  broadcast: (data) => request.post('/notify/broadcast', data),
  getBroadcastPage: (params) => request.get('/notify/broadcast/page', { params }),
  getBroadcastDetail: (id) => request.get(`/notify/broadcast/${id}`),
  withdrawBroadcast: (id) => request.delete(`/notify/broadcast/${id}`),
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
  indexDocument: (id) => request.post(`/qa/document/${id}/index`, null, { timeout: 600000 }),
  searchDocumentChunks: (params) => request.get('/qa/document/chunk/search', { params }),
  deleteDocument: (id) => request.delete(`/qa/document/${id}`),
  getDocumentDownloadUrl: (id) => `/api/qa/document/${id}/download`,
  // ---- 办公模板（复用 document 接口, docType=template） ----
  getTemplateList: (params) => request.get('/qa/document/list', { params: { ...params, docType: 'template' } }),
  addTemplate: (data) => request.post('/qa/document', { ...data, docType: 'template' }),
  fillTemplateFile: (id, fileInfo) => request.put(`/qa/document/${id}/file`, fileInfo),
  deleteTemplate: (id) => request.delete(`/qa/document/${id}`),
  getTemplateDownloadUrl: (id) => `/api/qa/document/${id}/download`,
}

// ==================== 党团流程 ====================
export const partyApi = {
  getTemplatePage: (params) => request.get('/party/template/page', { params }),
  getTemplateDetail: (id) => request.get(`/party/template/${id}`),
  createTemplate: (data) => request.post('/party/template', data),
  updateTemplate: (id, data) => request.put(`/party/template/${id}`, data),
  getInstancePage: (params) => request.get('/party/instance/page', { params }),
  createInstance: (data) => request.post('/party/instance', data),
  advanceStep: (id, data) => request.put(`/party/instance/${id}/advance`, data),
  suspendInstance: (id, data) => request.put(`/party/instance/${id}/suspend`, data),
  resumeInstance: (id, data) => request.put(`/party/instance/${id}/resume`, data),
  deleteInstance: (id) => request.delete(`/party/instance/${id}`),
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
