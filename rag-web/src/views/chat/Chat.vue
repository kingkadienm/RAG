<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 左侧会话列表 -->
      <div class="chat-sidebar" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
        <div class="chat-sidebar-header">
          <div class="sidebar-header-top">
            <el-button
              class="sidebar-toggle"
              :icon="sidebarCollapsed ? Expand : Fold"
              @click="toggleSidebar"
              :title="sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
              aria-label="切换侧边栏"
            />
            <el-select
                v-model="selectedKbId"
                placeholder="选择知识库"
                size="small"
                style="flex: 1"
                filterable
                @change="onKbChange"
            >
              <el-option
                  v-for="kb in kbList"
                  :key="kb.id"
                  :label="kb.name"
                  :value="kb.id"
              />
            </el-select>
          </div>
          <el-button
              type="primary"
              size="small"
              style="width: 100%; margin-top: 8px"
              :icon="Plus"
              @click="createNewSession"
          >
            新建对话
          </el-button>
        </div>
        <div class="session-list">
          <div
              v-for="session in sessions"
              :key="session.sessionId"
              :class="['session-item', { active: currentSessionId === session.sessionId }]"
              @click="switchSession(session)"
          >
            <div class="session-title">{{ session.title || '新对话' }}</div>
            <div class="session-time">{{ formatDate(session.updatedTime) }}</div>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无对话" :image-size="60"/>
        </div>
      </div>

      <!-- 侧边栏折叠提示 -->
      <div v-if="sidebarCollapsed" class="sidebar-collapsed-hint" @click="toggleSidebar">
        <el-icon><DArrowRight /></el-icon>
        <span>展开侧边栏</span>
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-main">
        <div class="chat-messages" ref="messagesContainer" @scroll="handleMessageScroll">
          <div v-if="messages.length === 0" class="empty-chat">
            <el-icon :size="64" color="#dcdfe6">
              <ChatDotRound/>
            </el-icon>
            <p>选择一个知识库，开始智能对话</p>
          </div>

          <div v-for="msg in messages" :key="msg.id" :class="['message-row', msg.role === 1 ? 'user' : 'assistant']">
            <div class="message-avatar">
              <el-avatar :size="36" :icon="msg.role === 1 ? UserFilled : BellFilled"
                         :type="msg.role === 1 ? 'primary' : 'success'"/>
            </div>
            <div class="message-body">
              <div class="message-role">{{ msg.role === 1 ? '我' : 'AI 助手' }}</div>
              <div v-if="msg.role === 2" class="message-content markdown-body" v-html="renderMarkdown(msg.content)"></div>
              <div v-else class="message-content">{{ msg.content }}</div>
              <div v-if="msg.refChunks" class="message-refs">
                <div style="font-weight: 500; margin-bottom: 4px">参考文档片段：</div>
                <div>{{ msg.refChunks }}</div>
              </div>
            </div>
          </div>

          <!-- 流式输出中的助手消息 -->
          <div v-if="streamingContent !== null"
               class="message-row assistant"
               role="log"
               aria-live="polite"
               aria-atomic="true"
               aria-relevant="additions">
            <div class="message-avatar">
              <el-avatar :size="36" :icon="BellFilled" type="success"/>
            </div>
            <div class="message-body">
              <div class="message-role">AI 助手</div>
              <div class="message-content markdown-body" v-html="renderMarkdown(streamingContent)" aria-live="assertive"></div>
            </div>
          </div>

          <div v-if="loading && streamingContent === null" class="message-row assistant">
            <div class="message-avatar">
              <el-avatar :size="36" :icon="BellFilled" type="success"/>
            </div>
            <div class="message-body">
              <div class="message-role">AI 助手</div>
              <div class="message-content">
                <el-icon class="is-loading">
                  <Loading/>
                </el-icon>
                正在思考...
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input-area">
          <div class="kb-selector" v-if="!selectedKbId">
            <el-select v-model="selectedKbId" placeholder="请先选择知识库" style="width: 100%">
              <el-option
                  v-for="kb in kbList"
                  :key="kb.id"
                  :label="kb.name"
                  :value="kb.id"
              />
            </el-select>
          </div>
          <div style="display: flex; gap: 12px">
            <el-input
                v-model="inputMessage"
                type="textarea"
                :rows="2"
                placeholder="请输入你的问题..."
                :disabled="!selectedKbId || sending"
                @keydown.enter.ctrl="handleSend"
                aria-label="消息输入框"
                aria-describedby="input-help"
            />
            <span id="input-help" class="visually-hidden">
              按 Ctrl+Enter 发送消息
            </span>
            <el-button
                :icon="Promotion"
                :loading="sending"
                :disabled="!inputMessage.trim() || !selectedKbId"
                :title="getSendButtonTitle()"
                :aria-disabled="!selectedKbId"
                @click="handleSend"
                type="submit"
                aria-label="发送消息"
            >
              发送
            </el-button>
          </div>
        </div>

        <!-- 返回顶部按钮 -->
        <button
          v-if="showBackToTop"
          class="back-to-top"
          @click="scrollToTop"
          aria-label="返回顶部"
          title="返回顶部"
        >
          <el-icon><ArrowUp /></el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, nextTick, onBeforeUnmount, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {UserFilled, BellFilled, Plus, ChatDotRound, Loading, Promotion, ArrowUp, Expand, Fold, DArrowRight} from '@element-plus/icons-vue'
