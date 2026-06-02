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
          <div class="route-title">
            <span>{{ $route.meta.title || '工作台' }}</span>
            <small>学院学生综合服务与党团管理平台</small>
          </div>
        </div>
        <div class="header-right">
          <el-badge :value="unreadCount" :hidden="!unreadCount" class="notify-badge">
            <el-button text circle>
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
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { notifyApi, authApi } from '@/api'

const router = useRouter()
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

const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

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
