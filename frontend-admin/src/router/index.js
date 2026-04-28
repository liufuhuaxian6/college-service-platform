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
      // 审批管理
      {
        path: 'approval/pending',
        name: 'ApprovalPending',
        component: () => import('@/views/approval/PendingList.vue'),
        meta: { title: '待审批', icon: 'Tickets', minRole: 2 },
      },
      {
        path: 'approval/all',
        name: 'ApprovalAll',
        component: () => import('@/views/approval/AllList.vue'),
        meta: { title: '全部申请', icon: 'Finished', minRole: 2 },
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

  // 权限检查
  if (to.meta.minRole && userStore.roleLevel > to.meta.minRole) {
    return next('/dashboard')
  }

  next()
})

export default router