import {chatApi} from '@/api/chat'
import {kbApi} from '@/api/knowledgeBase'
import type {ChatSessionVO, ChatMessageVO, ChatRequest, KnowledgeBase} from '@/types'
import {marked} from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

const kbList = ref<KnowledgeBase[]>([])
const selectedKbId = ref<number | null>(null)
const sessions = ref<ChatSessionVO[]>([])
const currentSessionId = ref('')
const messages = ref<ChatMessageVO[]>([])
const inputMessage = ref('')
const sending = ref(false)
const loading = ref(false)
const streamingContent = ref<string | null>(null)
const messagesContainer = ref<HTMLElement>()
const showBackToTop = ref(false)
const sidebarCollapsed = ref(false)
const userScrolling = ref(false) // P2-3: 用户正在滚动标志
const shouldAutoScroll = ref(true) // P2-3: 是否应该自动滚动

// 配置 marked
marked.setOptions({
  highlight: function(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value
      } catch (e) {
        console.error('[Markdown] 代码高亮失败:', e)
      }
    }
    return hljs.highlightAuto(code).value
  },
  breaks: true, // 启用换行符
  gfm: true, // 启用 GitHub 风格 Markdown
})

/**
 * P2-1: 切换侧边栏折叠状态
 */
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
  // 保存状态到 localStorage
  localStorage.setItem('chat-sidebar-collapsed', String(sidebarCollapsed.value))
}

/**
 * P2-3: 处理消息滚动事件
 */
const handleMessageScroll = () => {
  if (!messagesContainer.value) return

  const { scrollTop, scrollHeight, clientHeight } = messagesContainer.value
  const isAtBottom = scrollHeight - scrollTop - clientHeight < 50 // 距离底部 50px 以内

  // 如果用户在向上滚动（查看历史消息），停止自动滚动
  if (!isAtBottom) {
    userScrolling.value = true
    shouldAutoScroll.value = false
  } else {
    // 如果用户滚动到底部，恢复自动滚动
    userScrolling.value = false
    shouldAutoScroll.value = true
  }

  // 控制返回顶部按钮显示
  showBackToTop.value = scrollTop > 300
}

/**
 * P2-3: 智能滚动到底部
 * - 如果用户正在查看历史消息，不强制滚动
 * - 只在应该自动滚动时滚动
 */
const smartScrollToBottom = () => {
  if (!shouldAutoScroll.value || !messagesContainer.value) return

  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

/**
 * P2-3: 滚动到底部（强制）
 */
const forceScrollToBottom = () => {
  shouldAutoScroll.value = true
  userScrolling.value = false
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTo({
        top: messagesContainer.value.scrollHeight,
        behavior: 'smooth'
      })
    }
  })
}

/**
 * 渲染 Markdown 内容
 */
const renderMarkdown = (content: string) => {
  if (!content) return ''
  try {
    const html = marked.parse(content) as string
    return html
  } catch (e) {
    console.error('[Markdown] 渲染失败:', e)
    return content
  }
}

onMounted(async () => {
  await fetchKbList()
  // 从 URL 参数获取知识库 ID
  const urlKbId = new URLSearchParams(window.location.hash.split('?')[1] || '').get('kbId')
  if (urlKbId) {
    selectedKbId.value = Number(urlKbId)
  }
  fetchSessions()

  // 监听滚动事件
  messagesContainer.value?.addEventListener('scroll', handleMessageScroll)

  // P2-1: 恢复侧边栏状态
  const savedSidebarState = localStorage.getItem('chat-sidebar-collapsed')
  if (savedSidebarState !== null) {
    sidebarCollapsed.value = savedSidebarState === 'true'
  }
})

onBeforeUnmount(() => {
  // 清理滚动事件监听
  messagesContainer.value?.removeEventListener('scroll', handleMessageScroll)
})

const fetchKbList = async () => {
  const res = await kbApi.list()
  kbList.value = res.data
  if (kbList.value.length > 0 && !selectedKbId.value) {
    selectedKbId.value = kbList.value[0].id
  }
}

/**
 * 知识库切换确认对话框
 */
