import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据概览', icon: 'DataAnalysis', minRole: 2 },
      },
      // 智能问答
      {
        path: 'qa/knowledge',
        name: 'QaKnowledge',
        component: () => import('@/views/qa/KnowledgeList.vue'),
        meta: { title: '知识库管理', icon: 'ChatDotSquare', minRole: 2 },
      },
      {
        path: 'qa/document',
        name: 'QaDocument',
        component: () => import('@/views/qa/DocumentList.vue'),
        meta: { title: '政策文档', icon: 'Document', minRole: 2 },
      },
      {
        path: 'qa/template',
        name: 'QaTemplate',
        component: () => import('@/views/qa/TemplateList.vue'),
        meta: { title: '办公模板', icon: 'CopyDocument', minRole: 2 },
      },
      // 党团流程
      {
        path: 'party/template',
        name: 'PartyTemplate',
        component: () => import('@/views/party/TemplateList.vue'),
        meta: { title: '流程模板', icon: 'List', minRole: 2 },
      },
      {
        path: 'party/instance',
        name: 'PartyInstance',
        component: () => import('@/views/party/InstanceList.vue'),
        meta: { title: '学生流程', icon: 'User', minRole: 2 },
      },
      // 审批服务: 待审批/全部申请合并为审批中心 (页内 Tab), 旧路径重定向保持深链可用
      {
        path: 'approval/center',
        name: 'ApprovalCenter',
        component: () => import('@/views/approval/ApprovalCenter.vue'),
        meta: { title: '审批中心', icon: 'Tickets', minRole: 2 },
      },
      {
        path: 'approval/pending',
        redirect: to => ({ path: '/approval/center', query: { ...to.query, tab: 'pending' } }),
      },
      {
        path: 'approval/all',
        redirect: to => ({ path: '/approval/center', query: { ...to.query, tab: 'all' } }),
      },
      // 学生管理
      {
        path: 'student/list',
        name: 'StudentList',
        component: () => import('@/views/student/StudentList.vue'),
        meta: { title: '学生信息', icon: 'UserFilled', minRole: 3 },
      },
      // 系统管理
      {
        path: 'system/notify',
        name: 'SystemNotifyBroadcast',
        component: () => import('@/views/system/NotificationBroadcast.vue'),
        meta: { title: '通知群发', icon: 'BellFilled', minRole: 2 },
      },
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', icon: 'Setting', minRole: 2 },
      },
      {
        path: 'system/log',
        name: 'SystemLog',
        component: () => import('@/views/system/LogList.vue'),
        meta: { title: '操作日志', icon: 'Notebook', minRole: 1 },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  if (to.path === '/login') {
    return next()
  }

  if (to.meta.requiresAuth || to.matched.some(r => r.meta.requiresAuth)) {
    if (!userStore.isLoggedIn) {
      return next('/login')
    }
  }

  // 权限检查: 班团骨干(3级)只开放"学生信息", 权限不足时引导到可用页面;
  // 学生(4级)清登录态踢回 login, 避免和 dashboard 的 minRole 互相弹形成死循环
  if (to.meta.minRole && userStore.roleLevel > to.meta.minRole) {
    if (userStore.roleLevel === 3 && to.path !== '/student/list') {
      return next('/student/list')
    }
    userStore.logout()
    return next({ path: '/login', query: { reason: 'role' } })
  }

  next()
})

export default router
