<template>
  <view class="page">
    <!-- 标签筛选条 -->
    <scroll-view scroll-x class="tag-bar" v-if="tags.length">
      <view
        class="tag"
        :class="{ active: activeTag === '' }"
        @click="switchTag('')"
      >全部</view>
      <view
        v-for="t in tags"
        :key="t"
        class="tag"
        :class="{ active: activeTag === t }"
        @click="switchTag(t)"
      >{{ t }}</view>
    </scroll-view>

    <view class="actions">
      <text class="mark-all" @click="markAll">全部标记已读</text>
    </view>

    <view
      v-for="n in list"
      :key="n.id"
      class="notify-item"
      :class="{ unread: !n.isRead }"
      @click="markRead(n)"
    >
      <view class="notify-head">
        <text class="notify-title">{{ n.title }}</text>
        <text v-if="n.source" class="notify-source">{{ n.source }}</text>
      </view>
      <text class="notify-content">{{ n.content }}</text>
      <view class="notify-foot">
        <view class="tag-chips" v-if="splitTags(n.tags).length">
          <text v-for="t in splitTags(n.tags)" :key="t" class="tag-chip">{{ t }}</text>
        </view>
        <text class="notify-time">{{ n.createdAt }}</text>
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
    tags.value = res.data || []
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

function switchTag(t) {
  if (activeTag.value === t) return
  activeTag.value = t
  loadList()
}

function splitTags(s) {
  if (!s) return []
  return s.split(',').map(x => x.trim()).filter(Boolean)
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
  padding: 24rpx;
  background: var(--mp-bg);
  box-sizing: border-box;
}

.actions {
  text-align: right;
  margin-bottom: 16rpx;
}

.mark-all {
  color: var(--mp-primary);
  font-size: 26rpx;
}

.notify-item {
  padding: 24rpx;
  margin-bottom: 12rpx;
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: var(--mp-radius);
}

.notify-item.unread {
  border-left: 8rpx solid var(--mp-primary);
}

.notify-title {
  display: block;
  color: var(--mp-text-main);
  font-size: 28rpx;
  font-weight: 700;
}

.notify-content {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-sub);
  font-size: 26rpx;
  line-height: 1.5;
}

.notify-time {
  color: var(--mp-text-muted);
  font-size: 22rpx;
}

.notify-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.notify-source {
  padding: 2rpx 12rpx;
  font-size: 20rpx;
  color: var(--mp-primary);
  background: var(--mp-primary-light);
  border-radius: 16rpx;
}

.notify-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
  gap: 12rpx;
  flex-wrap: wrap;
}

.tag-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}

.tag-chip {
  padding: 2rpx 12rpx;
  font-size: 20rpx;
  color: var(--mp-text-sub);
  background: #f0eeec;
  border-radius: 16rpx;
}

.tag-bar {
  white-space: nowrap;
  margin-bottom: 16rpx;
  padding-bottom: 4rpx;
}

.tag {
  display: inline-block;
  padding: 8rpx 22rpx;
  margin-right: 12rpx;
  font-size: 24rpx;
  color: var(--mp-text-sub);
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: 32rpx;
}

.tag.active {
  color: #fff;
  background: var(--mp-primary);
  border-color: var(--mp-primary);
}
</style>
