<template>
  <view class="chat-page mp-page-bg">
    <scroll-view
      class="chat-scroll"
      scroll-y
      :scroll-into-view="scrollIntoId"
      :scroll-with-animation="true"
    >
      <!-- ===== 欢迎态 ===== -->
      <view v-if="!messages.length" class="welcome">
        <view class="welcome-ai">
          <RucSeal :size="76" tone="light" />
        </view>
        <text class="welcome-title">学院智能问答</text>
        <text class="welcome-sub">政策制度 · 报到入学 · 党团事务 · 证明办理，随时问我</text>
        <view class="example-grid">
          <view
            v-for="item in suggestions"
            :key="item"
            class="example-card"
            @click="askSuggestion(item)"
          >
            <text class="example-q">{{ item }}</text>
            <text class="example-go">提问 ›</text>
          </view>
        </view>
        <text class="welcome-note">答案附政策参考依据 · 最终办理请以学院正式通知为准</text>
      </view>

      <!-- ===== 对话态 ===== -->
      <view v-else class="conversation">
        <view
          v-for="msg in messages"
          :id="`msg-${msg.id}`"
          :key="msg.id"
          :class="['turn', `turn-${msg.role}`]"
        >
          <!-- 用户消息 -->
          <view v-if="msg.role === 'user'" class="user-bubble">
            <text class="user-text">{{ msg.content }}</text>
          </view>

          <!-- AI 消息 -->
          <view v-else class="ai-turn">
            <view class="ai-avatar"><RucSeal :size="34" tone="light" /></view>
            <view class="ai-body">
              <view v-if="msg.pending" class="ai-thinking">
                <view class="dot" /><view class="dot" /><view class="dot" />
                <text class="thinking-text">正在检索政策依据…</text>
              </view>

              <view v-else class="md">
                <block v-for="(b, bi) in parseBlocks(msg.display)" :key="bi">
                  <text v-if="b.type === 'h'" class="md-h">{{ b.text }}</text>
                  <view v-else-if="b.type === 'li'" class="md-li">
                    <text class="md-dot">•</text><text class="md-li-text">{{ b.text }}</text>
                  </view>
                  <view v-else-if="b.type === 'oli'" class="md-li">
                    <text class="md-num">{{ b.num }}</text><text class="md-li-text">{{ b.text }}</text>
                  </view>
                  <view v-else-if="b.type === 'gap'" class="md-gap" />
                  <text v-else class="md-p">{{ b.text }}</text>
                </block>
                <text v-if="msg.typing" class="md-caret">▍</text>
              </view>

              <!-- 参考来源: 可点击查看原文片段 -->
              <view
                v-if="msg.references && msg.references.length && !msg.pending && !msg.typing"
                class="refs"
              >
                <text class="refs-label">参考来源 · {{ msg.references.length }}</text>
                <view class="refs-list">
                  <view
                    v-for="(r, ri) in msg.references"
                    :key="ri"
                    class="ref-chip"
                    @click="openRef(r)"
                  >
                    <text class="ref-i">{{ ri + 1 }}</text>
                    <text class="ref-title">{{ r.title }}</text>
                    <text class="ref-go">›</text>
                  </view>
                </view>
              </view>

              <!-- 操作 -->
              <view v-if="!msg.pending && msg.content && !msg.typing" class="ai-actions">
                <view class="ai-act" :class="{ done: msg.copied }" @click="copyText(msg)">
                  <view v-if="msg.copied" class="ic-check"><view class="ic-check-stem" /></view>
                  <view v-else class="ic-copy"><view class="ic-copy-back" /><view class="ic-copy-front" /></view>
                </view>
              </view>
            </view>
          </view>
        </view>
        <view class="bottom-space" />
      </view>
    </scroll-view>

    <!-- ===== 输入区 ===== -->
    <view class="input-area">
      <!-- 欢迎态: 快捷标签; 对话态: 新对话 -->
      <scroll-view v-if="!messages.length" scroll-x class="chips" :show-scrollbar="false">
        <view class="chip" @click="navigateTo('/pages/qa/document')">政策文档</view>
        <view class="chip" @click="askSuggestion('本科新生什么时候报到？')">新生报到</view>
        <view class="chip" @click="askSuggestion('本科生可以申请休学吗？')">休学复学</view>
        <view class="chip" @click="askSuggestion('什么情况下会被退学？')">学籍处理</view>
      </scroll-view>
      <view v-else class="bar-top">
        <view class="newchat" @click="newChat">
          <text class="newchat-plus">＋</text><text>新对话</text>
        </view>
      </view>

      <view class="composer">
        <textarea
          class="composer-input"
          v-model="inputText"
          placeholder="发消息问学院智能助手…"
          placeholder-class="composer-ph"
          :disabled="sending"
          :auto-height="true"
          :show-confirm-bar="false"
          :cursor-spacing="24"
          :adjust-position="true"
          confirm-type="send"
          @confirm="send"
        />
        <view class="send-btn" :class="{ active: canSend }" @click="send">
          <text class="send-arrow">↑</text>
        </view>
      </view>
    </view>

    <!-- ===== 来源原文侧栏 ===== -->
    <view v-if="refPanel.show" class="side-mask" :class="{ open: refPanel.open }" @click="closeRef">
      <view class="side-panel" :class="{ open: refPanel.open }" @click.stop>
        <view class="side-head">
          <view class="side-head-l">
            <text class="side-kicker">参考来源 · 政策原文</text>
            <text class="side-title">{{ refPanel.ref.title }}</text>
            <text v-if="refPanel.ref.category" class="side-cat">{{ refPanel.ref.category }}</text>
          </view>
          <view class="side-close" @click="closeRef"><text>✕</text></view>
        </view>
        <scroll-view scroll-y class="side-body">
          <text class="side-snippet">{{ refPanel.ref.snippet }}</text>
          <text class="side-note">以上为本次问答命中的政策原文片段，完整内容请下载查看。</text>
        </scroll-view>
        <view class="side-foot">
          <view class="side-dl" @click="downloadRef">
            <text class="side-dl-icon">↓</text><text>下载原文档</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, nextTick } from 'vue'
