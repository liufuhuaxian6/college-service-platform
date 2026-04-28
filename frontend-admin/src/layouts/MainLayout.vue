<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapse">学院综合服务平台</span>
        <span v-else>综</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="isCollapse"
        router
        background-color="#001529"
        text-color="#ffffffb3"
        active-text-color="#409EFF"
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
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="unreadCount" :hidden="!unreadCount" class="notify-badge">
            <el-icon :size="18" style="cursor:pointer"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              {{ userStore.name }} ({{ roleName }})
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

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { notifyApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const unreadCount = ref(0)

const roleNames = { 1: '院领导', 2: '管理老师', 3: '班团骨干', 4: '学生' }
const roleName = computed(() => roleNames[userStore.roleLevel] || '未知')

const menuItems = [
  { path: '/dashboard', title: '数据概览', icon: 'DataAnalysis', minRole: 2 },
  {
    path: '/qa', title: '智能问答', icon: 'ChatDotSquare',
    children: [
      { path: '/qa/knowledge', title: '知识库管理', minRole: 2 },
      { path: '/qa/document', title: '政策文档', minRole: 2 },
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
      { path: '/system/user', title: '用户管理', minRole: 2 },
      { path: '/system/log', title: '操作日志', minRole: 1 },
    ],
  },
]

function handleCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
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
}
.layout-aside {
  background: #001529;
  transition: width 0.3s;
  overflow: hidden;
  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 16px;
    font-weight: bold;
    border-bottom: 1px solid #ffffff1a;
  }
}
.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
  padding: 0 20px;
  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    .collapse-btn { cursor: pointer; font-size: 20px; }
  }
  .header-right {
    display: flex;
    align-items: center;
    gap: 20px;
    .user-info { cursor: pointer; display: flex; align-items: center; gap: 4px; }
  }
}
.layout-main {
  background: #f0f2f5;
  padding: 20px;
}
</style>
