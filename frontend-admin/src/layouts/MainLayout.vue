<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '224px'" class="layout-aside">
      <div class="brand" :class="{ collapsed: isCollapse }">
        <div class="brand-mark">综</div>
        <div v-if="!isCollapse" class="brand-text">
          <strong>学院综合服务平台</strong>
          <span>学生服务与党团管理</span>
        </div>
      </div>

      <el-menu
        :default-active="$route.path"
        :collapse="isCollapse"
        router
        background-color="transparent"
        text-color="rgba(255,255,255,.72)"
        active-text-color="#ffffff"
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-sub-menu v-if="item.children" :index="item.path">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
              v-show="userStore.roleLevel <= (child.minRole || 4)"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item
            v-else
            :index="item.path"
            v-show="userStore.roleLevel <= (item.minRole || 4)"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-button text class="collapse-btn" @click="isCollapse = !isCollapse">
            <el-icon><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
          </el-button>
          <el-button v-if="$route.path !== '/dashboard'" text class="back-btn" @click="goBack">
            <el-icon><Back /></el-icon>返回
          </el-button>
          <el-breadcrumb separator="/" class="crumb">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="breadcrumb.group">{{ breadcrumb.group }}</el-breadcrumb-item>
            <el-breadcrumb-item>{{ breadcrumb.current }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="unreadCount" :hidden="!unreadCount" class="notify-badge">
            <el-button text circle @click="openNotify">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              {{ userStore.name }}<em>{{ roleName }}</em>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 修改密码 -->
  <el-dialog v-model="pwdVisible" title="修改密码" width="440px">
    <el-form :model="pwdForm" label-width="86px">
      <el-form-item label="原密码" required>
        <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
      </el-form-item>
      <el-form-item label="新密码" required>
        <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
      </el-form-item>
      <el-form-item label="确认新密码" required>
        <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdSubmitting" @click="submitPassword">确定</el-button>
    </template>
  </el-dialog>

  <!-- 消息中心 -->
  <el-drawer v-model="notifyVisible" title="消息中心" size="420px">
    <div class="notify-toolbar">
      <span class="notify-count">未读 {{ unreadCount }} 条</span>
      <el-button v-if="unreadCount" link type="primary" @click="markAllRead">全部已读</el-button>
    </div>
    <el-empty v-if="!notifyLoading && !notifyList.length" description="暂无消息" />
    <div v-else v-loading="notifyLoading" class="notify-list">
      <div
        v-for="n in notifyList"
        :key="n.id"
        class="notify-item"
        :class="{ unread: !n.isRead }"
        @click="readOne(n)"
      >
        <div class="notify-item__head">
          <span class="notify-item__title">{{ n.title }}</span>
          <span v-if="!n.isRead" class="notify-dot" />
        </div>
        <div class="notify-item__body">{{ n.content }}</div>
        <div class="notify-item__time">{{ formatDateTime(n.createdAt) }}</div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { notifyApi, authApi } from '@/api'
import { formatDateTime } from '@/utils/time'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const isCollapse = ref(false)
const unreadCount = ref(0)

const roleNames = { 1: '院领导', 2: '管理教师', 3: '班团骨干', 4: '学生' }
const roleName = computed(() => roleNames[userStore.roleLevel] || '未知角色')

const menuItems = [
  { path: '/dashboard', title: '数据概览', icon: 'DataAnalysis', minRole: 2 },
  {
    path: '/qa', title: '智能问答', icon: 'ChatDotSquare',
    children: [
      { path: '/qa/knowledge', title: '知识库管理', minRole: 2 },
      { path: '/qa/document', title: '政策文档', minRole: 2 },
      { path: '/qa/template', title: '办公模板', minRole: 2 },
    ],
  },
  {
    path: '/party', title: '党团流程', icon: 'List',
    children: [
      { path: '/party/template', title: '流程模板', minRole: 2 },
      { path: '/party/instance', title: '学生流程', minRole: 2 },
    ],
  },
  {
    path: '/approval', title: '审批管理', icon: 'Tickets',
    children: [
      { path: '/approval/pending', title: '待审批', minRole: 2 },
      { path: '/approval/all', title: '全部申请', minRole: 2 },
    ],
  },
  { path: '/student/list', title: '学生信息', icon: 'UserFilled', minRole: 3 },
  {
    path: '/system', title: '系统管理', icon: 'Setting',
    children: [
      { path: '/system/notify', title: '通知群发', minRole: 2 },
      { path: '/system/user', title: '用户管理', minRole: 2 },
      { path: '/system/log', title: '操作日志', minRole: 1 },
    ],
  },
]

// 面包屑: 首页 / 分组 / 当前页 (分组从菜单结构里反查当前路由所属的父级菜单)
const breadcrumb = computed(() => {
  const path = route.path
  const current = route.meta.title || '工作台'
  let group = ''
  for (const item of menuItems) {
    if (item.children && item.children.some(c => c.path === path)) {
      group = item.title
      break
    }
  }
  return { group, current }
})

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/dashboard')
}