import { qaApi, BASE_URL } from '@/api'
import RucSeal from '@/components/RucSeal.vue'

const suggestions = [
  '本科新生什么时候报到？',
  '什么情况下可以保留入学资格？',
  '本科生可以申请休学吗？',
  '国庆节怎么放假？',
]

const messages = ref([]) // 空 = 欢迎态
const inputText = ref('')
const scrollIntoId = ref('')
const sending = ref(false)
const canSend = computed(() => !!inputText.value.trim() && !sending.value)
let nextId = 1

async function scrollToBottom() {
  await nextTick()
  const last = messages.value[messages.value.length - 1]
  if (last) scrollIntoId.value = `msg-${last.id}`
}

function navigateTo(url) {
  uni.navigateTo({ url })
}

function newChat() {
  if (sending.value) return
  messages.value = []
  inputText.value = ''
}

function askSuggestion(question) {
  if (sending.value) return
  inputText.value = question
  send()
}

function copyText(msg) {
  const t = typeof msg === 'string' ? msg : (msg && msg.content)
  if (!t) return
  uni.setClipboardData({
    data: t,
    success: () => {
      if (msg && typeof msg === 'object') {
        msg.copied = true
        setTimeout(() => { msg.copied = false }, 1500)
      }
      uni.showToast({ title: '已复制', icon: 'none' })
    },
  })
}

// 将 AI 回复按行解析为段落 / 标题 / 列表块, 渲染出 markdown 排版感
function parseBlocks(text) {
  if (!text) return []
  return text.split('\n').map((line) => {
    let t = line.trim()
    if (!t) return { type: 'gap' }
    // 去掉行内粗体/代码标记
    t = t.replace(/\*\*(.+?)\*\*/g, '$1').replace(/`(.+?)`/g, '$1')
    if (/^#{1,6}\s/.test(t)) return { type: 'h', text: t.replace(/^#{1,6}\s/, '') }
    if (/^[-*•·]\s+/.test(t)) return { type: 'li', text: t.replace(/^[-*•·]\s+/, '') }
    const om = t.match(/^(\d+)[.、)]\s*/)
    if (om) return { type: 'oli', num: om[1] + '.', text: t.replace(/^(\d+)[.、)]\s*/, '') }
    return { type: 'p', text: t }
  })
}

// ===== 来源引用侧栏 =====
const refPanel = ref({ show: false, open: false, ref: {} })

function openRef(r) {
  refPanel.value = { show: true, open: false, ref: r }
  nextTick(() => { refPanel.value.open = true })
}

function closeRef() {
  refPanel.value.open = false
  setTimeout(() => { refPanel.value = { show: false, open: false, ref: {} } }, 220)
}

