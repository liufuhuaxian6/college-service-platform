<template>
  <view class="chat-page">
    <scroll-view class="chat-list" scroll-y :scroll-into-view="scrollIntoId">
      <view class="assistant-card">
        <view class="assistant-top">
          <view class="assistant-avatar">AI</view>
          <view>
            <text class="assistant-name">学院智能问答</text>
            <text class="assistant-status">基于政策文档与知识库检索回答</text>
          </view>
        </view>
        <text class="assistant-desc">
          你可以咨询报到入学、证明申请、党团流程、政策制度等事项。涉及最终办理要求时，请以学院正式通知为准。
        </text>
      </view>

      <view v-if="messages.length === 1" class="prompt-panel">
        <text class="prompt-title">可以这样问</text>
        <view class="prompt-list">
          <view v-for="item in suggestions" :key="item" class="prompt-chip" @click="askSuggestion(item)">
            <text>{{ item }}</text>
          </view>
        </view>
      </view>

      <view
        v-for="msg in messages"
        :id="`msg-${msg.id}`"
        :key="msg.id"
        class="msg-row"
        :class="`msg-row-${msg.role}`"
      >
        <view v-if="msg.role === 'ai'" class="msg-avatar">AI</view>
        <view class="msg-main">
          <view class="bubble" :class="[`bubble-${msg.role}`, { pending: msg.pending }]">
            <view v-if="msg.pending" class="typing">
              <view class="typing-dot" />
              <view class="typing-dot" />
              <view class="typing-dot" />
              <text class="typing-text">正在检索政策依据</text>
            </view>
            <text v-else class="bubble-text" :class="`bubble-text-${msg.role}`">{{ msg.content }}</text>
          </view>

          <view v-if="msg.sources?.length" class="source-panel">
            <text class="source-title">参考依据</text>
            <view v-for="(source, index) in msg.sources" :key="index" class="source-item">
              <text class="source-index">{{ index + 1 }}</text>
              <text class="source-text">{{ source }}</text>
            </view>
          </view>
        </view>
        <view v-if="msg.role === 'user'" class="msg-avatar user-avatar">我</view>
      </view>

      <view class="bottom-space" />
    </scroll-view>

    <view class="input-shell">
      <view class="input-tools">
        <view class="tool-pill" @click="navigateTo('/pages/qa/document')">政策文档</view>
        <view class="tool-pill" @click="askSuggestion('本科新生什么时候报到？')">新生报到</view>
        <view class="tool-pill" @click="askSuggestion('证明申请审批需要多久？')">证明审批</view>
      </view>

      <view class="composer">
        <view class="input-card">
          <view class="input-prefix">问</view>
          <input
            v-model="inputText"
            class="input"
            placeholder="输入你的问题"
            confirm-type="send"
            :disabled="sending"
            @confirm="send"
          />
        </view>
        <view v-if="canSend" class="send-fab send-fab-ready" @click="send">
          <text class="send-fab-text send-fab-text-ready">发送</text>
        </view>
        <view v-else class="send-fab send-fab-disabled" @click="send">
          <text class="send-fab-text send-fab-text-disabled">{{ sending ? '检索' : '发送' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, nextTick, watch } from 'vue'
import { qaApi } from '@/api'

const suggestions = [
  '本科新生什么时候报到？',
  '什么情况下可以申请保留入学资格？',
  '党员发展流程有哪些节点？',
  '在读证明如何申请？',
]

const messages = ref([
  {
    id: 1,
    role: 'ai',
    content: '你好，我是学院智能问答助手。请尽量描述具体场景，我会优先检索政策文档并给出依据。',
  },
])
const inputText = ref('')
const scrollIntoId = ref('msg-1')
const sending = ref(false)
const canSend = computed(() => !!inputText.value.trim() && !sending.value)
let nextId = 2

async function scrollToBottom() {
  await nextTick()
  const last = messages.value[messages.value.length - 1]
  if (last) scrollIntoId.value = `msg-${last.id}`
}

function navigateTo(url) {
  uni.navigateTo({ url })
}

function askSuggestion(question) {
  if (sending.value) return
  inputText.value = question
  send()
}

function normalizeSources(data) {
  const raw = data?.sources || data?.references || data?.documents || data?.chunks || []
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => {
      if (typeof item === 'string') return item
      return item.title || item.documentName || item.source || item.content || item.text || ''
    })
    .filter(Boolean)
    .slice(0, 3)
}

