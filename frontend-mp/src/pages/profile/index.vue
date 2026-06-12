<template>
  <view class="page mp-page-bg">
    <view class="profile-hero mp-hero">
      <RucSeal :size="300" tone="light" class="mp-hero-seal" />
      <view class="hero-center">
        <RucSeal :size="110" tone="light" class="hero-seal" />
        <text class="name">{{ userInfo.name }}</text>
        <view class="name-underline" />
        <text class="identity">{{ userInfo.major }} · {{ userInfo.className }}</text>
        <text class="role-chip">{{ roleLabel }}</text>
      </view>
    </view>

    <view class="info-card">
      <view class="info-item">
        <text class="info-label">学号</text>
        <text class="info-value">{{ userStore.studentId || profile.studentNo || '-' }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">专业</text>
        <text class="info-value">{{ userInfo.major }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">班级</text>
        <text class="info-value">{{ userInfo.className }}</text>
      </view>
    </view>

    <view class="quick-list">
      <view class="quick-row" @click="switchTab('/pages/approval/index')">
        <view class="quick-text">
          <text class="quick-title">我的申请</text>
          <text class="quick-sub">查看历史申请单与办理进度</text>
        </view>
        <text class="quick-arrow">›</text>
      </view>
      <view class="quick-row" @click="navigateTo('/pages/party/index')">
        <view class="quick-text">
          <text class="quick-title">党团进度</text>
          <text class="quick-sub">入党 / 入团各阶段节点进展</text>
        </view>
        <text class="quick-arrow">›</text>
      </view>
      <view class="quick-row" @click="navigateTo('/pages/notify/index')">
        <view class="quick-text">
          <text class="quick-title">消息中心</text>
          <text class="quick-sub">通知与系统提醒</text>
        </view>
        <text class="quick-arrow">›</text>
      </view>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title">联系方式</text>
      </view>
      <view class="contact-row" @click="editEmail">
        <view class="contact-info">
          <text class="contact-label">邮箱</text>
          <text class="contact-value">{{ profile.email || '-' }}</text>
          <text v-if="!profile.emailCustom" class="contact-hint">默认: 学号@ruc.edu.cn, 可修改</text>
        </view>
        <text class="contact-action">修改 ›</text>
      </view>
      <view class="contact-row" @click="editPhone">
        <view class="contact-info">
          <text class="contact-label">手机</text>
          <text class="contact-value">{{ profile.phone || '未填写' }}</text>
        </view>
        <text class="contact-action">修改 ›</text>
      </view>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title">我的荣誉</text>
        <text class="section-count">{{ honors.length }} 项</text>
      </view>

      <view v-for="h in honors" :key="h.id" class="honor-item">
        <view class="honor-medal" :class="medalClass(h.level)">
          <image class="honor-medal__img" src="/static/icons/icon-medal.svg" mode="aspectFit" />
        </view>
        <view class="honor-body">
          <text class="honor-name">{{ h.name }}</text>
          <view class="honor-meta-row">
            <text class="honor-level" :class="medalClass(h.level)">{{ h.level || '其他' }}</text>
            <text class="honor-date">{{ h.date || '-' }}</text>
          </view>
        </view>
      </view>

      <EmptyState v-if="!honors.length" title="暂无荣誉记录" description="荣誉信息由教师端维护后同步展示。" />
    </view>

    <view class="action-panel">
      <view class="action-button secondary" @click="loadProfile">
        <text class="action-text">刷新个人信息</text>
      </view>
      <view class="action-button danger" @click="handleLogout">
        <text class="action-text">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { studentApi, authApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import RucSeal from '@/components/RucSeal.vue'

const userStore = useUserStore()
const profile = ref({})
const honors = ref([])

const roleLabel = computed(() => (Number(userStore.roleLevel) === 3 ? '班团骨干' : '在校学生'))

const userInfo = computed(() => ({
  name: profile.value.name || userStore.name || '同学',
  major: profile.value.major || '-',
  className: profile.value.className || '-',
}))

onMounted(loadProfile)

async function loadProfile() {
  try {
    const [profileRes, honorsRes] = await Promise.all([
      studentApi.getProfile(),
      studentApi.getHonors(),
    ])
    profile.value = profileRes.data || {}
    honors.value = (honorsRes.data || []).map((item) => ({
      id: item.id,
      name: item.honorName,
      level: item.honorLevel,
      date: item.awardDate,
    }))
  } catch (e) {
    profile.value = {}
    honors.value = []
  }
}

function navigateTo(url) {
  uni.navigateTo({ url })
}

function switchTab(url) {
  uni.switchTab({ url })
}

function editEmail() {
  uni.showModal({
    title: '修改邮箱',
    editable: true,
    placeholderText: profile.value.email || '学号@ruc.edu.cn',
    content: profile.value.emailCustom ? (profile.value.email || '') : '',
    success: async (res) => {
      if (!res.confirm) return
      const v = (res.content || '').trim()
      if (v && !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(v)) {
        uni.showToast({ title: '邮箱格式不合法', icon: 'none' })
        return
      }
      try {
        await authApi.updateProfile({ email: v })
        uni.showToast({ title: v ? '已更新' : '已恢复默认' })
        loadProfile()
      } catch (_) { /* api 层已 toast */ }
    },
  })
}

function editPhone() {
  uni.showModal({
    title: '修改手机号',
    editable: true,
    placeholderText: profile.value.phone || '请输入手机号',
    content: profile.value.phone || '',
    success: async (res) => {
      if (!res.confirm) return
      const v = (res.content || '').trim()
      if (v && !/^\d{6,20}$/.test(v)) {
        uni.showToast({ title: '手机号格式不合法', icon: 'none' })
        return
      }
      try {
        await authApi.updateProfile({ phone: v })
        uni.showToast({ title: '已更新' })
        loadProfile()
      } catch (_) { /* api 层已 toast */ }
    },
  })
}

// 荣誉级别 → 奖章配色 (国家级鎏金 / 省部级人大红 / 其他暖灰)
function medalClass(level) {
  if (level === '国家级') return 'medal-gold'
  if (level === '省部级') return 'medal-red'
  if (level === '校级') return 'medal-blue'
  return 'medal-gray'
}

function handleLogout() {
  uni.showModal({
    title: '确认退出',
    content: '确定退出登录吗？',
    success: (res) => {
      if (res.confirm) userStore.logout()
    },
  })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx 24rpx 42rpx;
  box-sizing: border-box;
}

/* 居中式个人 hero */
.profile-hero {
  padding: 44rpx 32rpx 70rpx;
}

.hero-center {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-seal {
  filter: drop-shadow(0 10rpx 24rpx rgba(0, 0, 0, 0.22));
}

.name {
  display: block;
  margin-top: 22rpx;
  color: #fff;
  font-family: var(--mp-font-display);
  font-size: 46rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
}

.name-underline {
  width: 64rpx;
  height: 6rpx;
  margin-top: 14rpx;
  border-radius: 4rpx;
  background: linear-gradient(90deg, rgba(184, 146, 62, 0.2), var(--mp-gold), rgba(184, 146, 62, 0.2));
}

.identity {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.8);
  font-size: 24rpx;
  line-height: 1.4;
}

.role-chip {
  display: inline-flex;
  margin-top: 16rpx;
  padding: 5rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  border: 1rpx solid rgba(255, 255, 255, 0.24);
  color: rgba(255, 255, 255, 0.9);
  font-size: 21rpx;
  letter-spacing: 2rpx;
}

.info-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  margin: -22rpx 18rpx 22rpx;
  padding: 18rpx;
  position: relative;
  z-index: 2;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--mp-shadow);
}

.info-item {
  padding: 12rpx 4rpx;
  text-align: center;
}

.info-label {
  display: block;
  color: var(--mp-text-sub);
  font-size: 21rpx;
}

.info-value {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-main);
  font-size: 24rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-list {
  margin-bottom: 22rpx;
  padding: 8rpx 24rpx;
  background: #fff;
  border-radius: 20rpx;
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  box-shadow: 0 10rpx 24rpx rgba(31, 35, 41, 0.04);
}

.quick-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid var(--mp-border);
}