function downloadRef() {
  const r = refPanel.value.ref
  if (!r || !r.documentId) {
    uni.showToast({ title: '该来源暂无可下载文件', icon: 'none' })
    return
  }
  const token = uni.getStorageSync('token') || ''
  uni.showLoading({ title: '下载中' })
  uni.downloadFile({
    url: `${BASE_URL}/qa/document/${r.documentId}/download`,
    header: token ? { Authorization: `Bearer ${token}` } : {},
    success: (res) => {
      if (res.statusCode !== 200) {
        uni.showToast({ title: '下载失败', icon: 'none' })
        return
      }
      uni.openDocument({
        filePath: res.tempFilePath,
        showMenu: true,
        fail: () => {
          uni.showModal({
            title: '提示',
            content: '当前环境不支持直接预览，请点击右上 ··· 用其他应用打开或保存到手机。',
            showCancel: false,
          })
        },
      })
    },
    fail: () => uni.showToast({ title: '下载失败', icon: 'none' }),
    complete: () => uni.hideLoading(),
  })
}

// 打字机效果: 拿到完整答案后逐字显示, 营造流式输出感
function typewriter(msg, fullText) {
  msg.content = fullText
  msg.display = ''
  msg.typing = true
  let i = 0
  const step = Math.max(1, Math.round(fullText.length / 240)) // 长文本加速
  const timer = setInterval(() => {
    i += step + 1
    msg.display = fullText.slice(0, i)
    if (i % 24 < step + 1) scrollToBottom()
    if (i >= fullText.length) {
      msg.display = fullText
      msg.typing = false
      clearInterval(timer)
      scrollToBottom()
    }
  }, 18)
}

async function send() {
  const q = inputText.value.trim()
  if (!q || sending.value) return

  sending.value = true
  // 多轮上下文: 取当前问题之前最近 6 条已完成对话 (ai 角色转 assistant)
  const history = messages.value
    .filter((m) => !m.pending && m.content && (m.role === 'user' || m.role === 'ai'))
    .slice(-6)
    .map((m) => ({ role: m.role === 'ai' ? 'assistant' : 'user', content: m.content }))
  messages.value.push({ id: nextId++, role: 'user', content: q })
  const aiId = nextId++
  messages.value.push({ id: aiId, role: 'ai', content: '', display: '', pending: true, typing: false })
  inputText.value = ''
  await scrollToBottom()

  try {
    const res = await qaApi.chat({ question: q, history })
    const target = messages.value.find((m) => m.id === aiId)
    if (target) {
      target.references = res?.data?.references || []
      target.pending = false
      typewriter(target, res?.data?.answer || '未获取到回复内容。')
    }
  } catch (e) {
    const target = messages.value.find((m) => m.id === aiId)
    if (target) {
      target.pending = false
      typewriter(target, '抱歉，暂时无法获取回复，请稍后再试。')
    }
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  padding: 24rpx 24rpx 0;
  box-sizing: border-box;
}

/* ===== 欢迎态 ===== */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 90rpx 16rpx 30rpx;
}

.welcome-ai {
  width: 132rpx;
  height: 132rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--mp-red-gradient);
  box-shadow: 0 14rpx 32rpx rgba(157, 34, 53, 0.28);
}

.welcome-title {
  margin-top: 28rpx;
  color: var(--mp-text-main);
  font-family: var(--mp-font-display);
  font-size: 46rpx;
  font-weight: 800;
  letter-spacing: 4rpx;
  text-indent: 4rpx;
}

.welcome-sub {
  margin-top: 16rpx;
  padding: 0 30rpx;
  color: var(--mp-text-sub);
  font-size: 23rpx;
  text-align: center;
  line-height: 1.6;
}

.example-grid {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-top: 50rpx;
}

.example-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 150rpx;
  padding: 22rpx 22rpx 18rpx;
  border-radius: 22rpx;
  background: #fff;
  border: 1rpx solid rgba(157, 34, 53, 0.1);
  box-shadow: var(--mp-shadow-card);
  transition: transform 0.15s ease;
}

.example-card:active {
  transform: scale(0.98);
}

.example-q {
  color: var(--mp-text-regular);
  font-size: 25rpx;
  line-height: 1.45;
}

.example-go {
  margin-top: 16rpx;
  color: var(--mp-primary);
  font-size: 22rpx;
  font-weight: 600;
}

.welcome-note {
  margin-top: 44rpx;
  padding: 0 20rpx;
  color: var(--mp-text-muted);
  font-size: 21rpx;
  text-align: center;
  line-height: 1.5;
}

/* ===== 对话态 ===== */
.conversation {
  padding-top: 12rpx;
}

.turn {
  display: flex;
  margin-bottom: 36rpx;
}

.turn-user {
  justify-content: flex-end;
}

/* 用户气泡 */
.user-bubble {
  max-width: 80%;
  padding: 20rpx 26rpx;
  border-radius: 26rpx 26rpx 8rpx 26rpx;
  background: var(--mp-red-gradient);
  box-shadow: 0 10rpx 22rpx rgba(157, 34, 53, 0.2);
}