async function send() {
  const q = inputText.value.trim()
  if (!q || sending.value) return

  sending.value = true
  messages.value.push({ id: nextId++, role: 'user', content: q })
  const pendingAiId = nextId++
  messages.value.push({ id: pendingAiId, role: 'ai', content: '', pending: true })
  inputText.value = ''
  await scrollToBottom()

  try {
    const res = await qaApi.chat({ question: q })
    const target = messages.value.find((m) => m.id === pendingAiId)
    if (target) {
      target.content = res?.data?.answer || '未获取到回复内容。'
      target.sources = normalizeSources(res?.data)
      target.pending = false
    }
  } catch (e) {
    const target = messages.value.find((m) => m.id === pendingAiId)
    if (target) {
      target.content = '抱歉，暂时无法获取回复，请稍后再试。'
      target.pending = false
    }
  } finally {
    sending.value = false
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
.chat-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 50% 0%, rgba(155, 44, 54, 0.1), transparent 35%),
    linear-gradient(180deg, #FBF7F5 0%, var(--mp-bg) 42%, var(--mp-bg) 100%);
}

.chat-list {
  height: 100vh;
  padding: 24rpx 24rpx 0;
  box-sizing: border-box;
}

.assistant-card {
  padding: 28rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #9B2C36 0%, #7E2630 100%);
  box-shadow: 0 16rpx 38rpx rgba(155, 44, 54, 0.2);
}

.assistant-top {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.assistant-avatar,
.msg-avatar {
  width: 58rpx;
  height: 58rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 18rpx;
  font-size: 22rpx;
  font-weight: 800;
}

.assistant-avatar {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.assistant-name {
  display: block;
  color: #fff;
  font-size: 32rpx;
  font-weight: 800;
}

.assistant-status {
  display: block;
  margin-top: 4rpx;
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}

.assistant-desc {
  display: block;
  margin-top: 24rpx;
  color: rgba(255, 255, 255, 0.84);
  font-size: 24rpx;
  line-height: 1.65;
}

.prompt-panel {
  margin: 18rpx 0 26rpx;
  padding: 22rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10rpx 26rpx rgba(31, 35, 41, 0.05);
}

.prompt-title {
  display: block;
  margin-bottom: 16rpx;
  color: var(--mp-text-main);
  font-size: 26rpx;
  font-weight: 700;
}

.prompt-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.prompt-chip {
  max-width: 100%;
  padding: 13rpx 18rpx;
  border-radius: 999rpx;
  background: #fff;
  border: 1rpx solid rgba(155, 44, 54, 0.13);
  color: var(--mp-text-regular);
  font-size: 23rpx;
  line-height: 1.35;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-bottom: 24rpx;
}

.msg-row-user {
  justify-content: flex-end;
}

.msg-avatar {
  background: #fff;
  color: var(--mp-primary);
  border: 1rpx solid rgba(155, 44, 54, 0.13);
}

.user-avatar {
  background: #9B2C36;
  color: #fff;
  border-color: #9B2C36;
}

.msg-main {
  max-width: 78%;
}

.bubble {
  padding: 22rpx 24rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.96);
  border: 1rpx solid rgba(31, 35, 41, 0.06);
  box-shadow: 0 8rpx 22rpx rgba(31, 35, 41, 0.04);
}

.bubble-ai {
  border-top-left-radius: 8rpx;
}

.bubble-user {
  border-top-right-radius: 8rpx;
  background: #9B2C36;
  border-color: #9B2C36;
  box-shadow: 0 10rpx 24rpx rgba(155, 44, 54, 0.18);
}

.bubble-text {
  color: var(--mp-text-main);
  font-size: 28rpx;
  line-height: 1.68;
  white-space: pre-wrap;
}

.bubble-text-user {
  color: #fff;
}

.bubble-text-ai {
  color: var(--mp-text-main);
}

.typing {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.typing-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: var(--mp-primary);
  opacity: 0.45;
}

.typing-text {
  margin-left: 8rpx;
  color: var(--mp-text-sub);
  font-size: 24rpx;
}

.source-panel {
  margin-top: 12rpx;
  padding: 18rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.76);
  border: 1rpx solid rgba(155, 44, 54, 0.1);
}

.source-title {
  display: block;
  margin-bottom: 10rpx;
  color: var(--mp-primary);
  font-size: 22rpx;
  font-weight: 700;
}

.source-item {
  display: flex;
  gap: 10rpx;
  margin-top: 8rpx;
}

.source-index {
  width: 28rpx;
  height: 28rpx;
  line-height: 28rpx;
  text-align: center;
  border-radius: 50%;
  background: var(--mp-primary-light);
  color: var(--mp-primary);
  font-size: 18rpx;
}

.source-text {
  flex: 1;
  color: var(--mp-text-regular);
  font-size: 22rpx;
  line-height: 1.45;
}

.bottom-space {
  height: 230rpx;
}

.input-shell {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 14rpx 20rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid rgba(31, 35, 41, 0.06);
  box-shadow: 0 -10rpx 30rpx rgba(31, 35, 41, 0.06);
  box-sizing: border-box;
}

.input-tools {
  display: flex;
  gap: 12rpx;
  margin-bottom: 14rpx;
  overflow-x: auto;
  white-space: nowrap;
}

.tool-pill {
  flex-shrink: 0;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: #F5F1F1;
  color: var(--mp-text-regular);
  font-size: 22rpx;
  border: 1rpx solid rgba(155, 44, 54, 0.08);
}

.composer {
  position: relative;
  min-height: 88rpx;
  padding-right: 118rpx;
  box-sizing: border-box;
}

.input-card {
  width: 100%;
  height: 88rpx;
  display: flex;
  align-items: center;
  gap: 10rpx;
  padding: 10rpx 18rpx;
  border-radius: 26rpx;
  background: #fff;
  border: 1rpx solid rgba(155, 44, 54, 0.12);
  box-shadow: 0 10rpx 26rpx rgba(31, 35, 41, 0.08);
  box-sizing: border-box;
}

.input-prefix {
  width: 48rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  flex-shrink: 0;
  border-radius: 14rpx;
  background: var(--mp-primary-light);
  color: var(--mp-primary);
  font-size: 22rpx;
  font-weight: 800;
}

.input {
  flex: 1;
  width: 100%;
  min-width: 0;
  height: 68rpx;
  padding: 0 8rpx;
  background: transparent;
  font-size: 28rpx;
  color: var(--mp-text-main);
  box-sizing: border-box;
}

.send-fab {
  position: absolute;
  right: 0;
  top: 2rpx;
  width: 104rpx;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 18rpx rgba(31, 35, 41, 0.08);
  z-index: 20;
  box-sizing: border-box;
}

.send-fab-ready {
  background: #9B2C36;
  box-shadow: 0 8rpx 18rpx rgba(155, 44, 54, 0.24);
}

.send-fab-disabled {
  background: #F0F1F3;
}

.send-fab-text {
  display: block;
  width: 104rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-weight: 800;
  text-align: center;
}

.send-fab-text-ready {
  color: #FFFFFF;
  font-size: 26rpx;
}

.send-fab-text-disabled {
  color: #9AA1AA;
  font-size: 28rpx;
}
</style>