.quick-row:last-child {
  border-bottom: none;
}

.quick-text {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.quick-title {
  color: var(--mp-text-main);
  font-size: 28rpx;
  font-weight: 600;
}

.quick-sub {
  margin-top: 6rpx;
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.quick-arrow {
  flex-shrink: 0;
  color: var(--mp-text-muted);
  font-size: 30rpx;
  line-height: 1;
}

.section {
  margin-bottom: 22rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  box-shadow: 0 10rpx 26rpx rgba(31, 35, 41, 0.05);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.section-title {
  position: relative;
  padding-left: 20rpx;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 800;
}

/* 红金双色装饰条 */
.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6rpx;
  bottom: 6rpx;
  width: 7rpx;
  border-radius: 4rpx;
  background: linear-gradient(180deg, var(--mp-primary) 0%, var(--mp-primary) 62%, var(--mp-gold) 62%, var(--mp-gold) 100%);
}

.section-count {
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.honor-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--mp-border);
}

.honor-item:last-child {
  border-bottom: none;
}

/* 奖章 */
.honor-medal {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
}

.honor-medal__img {
  width: 36rpx;
  height: 36rpx;
}

.honor-medal.medal-gold {
  background: linear-gradient(135deg, #D4B16A, #9A7833);
  box-shadow: 0 6rpx 14rpx rgba(154, 120, 51, 0.32);
}

.honor-medal.medal-red {
  background: var(--mp-red-gradient);
  box-shadow: 0 6rpx 14rpx rgba(157, 34, 53, 0.28);
}

.honor-medal.medal-blue {
  background: linear-gradient(135deg, #5380A8, #356382);
  box-shadow: 0 6rpx 14rpx rgba(53, 99, 130, 0.28);
}

.honor-medal.medal-gray {
  background: linear-gradient(135deg, #9AA1AA, #6E7681);
}

.honor-body {
  flex: 1;
  min-width: 0;
}

.honor-name {
  display: block;
  color: var(--mp-text-main);
  font-size: 27rpx;
  font-weight: 700;
  line-height: 1.45;
}

.honor-meta-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 8rpx;
}

.honor-level {
  padding: 2rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 650;
}

.honor-level.medal-gold {
  color: #8A6422;
  background: var(--mp-gold-light);
}

.honor-level.medal-red {
  color: var(--mp-primary);
  background: var(--mp-primary-light);
}

.honor-level.medal-blue {
  color: #356382;
  background: #EBF1F5;
}

.honor-level.medal-gray {
  color: var(--mp-info);
  background: var(--mp-info-bg);
}

.honor-date {
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.action-panel {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16rpx;
  margin-bottom: 22rpx;
}

.action-button {
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22rpx;
  font-size: 27rpx;
  font-weight: 750;
  box-sizing: border-box;
}

.action-button.secondary {
  background: #fff;
  color: var(--mp-text-main);
  border: 1rpx solid rgba(31, 35, 41, 0.08);
  box-shadow: 0 10rpx 24rpx rgba(31, 35, 41, 0.04);
}

.action-button.danger {
  background: #FFF8F8;
  color: #B4232C;
  border: 1rpx solid #E8B8BA;
}

.action-text {
  color: inherit;
}

.contact-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid var(--mp-border);
  gap: 16rpx;
}

.contact-row:last-child {
  border-bottom: none;
}

.contact-info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.contact-label {
  color: var(--mp-text-sub);
  font-size: 22rpx;
}

.contact-value {
  margin-top: 4rpx;
  color: var(--mp-text-main);
  font-size: 26rpx;
  font-weight: 600;
}

.contact-hint {
  margin-top: 4rpx;
  color: var(--mp-text-muted);
  font-size: 20rpx;
}

.contact-action {
  flex-shrink: 0;
  color: var(--mp-primary);
  font-size: 24rpx;
}
</style>