.user-text {
  color: #fff;
  font-size: 28rpx;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* AI 回合 */
.ai-turn {
  display: flex;
  gap: 16rpx;
  width: 100%;
}

.ai-avatar {
  width: 60rpx;
  height: 60rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--mp-red-gradient);
  box-shadow: 0 6rpx 14rpx rgba(157, 34, 53, 0.22);
}

.ai-body {
  flex: 1;
  min-width: 0;
  padding: 22rpx 24rpx;
  border-radius: 8rpx 26rpx 26rpx 26rpx;
  background: #fff;
  border: 1rpx solid rgba(35, 31, 32, 0.06);
  box-shadow: var(--mp-shadow-card);
}

.ai-thinking {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: var(--mp-primary);
  opacity: 0.4;
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-4rpx); }
}

.thinking-text {
  margin-left: 10rpx;
  color: var(--mp-text-sub);
  font-size: 24rpx;
}

/* markdown 排版 */
.md {
  display: flex;
  flex-direction: column;
}

.md-p {
  color: var(--mp-text-main);
  font-size: 28rpx;
  line-height: 1.72;
  word-break: break-word;
}

.md-h {
  margin: 6rpx 0;
  color: var(--mp-text-main);
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1.5;
}

.md-li {
  display: flex;
  gap: 12rpx;
  margin: 4rpx 0;
}

.md-dot {
  color: var(--mp-primary);
  font-size: 28rpx;
  line-height: 1.72;
}

.md-num {
  flex-shrink: 0;
  color: var(--mp-primary);
  font-size: 27rpx;
  font-weight: 700;
  line-height: 1.72;
}

.md-li-text {
  flex: 1;
  color: var(--mp-text-main);
  font-size: 28rpx;
  line-height: 1.72;
  word-break: break-word;
}

.md-gap {
  height: 14rpx;
}

.md-caret {
  color: var(--mp-primary);
  font-size: 26rpx;
  animation: caret 0.8s steps(1) infinite;
}

@keyframes caret {
  50% { opacity: 0; }
}

/* 参考来源 chips */
.refs {
  margin-top: 18rpx;
  padding-top: 14rpx;
  border-top: 1rpx dashed var(--mp-border);
}

.refs-label {
  display: block;
  margin-bottom: 12rpx;
  color: var(--mp-primary);
  font-size: 21rpx;
  font-weight: 700;
}

.refs-list {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.ref-chip {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 14rpx 16rpx;
  border-radius: 14rpx;
  background: var(--mp-bg-warm);
  border: 1rpx solid rgba(157, 34, 53, 0.1);
  transition: background 0.15s ease;
}

.ref-chip:active {
  background: var(--mp-primary-light);
}

.ref-i {
  width: 30rpx;
  height: 30rpx;
  flex-shrink: 0;
  line-height: 30rpx;
  text-align: center;
  border-radius: 8rpx;
  background: var(--mp-primary);
  color: #fff;
  font-size: 19rpx;
  font-weight: 700;
}

.ref-title {
  flex: 1;
  min-width: 0;
  color: var(--mp-text-regular);
  font-size: 23rpx;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ref-go {
  flex-shrink: 0;
  color: var(--mp-primary);
  font-size: 30rpx;
  line-height: 1;
}

/* ===== 来源原文侧栏 ===== */
.side-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
  background: rgba(35, 31, 32, 0);
  transition: background 0.22s ease;
}

.side-mask.open {
  background: rgba(35, 31, 32, 0.42);
}

.side-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 86%;
  display: flex;
  flex-direction: column;
  background: var(--mp-bg);
  box-shadow: -12rpx 0 40rpx rgba(0, 0, 0, 0.18);
  transform: translateX(100%);
  transition: transform 0.24s cubic-bezier(0.25, 0.8, 0.3, 1);
}

.side-panel.open {
  transform: translateX(0);
}

.side-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  padding: 36rpx 28rpx 22rpx;
  color: #fff;
  background: var(--mp-red-gradient);
}

.side-head-l {
  min-width: 0;
}

.side-kicker {
  display: block;
  color: rgba(255, 255, 255, 0.72);
  font-size: 20rpx;
  letter-spacing: 1rpx;
}

.side-title {
  display: block;
  margin-top: 10rpx;
  color: #fff;
  font-family: var(--mp-font-display);
  font-size: 32rpx;
  font-weight: 800;
  line-height: 1.35;
}

