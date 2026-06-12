<template>
  <div class="shell">
    <!-- ===== 顶部品牌栏 (人大红) ===== -->
    <header class="topbar">
      <div class="topbar-brand">
        <RucSeal :size="40" disc />
        <div class="topbar-name">
          <strong>学院学生综合服务与党团管理平台</strong>
          <span>中国人民大学 · 信息学院</span>
        </div>
      </div>
      <div class="topbar-right">
        <span class="topbar-motto">实事求是</span>
        <el-badge :value="unreadCount" :hidden="!unreadCount" :max="99">
          <button class="topbar-icon" @click="openNotify">
            <el-icon :size="18"><Bell /></el-icon>
          </button>
        </el-badge>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="topbar-user">
            <strong>{{ userStore.name }}</strong>
            <em>{{ roleName }}</em>
            <el-icon class="topbar-caret"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="body">
      <!-- ===== 浅色分组侧边栏 ===== -->
      <aside class="sidenav">
        <nav class="sidenav-scroll">
          <template v-for="group in menuGroups" :key="group.label">
            <div class="nav-group-label">{{ group.label }}</div>
            <router-link
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ active: $route.path === item.path }"
            >
              <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </router-link>
          </template>
        </nav>
        <div class="sidenav-foot">
          <RucSeal :size="26" />
          <span>RUC · 信息学院</span>
        </div>
      </aside>

      <!-- ===== 内容区 ===== -->
      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="route-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>

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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { notifyApi, authApi } from '@/api'
import { formatDateTime } from '@/utils/time'
import RucSeal from '@/components/common/RucSeal.vue'

const router = useRouter()
const userStore = useUserStore()
const unreadCount = ref(0)

const roleNames = { 1: '院领导', 2: '管理教师', 3: '班团骨干', 4: '学生' }
const roleName = computed(() => roleNames[userStore.roleLevel] || '未知角色')

// 扁平分组导航: 分组标签 + 直达条目 (取代旧版折叠子菜单)
const rawGroups = [
  {
    label: '总览',
    items: [{ path: '/dashboard', title: '工作台', icon: 'DataAnalysis', minRole: 2 }],
  },
  {
    label: '智能问答',
    items: [
      { path: '/qa/knowledge', title: '知识库管理', icon: 'ChatDotSquare', minRole: 2 },
      { path: '/qa/document', title: '政策文档', icon: 'Document', minRole: 2 },
      { path: '/qa/template', title: '办公模板', icon: 'CopyDocument', minRole: 2 },
    ],
  },
  {
    label: '党团流程',
    items: [
      { path: '/party/template', title: '流程模板', icon: 'List', minRole: 2 },
      { path: '/party/instance', title: '学生流程', icon: 'Connection', minRole: 2 },
    ],
  },
  {
    label: '审批服务',
    items: [{ path: '/approval/center', title: '审批中心', icon: 'Tickets', minRole: 2 }],
  },
  {
    label: '学生工作',
    items: [{ path: '/student/list', title: '学生信息', icon: 'UserFilled', minRole: 3 }],
  },
  {
    label: '系统管理',
    items: [
      { path: '/system/notify', title: '通知群发', icon: 'BellFilled', minRole: 2 },
      { path: '/system/user', title: '用户管理', icon: 'Setting', minRole: 2 },
      { path: '/system/log', title: '操作日志', icon: 'Notebook', minRole: 1 },
    ],
  },
]

// 按角色过滤: 分组内条目全部无权限时整组隐藏
const menuGroups = computed(() => {
  const level = userStore.roleLevel
  return rawGroups
    .map(g => ({ ...g, items: g.items.filter(i => level <= (i.minRole || 4)) }))
    .filter(g => g.items.length)
})

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
.shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--app-bg);
}

/* ===== 顶部品牌栏 ===== */
.topbar {
  flex: 0 0 auto;
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  color: #fff;
  background:
    radial-gradient(circle at 90% -40%, rgba(255, 255, 255, 0.16), transparent 44%),
    var(--app-red-gradient);
  box-shadow: 0 2px 12px rgba(92, 20, 32, 0.3);
  z-index: 10;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 13px;
  min-width: 0;
}

.topbar-name {
  display: flex;
  flex-direction: column;
  min-width: 0;

  strong {
    font-family: var(--app-font-display);
    font-size: 16px;
    letter-spacing: 1.5px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  span {
    margin-top: 3px;
    color: rgba(255, 255, 255, 0.6);
    font-size: 11px;
    letter-spacing: 1px;
  }
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.topbar-motto {
  font-family: var(--app-font-display);
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  letter-spacing: 8px;
  text-indent: 8px;
}

.topbar-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s var(--app-ease);

  &:hover {
    background: rgba(255, 255, 255, 0.22);
  }
}

.topbar-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.1);
  cursor: pointer;
  transition: background 0.18s var(--app-ease);

  &:hover {
    background: rgba(255, 255, 255, 0.18);
  }

  strong {
    color: #fff;
    font-size: 13.5px;
    font-weight: 600;
  }

  em {
    font-style: normal;
    color: rgba(255, 255, 255, 0.65);
    font-size: 12px;

    &::before {
      content: "·";
      margin-right: 6px;
    }
  }
}

.topbar-caret {
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}

/* ===== 主体 ===== */
.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* ===== 浅色分组侧边栏 ===== */
.sidenav {
  flex: 0 0 212px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--app-border);
}

.sidenav-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 14px 12px;
}

.nav-group-label {
  margin: 14px 10px 6px;
  color: var(--app-text-placeholder);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 2px;

  &:first-child {
    margin-top: 0;
  }
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  margin-bottom: 2px;
  border-radius: 9px;
  color: var(--app-text-regular);
  font-size: 14px;
  text-decoration: none;
  transition: all 0.16s var(--app-ease);

  .nav-icon {
    font-size: 17px;
    color: var(--app-text-secondary);
    transition: color 0.16s var(--app-ease);
  }

  &:hover {
    background: var(--app-muted);
    color: var(--app-text);
  }

  &.active {
    position: relative;
    background: var(--app-primary-light);
    color: var(--app-primary);
    font-weight: 600;

    .nav-icon {
      color: var(--app-primary);
    }

    /* 鎏金指示条 */
    &::before {
      content: "";
      position: absolute;
      left: 0;
      top: 9px;
      bottom: 9px;
      width: 3px;
      border-radius: 0 3px 3px 0;
      background: var(--app-gold);
    }
  }
}

.sidenav-foot {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 0;
  border-top: 1px solid var(--app-border-light);
  color: var(--app-text-placeholder);
  font-size: 11px;
  letter-spacing: 1.5px;
}

/* ===== 内容区 ===== */
.content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 22px 24px;
}

/* 路由切换只做透明度过渡: transform 会短暂使页面成为 fixed 弹窗的包含块 */
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.18s var(--app-ease);
}

.route-fade-enter-from,
.route-fade-leave-to {
  opacity: 0;
}

/* ===== 消息中心抽屉 ===== */
.notify-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
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
  padding: 13px 15px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.notify-item:hover {
  background: var(--app-muted);
}

.notify-item.unread {
  border-color: var(--app-primary-soft);
  background: rgba(157, 34, 53, 0.04);
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
</style>
