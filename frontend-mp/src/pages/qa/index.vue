<template>
  <view class="chat-page">
    <view class="nav-bar">
      <text class="nav-title">智能助理</text>
    </view>

    <scroll-view class="chat-list" scroll-y :scroll-into-view="scrollIntoId">
      <view
        v-for="msg in messages"
        :key="msg.id"
        :id="`msg-${msg.id}`"
        class="msg-row"
        :class="msg.role"
      >
        <view class="bubble">
          <text class="bubble-text" :class="{ pending: msg.pending }">{{ msg.content }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="input-bar">
      <input
        class="input"
        v-model="inputText"
        placeholder="请输入您的问题..."
        confirm-type="send"
        @confirm="send"
      />
      <button class="btn-send" @click="send" :disabled="!inputText.trim()">发送</button>
    </view>
  </view>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { qaApi } from '@/api'

const messages = ref([
  {
    id: 1,
    role: 'ai',
    content: '你好，我是学院智能助手，你可以问我关于入党流程、奖学金申请等问题。',
  },
])
const inputText = ref('')
const scrollIntoId = ref('msg-1')
let nextId = 2

async function scrollToBottom() {
  await nextTick()
  const last = messages.value[messages.value.length - 1]
  if (!last) return
  scrollIntoId.value = `msg-${last.id}`
}

async function send() {
  const q = inputText.value.trim()
  if (!q) return

  messages.value.push({ id: nextId++, role: 'user', content: q })
  const pendingAiId = nextId++
  messages.value.push({ id: pendingAiId, role: 'ai', content: '正在思考...', pending: true })
  inputText.value = ''
  await scrollToBottom()

  try {
    const res = await qaApi.chat({ question: q })
    // const res = await new Promise((resolve) => {
    //   setTimeout(() => resolve({ data: { answer: `这是模拟回复：你问的是「${q}」` } }), 800)
    // })
    const answer = res?.data?.answer
    const target = messages.value.find((m) => m.id === pendingAiId)
    if (target) {
      target.content = answer || '（未获取到回复内容）'
      target.pending = false
    }
  } catch (e) {
    const target = messages.value.find((m) => m.id === pendingAiId)
    if (target) {
      target.content = '抱歉，暂时无法获取回复，请稍后再试。'
      target.pending = false
    }
  } finally {
    await scrollToBottom()
  }
}

watch(
  () => messages.value.length,
  async () => {
    await scrollToBottom()
  },
)
</script>

<style scoped>
.chat-page { height: 100vh; background: #f0f2f5; }

.nav-bar {
  height: 96rpx;
  background: #1a3a5c;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 24rpx;
}
.nav-title { color: #fff; font-size: 34rpx; font-weight: bold; }

.chat-list {
  height: calc(100vh - 96rpx);
  padding: 20rpx 20rpx calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.msg-row { display: flex; margin-bottom: 20rpx; }
.msg-row.user { justify-content: flex-end; }
.msg-row.ai { justify-content: flex-start; }

.bubble { max-width: 75%; padding: 20rpx 24rpx; border-radius: 12rpx; }
.bubble-text { font-size: 28rpx; line-height: 1.6; }
.msg-row.ai .bubble-text.pending { color: #999; font-size: 24rpx; }
.msg-row.user .bubble { background: #1a3a5c; }
.msg-row.user .bubble-text { color: #fff; }
.msg-row.ai .bubble { background: #fff; }
.msg-row.ai .bubble-text { color: #111; }

.input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  padding: 16rpx 20rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eee;
  gap: 16rpx;
  box-sizing: border-box;
}
.input { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; }
.btn-send { background: #1a3a5c; color: #fff; border: none; border-radius: 8rpx; padding: 16rpx 32rpx; font-size: 28rpx; }
</style>