const onKbChange = () => {
  // 如果当前有消息，询问用户是否确认切换
  if (messages.value.length > 0 && selectedKbId.value !== currentSessionId.value) {
    ElMessageBox.confirm(
      '切换知识库将清空当前对话，是否继续？',
      '确认切换',
      {
        confirmButtonText: '确定切换',
        cancelButtonText: '取消',
        type: 'warning',
      }
    ).then(() => {
      currentSessionId.value = ''
      messages.value = []
      fetchSessions()
    }).catch(() => {
      // 恢复选择（如果取消）
      if (currentKbId.value) {
        selectedKbId.value = currentKbId.value
      }
    })
  } else {
    currentSessionId.value = ''
    messages.value = []
    fetchSessions()
  }
}

const fetchSessions = async () => {
  if (!selectedKbId.value) return
  loading.value = true
  try {
    const raw = await chatApi.getSessions()
    const list = Array.isArray(raw) ? raw : (raw?.data ?? [])
    const filtered = list.filter(s => !selectedKbId.value || s.kbId === selectedKbId.value)
    sessions.value = filtered
  } catch (e) {
    console.error('[Chat] fetchSessions error:', e)
  } finally {
    loading.value = false
  }
}

const createNewSession = () => {
  currentSessionId.value = ''
  messages.value = []
}

const switchSession = async (session: ChatSessionVO) => {
  currentSessionId.value = session.sessionId
  const res = await chatApi.getMessages(session.sessionId)
  messages.value = res.data
  shouldAutoScroll.value = true // 切换会话时恢复自动滚动
  forceScrollToBottom()
}

const handleSend = async () => {
  if (!inputMessage.value.trim() || !selectedKbId.value || sending.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  // 添加用户消息到 UI
  const userMsg: ChatMessageVO = {
    id: Date.now(),
    sessionId: currentSessionId.value || 'temp',
    role: 1,
    content: userMessage,
    createdTime: new Date().toISOString(),
  }
  messages.value.push(userMsg)
  shouldAutoScroll.value = true // 发送消息时恢复自动滚动
  smartScrollToBottom()

  sending.value = true
  streamingContent.value = ''
  loading.value = true

  try {
    const data: ChatRequest = {
      question: userMessage,
      sessionId: currentSessionId.value || undefined,
      kbIds: [selectedKbId.value],
    }

    // 流式接收回复，实时更新 UI
    await chatApi.chatStream(data, (chunk) => {
      streamingContent.value += chunk
      smartScrollToBottom() // P2-3: 使用智能滚动
    })

    // 流结束后，更新为最终消息
    const assistantMsg: ChatMessageVO = {
      id: Date.now() + 1,
      sessionId: currentSessionId.value,
      role: 2,
      content: streamingContent.value || '',
      refChunks: '', // 流式模式下暂无参考文档（后端 SSE 简化处理）
      createdTime: new Date().toISOString(),
    }
    messages.value.push(assistantMsg)
    streamingContent.value = null
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    sending.value = false
    loading.value = false
    streamingContent.value = null
    smartScrollToBottom()
  }
}

// P2-3: 使用智能滚动替代强制滚动
const scrollToBottom = smartScrollToBottom

const formatDate = (d: string) => {
  if (!d) return ''
  return new Date(d).toLocaleString('zh-CN', {month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'})
}

const getSendButtonTitle = () => {
  if (!selectedKbId.value) return '请先选择知识库再发送消息'
  if (!inputMessage.value.trim()) return '请输入消息内容'
  return '发送消息 (Ctrl+Enter)'
}

onMounted(() => {
})
</script>

<style scoped>
.chat-page {
  height: 100%;
}

.chat-container {
  height: calc(100vh - 140px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.chat-sidebar {
  width: 280px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.chat-sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
}

.session-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f8f9fa;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c0c4cc;
  font-size: 16px;
  gap: 12px;
}

.message-row {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-body {
  max-width: 70%;
}

.message-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  text-align: right;
}

.message-row.user .message-role {
  text-align: left;
}

.message-content {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
  white-space: pre-wrap;
}

.message-row.assistant .message-content {
  background: #fff;
  border: 1px solid #e4e7ed;
}

.message-row.user .message-content {
  background: #409eff;
  color: #fff;
}

.message-refs {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f0f9ff;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  border: 1px solid #d4edfc;
}

.chat-input-area {
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}

.kb-selector {
  margin-bottom: 12px;
}

/* P2-1: 侧边栏折叠样式 */
.sidebar-header-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-toggle {
  flex-shrink: 0;
}

.sidebar-collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border-right: none;
  transition: all 0.3s ease;
}

.sidebar-collapsed-hint {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  background: var(--color-primary);
  color: #fff;
  padding: 12px 8px;
  border-radius: 0 8px 8px 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  box-shadow: var(--shadow-md);
  transition: all 0.2s ease;
  z-index: 10;

  &:hover {
    background: var(--color-primary-hover);
    padding-left: 12px;
  }

  .el-icon {
    font-size: 18px;
  }
}

/* 屏幕阅读器专用：隐藏内容但保持可访问 */
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
