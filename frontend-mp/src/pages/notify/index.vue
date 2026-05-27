<template>
  <view class="page">
    <scroll-view scroll-x class="tag-bar" show-scrollbar="false">
      <view class="tag-row">
        <view class="tag" :class="{ active: activeTag === '' }" @click="switchTag('')">
          全部
        </view>
        <view
          v-for="t in tags"
          :key="t"
          class="tag"
          :class="{ active: activeTag === t }"
          @click="switchTag(t)"
        >
          {{ t }}
        </view>
      </view>
    </scroll-view>

    <view class="toolbar">
      <text class="toolbar-title">{{ activeTag || '全部通知' }}</text>
      <text class="mark-all" @click="markAll">全部已读</text>
    </view>

    <view
      v-for="n in list"
      :key="n.id"
      class="notify-item"
      :class="{ unread: !n.isRead }"
      @click="markRead(n)"
    >
      <view class="notify-main">
        <view class="notify-head">
          <view v-if="!n.isRead" class="unread-dot" />
          <text class="notify-title">{{ n.title }}</text>
        </view>
        <text class="notify-content">{{ n.content }}</text>
      </view>

      <view class="notify-foot">
        <view class="tag-chips">
          <text v-if="n.source" class="tag-chip source-chip">{{ n.source }}</text>
          <text v-for="t in splitTags(n.tags)" :key="t" class="tag-chip">{{ t }}</text>
        </view>
        <text class="notify-time">{{ formatTime(n.createdAt) }}</text>
      </view>
    </view>

    <EmptyState v-if="!list.length" title="暂无消息" description="新的审批和系统通知会显示在这里。" />
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { notifyApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'

const list = ref([])
const tags = ref([])
const activeTag = ref('')

onMounted(async () => {
  await loadTags()
  await loadList()
})

async function loadTags() {
  try {
    const res = await notifyApi.getTags()
    tags.value = normalizeTags(res.data || [])
  } catch (_) {
    tags.value = []
  }
}

async function loadList() {
  const params = { page: 1, size: 50 }
  if (activeTag.value) params.tag = activeTag.value
  const res = await notifyApi.getPage(params)
  list.value = res.data?.records || []
}

function normalizeTags(raw) {
  const seen = new Set()
  return raw
    .map((item) => String(item || '').trim())
    .filter(Boolean)
    .filter((item) => {
      if (seen.has(item)) return false
      seen.add(item)
      return true
    })
}

function switchTag(t) {
  if (activeTag.value === t) return
  activeTag.value = t
  loadList()
}

function splitTags(s) {
  if (!s) return []
  return normalizeTags(String(s).split(','))
}

function formatTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2})/)
  if (!match) return text.split('.')[0]
  const now = new Date()
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const prefix = now.getFullYear() === year ? `${month}-${day}` : `${year}-${month}-${day}`
  return `${prefix} ${match[4]}:${match[5]}`
}

async function markRead(n) {
  if (!n.isRead) {
    await notifyApi.markRead(n.id)
    n.isRead = true
  }
}

async function markAll() {
  await notifyApi.markAllRead()
  list.value.forEach((n) => {
    n.isRead = true
  })
  uni.showToast({ title: '已全部标记', icon: 'success' })
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 20rpx 24rpx 36rpx;
  background: linear-gradient(180deg, #FBF7F5 0%, #F6F4F2 320rpx, #F6F4F2 100%);
  box-sizing: border-box;
}

.tag-bar {
  width: 100%;
  margin-bottom: 18rpx;
  white-space: nowrap;
}

.tag-row {
  display: inline-flex;
  gap: 12rpx;
  padding: 4rpx 0 8rpx;
}

.tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 96rpx;
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #5B6472;
  border: 1rpx solid rgba(31, 35, 41, 0.08);
  box-shadow: 0 8rpx 18rpx rgba(31, 35, 41, 0.04);
  font-size: 24rpx;
  font-weight: 650;
  box-sizing: border-box;
}

.tag.active {
  color: #FFFFFF;
  background: #9B2C36;
  border-color: #9B2C36;
  box-shadow: 0 10rpx 22rpx rgba(155, 44, 54, 0.18);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6rpx 0 18rpx;
}

.toolbar-title {
  color: #1F2329;
  font-size: 30rpx;
  font-weight: 800;
}

.mark-all {
  color: #9B2C36;
  font-size: 24rpx;
  font-weight: 700;
}

.notify-item {
  padding: 24rpx;
  margin-bottom: 18rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(31, 35, 41, 0.07);
  border-radius: 22rpx;
  box-shadow: 0 12rpx 30rpx rgba(31, 35, 41, 0.05);
}

.notify-item.unread {
  border-color: rgba(155, 44, 54, 0.22);
}

.notify-head {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
}

.unread-dot {
  width: 12rpx;
  height: 12rpx;
  margin-top: 14rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: #9B2C36;
}

.notify-title {
  flex: 1;
  color: #1F2329;
  font-size: 29rpx;
  font-weight: 800;
  line-height: 1.45;
}

.notify-content {
  display: block;
  margin-top: 10rpx;
  color: #4E5969;
  font-size: 25rpx;
  line-height: 1.62;
}

.notify-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  gap: 16rpx;
}

.tag-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  min-width: 0;
}

.tag-chip {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  color: #5B6472;
  background: #F2F3F5;
  font-size: 20rpx;
  line-height: 1.3;
}

.source-chip {
  color: #9B2C36;
  background: #F7EDEF;
}

.notify-time {
  flex-shrink: 0;
  color: #86909C;
  font-size: 22rpx;
}
</style>
