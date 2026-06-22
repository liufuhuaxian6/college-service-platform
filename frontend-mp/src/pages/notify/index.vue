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
      @click="openNotice(n)"
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
  try {
    const res = await notifyApi.getPage(params)
    list.value = res.data?.records || []
  } catch (_) {
    list.value = []
  }
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
  if (n.isRead) return
  try {
    await notifyApi.markRead(n.id)
    n.isRead = true
  } catch (_) { /* request 层已提示 */ }
}

// 点击通知: 标记已读 + 弹出完整内容(正文列表已截断 2 行, 这里看全文)
function openNotice(n) {
  markRead(n)
  const lines = [n.content || '（无正文）']
  if (n.source) lines.push('\n来源：' + n.source)
  if (n.tags) lines.push('标签：' + n.tags)
  uni.showModal({
    title: n.title || '通知',
    content: lines.join('\n'),
    showCancel: !!n.sourceUrl,
    cancelText: '复制链接',
    confirmText: '知道了',
    success: (res) => {
      if (res.cancel && n.sourceUrl) {
        uni.setClipboardData({
          data: n.sourceUrl,
          success: () => uni.showToast({ title: '链接已复制', icon: 'none' }),
        })
      }
    },
  })
}

async function markAll() {
  try {
    await notifyApi.markAllRead()
    list.value.forEach((n) => {
      n.isRead = true
    })
    uni.showToast({ title: '已全部标记', icon: 'success' })
  } catch (_) { /* request 层已提示 */ }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 20rpx 24rpx 36rpx;
  background:
    radial-gradient(circle at 14% 0%, rgba(157, 34, 53, 0.08), transparent 30%),
    linear-gradient(180deg, #FBF7F5 0%, var(--mp-bg) 320rpx, var(--mp-bg) 100%);
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
  background: var(--mp-red-gradient);
  border-color: transparent;
  box-shadow: 0 10rpx 22rpx rgba(157, 34, 53, 0.26);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6rpx 0 18rpx;
}

.toolbar-title {
  position: relative;
  padding-left: 20rpx;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 800;
}

/* 红金双色装饰条 */
.toolbar-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6rpx;
  bottom: 6rpx;
  width: 7rpx;
  border-radius: 4rpx;
  background: linear-gradient(180deg, var(--mp-primary) 0%, var(--mp-primary) 62%, var(--mp-gold) 62%, var(--mp-gold) 100%);
}

.mark-all {
  color: #9D2235;
  font-size: 24rpx;
  font-weight: 700;
}

.notify-item {
  position: relative;
  padding: 18rpx 20rpx 16rpx 26rpx;
  margin-bottom: 12rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(35, 31, 32, 0.07);
  border-radius: 18rpx;
  box-shadow: 0 8rpx 20rpx rgba(35, 31, 32, 0.04);
  overflow: hidden;
  transition: transform 0.15s ease;
}

.notify-item:active {
  transform: scale(0.985);
}

/* 未读: 左侧人大红色条 + 淡红底 */
.notify-item.unread {
  border-color: rgba(157, 34, 53, 0.2);
  background: linear-gradient(90deg, rgba(157, 34, 53, 0.04), #fff 30%);
}

.notify-item.unread::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8rpx;
  background: var(--mp-red-gradient);
}

.notify-head {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
}

.unread-dot {
  width: 11rpx;
  height: 11rpx;
  margin-top: 11rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: #9D2235;
}

.notify-title {
  flex: 1;
  color: #1F2329;
  font-size: 27rpx;
  font-weight: 750;
  line-height: 1.35;
}

.notify-content {
  display: block;
  margin-top: 6rpx;
  color: #4E5969;
  font-size: 23rpx;
  line-height: 1.5;
  /* 长内容 2 行截断, 避免单卡过高 */
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notify-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12rpx;
  gap: 16rpx;
}

.tag-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  min-width: 0;
}

.tag-chip {
  padding: 3rpx 11rpx;
  border-radius: 999rpx;
  color: #5B6472;
  background: #F2F3F5;
  font-size: 19rpx;
  line-height: 1.3;
}

.source-chip {
  color: #9D2235;
  background: #F7EDEF;
}

.notify-time {
  flex-shrink: 0;
  color: #86909C;
  font-size: 22rpx;
}
</style>
