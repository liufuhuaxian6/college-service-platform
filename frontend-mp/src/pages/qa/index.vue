<template>
  <view class="chat-page">
    <!-- 消息列表 -->
    <scroll-view class="chat-list" scroll-y :scroll-top="scrollTop">
      <view class="msg" v-for="(msg, i) in messages" :key="i" :class="msg.role">
        <view class="bubble">
          <text>{{ msg.content }}</text>
          <text class="tag" v-if="msg.aiGenerated">AI生成，仅供参考</text>
          <text class="source" v-if="msg.sourceUrl" @click="copyUrl(msg.sourceUrl)">📎 查看官方链接</text>
        </view>
      </view>
    </scroll-view>

    <!-- 输入框 -->
    <view class="input-bar">
      <input class="input" v-model="inputText" placeholder="请输入您的问题..." @confirm="send" />
      <button class="btn-send" @click="send" :disabled="!inputText.trim() || sending">发送</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { qaApi } from '@/api'

const messages = ref([
  { role: 'system', content: '你好！我是学院智能助手，有什么可以帮你的？' },
])
const inputText = ref('')
const sending = ref(false)
const scrollTop = ref(0)

async function send() {
  const q = inputText.value.trim()
  if (!q) return

  messages.value.push({ role: 'user', content: q })
  inputText.value = ''
  sending.value = true
  scrollTop.value = 99999

  try {
    const res = await qaApi.chat({ question: q })
    messages.value.push({
      role: 'system',
      content: res.data.answer,
      aiGenerated: res.data.aiGenerated,
      sourceUrl: res.data.sourceUrl,
    })
  } catch (e) {
    messages.value.push({ role: 'system', content: '抱歉，查询失败，请稍后重试。' })
  } finally {
    sending.value = false
    scrollTop.value = 99999
  }
}

function copyUrl(url) {
  uni.setClipboardData({ data: url })
}
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; background: #f0f2f5; }
.chat-list { flex: 1; padding: 20rpx; }
.msg { margin-bottom: 20rpx; display: flex; }
.msg.user { justify-content: flex-end; }
.msg.system { justify-content: flex-start; }
.bubble {
  max-width: 75%; padding: 20rpx 24rpx; border-radius: 12rpx; font-size: 28rpx; line-height: 1.6;
}
.msg.user .bubble { background: #1a3a5c; color: #fff; }
.msg.system .bubble { background: #fff; color: #333; }
.tag { display: block; font-size: 22rpx; color: #e6a23c; margin-top: 8rpx; }
.source { display: block; font-size: 22rpx; color: #409eff; margin-top: 8rpx; }
.input-bar { display: flex; padding: 16rpx 20rpx; background: #fff; border-top: 1rpx solid #eee; gap: 16rpx; }
.input { flex: 1; border: 1rpx solid #ddd; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; }
.btn-send { background: #1a3a5c; color: #fff; border: none; border-radius: 8rpx; padding: 16rpx 32rpx; font-size: 28rpx; }
</style>