.side-cat {
  display: inline-flex;
  margin-top: 12rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.16);
  border: 1rpx solid rgba(255, 255, 255, 0.22);
  color: rgba(255, 255, 255, 0.9);
  font-size: 20rpx;
}

.side-close {
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  font-size: 26rpx;
}

.side-body {
  flex: 1;
  min-height: 0;
  padding: 28rpx 28rpx 0;
  box-sizing: border-box;
}

.side-snippet {
  display: block;
  color: var(--mp-text-main);
  font-size: 27rpx;
  line-height: 1.85;
  white-space: pre-wrap;
  word-break: break-word;
}

.side-note {
  display: block;
  margin: 28rpx 0 30rpx;
  padding-top: 18rpx;
  border-top: 1rpx dashed var(--mp-border);
  color: var(--mp-text-muted);
  font-size: 21rpx;
  line-height: 1.6;
}

.side-foot {
  flex-shrink: 0;
  padding: 16rpx 28rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid var(--mp-border);
}

.side-dl {
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  border-radius: 20rpx;
  background: var(--mp-red-gradient);
  color: #fff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: 0 10rpx 22rpx rgba(157, 34, 53, 0.24);
}

.side-dl-icon {
  font-size: 30rpx;
  font-weight: 800;
}

.ai-actions {
  display: flex;
  gap: 12rpx;
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid var(--mp-border);
}

/* 复制操作: 现代 AI 图标按钮 */
.ai-act {
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10rpx;
  color: var(--mp-text-muted);
  transition: background 0.15s ease;
}

.ai-act:active {
  background: var(--mp-bg-warm);
}

/* 复制图标: 两个重叠圆角方框 */
.ic-copy {
  position: relative;
  width: 30rpx;
  height: 30rpx;
}

.ic-copy-back {
  position: absolute;
  top: 0;
  right: 0;
  width: 20rpx;
  height: 24rpx;
  border: 2.5rpx solid currentColor;
  border-radius: 5rpx;
  box-sizing: border-box;
}

.ic-copy-front {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 20rpx;
  height: 24rpx;
  border: 2.5rpx solid currentColor;
  border-radius: 5rpx;
  background: #fff;
  box-sizing: border-box;
}

/* 已复制: 绿色对勾 */
.ic-check {
  position: relative;
  width: 30rpx;
  height: 30rpx;
}

.ic-check-stem {
  position: absolute;
  left: 9rpx;
  top: 3rpx;
  width: 9rpx;
  height: 18rpx;
  border-right: 3rpx solid var(--mp-success);
  border-bottom: 3rpx solid var(--mp-success);
  transform: rotate(45deg);
  box-sizing: border-box;
}

.bottom-space {
  height: 20rpx;
}

/* ===== 输入区 ===== */
.input-area {
  flex-shrink: 0;
  padding: 14rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.98);
  border-top: 1rpx solid rgba(35, 31, 32, 0.06);
  box-shadow: 0 -8rpx 24rpx rgba(35, 31, 32, 0.05);
  box-sizing: border-box;
}

.chips {
  width: 100%;
  white-space: nowrap;
  margin-bottom: 14rpx;
}

.chip {
  display: inline-flex;
  align-items: center;
  height: 56rpx;
  padding: 0 22rpx;
  margin-right: 12rpx;
  border-radius: 999rpx;
  background: var(--mp-primary-light);
  color: var(--mp-primary);
  font-size: 22rpx;
  font-weight: 600;
}

.bar-top {
  display: flex;
  justify-content: center;
  margin-bottom: 12rpx;
}

.newchat {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  height: 52rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #fff;
  border: 1rpx solid var(--mp-border);
  color: var(--mp-text-regular);
  font-size: 23rpx;
}

.newchat-plus {
  color: var(--mp-primary);
  font-size: 26rpx;
  font-weight: 700;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 14rpx;
}

.composer-input {
  flex: 1;
  min-height: 80rpx;
  max-height: 300rpx;
  padding: 22rpx 24rpx;
  border-radius: 28rpx;
  background: var(--mp-bg-warm);
  border: 1rpx solid rgba(35, 31, 32, 0.08);
  font-size: 28rpx;
  line-height: 1.4;
  color: var(--mp-text-main);
  box-sizing: border-box;
}

.composer-ph {
  color: #A8AFBA;
}

.send-btn {
  width: 80rpx;
  height: 80rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #D9CFC9;
  transition: background 0.18s ease;
}

.send-btn.active {
  background: var(--mp-red-gradient);
  box-shadow: 0 8rpx 18rpx rgba(157, 34, 53, 0.28);
}

.send-arrow {
  color: #fff;
  font-size: 38rpx;
  font-weight: 800;
  line-height: 1;
}
</style>