const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

// ===== 消息中心 =====
const notifyVisible = ref(false)
const notifyLoading = ref(false)
const notifyList = ref([])

async function loadNotifyList() {
  notifyLoading.value = true
  try {
    const res = await notifyApi.getPage({ page: 1, size: 30 })
    notifyList.value = res.data?.records || []
  } finally {
    notifyLoading.value = false
  }
}

async function openNotify() {
  notifyVisible.value = true
  await loadNotifyList()
}

async function readOne(n) {
  if (n.isRead) return
  try {
    await notifyApi.markRead(n.id)
    n.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (e) { /* ignore */ }
}

async function markAllRead() {
  try {
    await notifyApi.markAllRead()
    notifyList.value.forEach(n => { n.isRead = true })
    unreadCount.value = 0
    ElMessage.success('已全部标记为已读')
  } catch (e) { /* ignore */ }
}

function handleCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'password') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    pwdVisible.value = true
  }
}

async function submitPassword() {
  if (!pwdForm.oldPassword) { ElMessage.warning('请输入原密码'); return }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位'); return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致'); return
  }
  if (pwdForm.oldPassword === pwdForm.newPassword) {
    ElMessage.warning('新密码不能与原密码相同'); return
  }
  pwdSubmitting.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码已修改, 请用新密码重新登录')
    pwdVisible.value = false
    userStore.logout()
    router.push('/login')
  } finally {
    pwdSubmitting.value = false
  }
}

onMounted(async () => {
  try {
    const res = await notifyApi.getUnreadCount()
    unreadCount.value = res.data?.count || 0
  } catch (e) { /* ignore */ }
})
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
  background: var(--app-bg);
}

.layout-aside {
  background: var(--app-sidebar);
  transition: width 0.25s ease;
  overflow: hidden;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  color: #fff;
  border-bottom: 1px solid rgba(255,255,255,.08);

  &.collapsed {
    justify-content: center;
    padding: 0;
  }
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  background: var(--app-primary);
  font-weight: 700;
}

.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;

  strong {
    font-size: 15px;
    line-height: 1.2;
    white-space: nowrap;
  }

  span {
    margin-top: 4px;
    color: rgba(255,255,255,.58);
    font-size: 12px;
  }
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 44px;
  margin: 4px 8px;
  border-radius: 6px;
}

:deep(.el-menu-item.is-active) {
  background: var(--app-sidebar-active);
  position: relative;
}

:deep(.el-menu-item.is-active::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 9px;
  bottom: 9px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--app-primary);
}

.layout-header {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  color: var(--app-text-regular);
}

.back-btn {
  color: var(--app-text-regular);
  font-size: 13px;
}

.crumb {
  font-size: 14px;
}

.notify-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.notify-count {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.notify-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notify-item {
  padding: 12px 14px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.notify-item:hover {
  background: var(--app-bg);
}

.notify-item.unread {
  border-color: var(--app-primary);
  background: rgba(155, 44, 54, 0.04);
}

.notify-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.notify-item__title {
  font-weight: 650;
  color: var(--app-text);
}

.notify-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--app-primary);
  flex-shrink: 0;
}

.notify-item__body {
  margin-top: 6px;
  color: var(--app-text-regular);
  font-size: 13px;
  line-height: 1.5;
}

.notify-item__time {
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.route-title {
  display: flex;
  flex-direction: column;

  span {
    color: var(--app-text);
    font-size: 15px;
    font-weight: 650;
  }

  small {
    margin-top: 2px;
    color: var(--app-text-secondary);
    font-size: 12px;
  }
}

.user-info {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--app-text);

  em {
    font-style: normal;
    color: var(--app-text-secondary);
    font-size: 12px;
  }
}

.layout-main {
  background: var(--app-bg);
  padding: 20px;
  overflow: auto;
}
</style>
