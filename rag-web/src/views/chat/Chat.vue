<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 侧边栏 -->
      <ChatSidebar :collapsed="sidebarCollapsed" @toggle-collapse="toggleSidebar">
        <template #header>
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
        </template>

        <template #body>
          <SessionList
              :sessions="sessions"
              :current-session-id="currentSessionId"
              :loading="loading"
              @session-select="switchSession"
          >
            <template v-if="sessions.length > 0" #header>
              <div class="session-list-header-title">历史对话</div>
            </template>
          </SessionList>
        </template>
      </ChatSidebar>

      <!-- 右侧聊天区域 -->
      <div class="chat-main">
        <MessageList
            ref="messageListRef"
            :messages="messages"
            :streaming-content="streamingContent"
            :loading="loading"
            :selected-kb-id="selectedKbId"
            @scroll="handleMessageScroll"
        />

        <div class="chat-input-area">
          <ChatInput
              v-model="inputMessage"
              :selected-kb-id="selectedKbId"
              :kb-list="kbList"
              :sending="sending"
              @send="handleSend"
              @kb-change="onKbChange"
          />
        </div>

        <!-- 返回顶部按钮 -->
        <button
            v-if="showBackToTop"
            class="back-to-top"
            @click="scrollToTop"
            aria-label="返回顶部"
            title="返回顶部"
        >
          <el-icon>
            <ArrowUp/>
          </el-icon>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, nextTick} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  Plus,
  Expand,
  Fold
} from '@element-plus/icons-vue'
import {chatApi} from '@/api/chat'
import {kbApi} from '@/api/knowledgeBase'
import type {ChatSessionVO, ChatMessageVO, ChatRequest, KnowledgeBase} from '@/types'

// 导入组件
import ChatSidebar from './components/ChatSidebar.vue'
import SessionList from './components/SessionList.vue'
import MessageList from './components/MessageList.vue'
import ChatInput from './components/ChatInput.vue'

// ========== 状态管理 ==========

const kbList = ref<KnowledgeBase[]>([])
const selectedKbId = ref<number | null>(null)
const sessions = ref<ChatSessionVO[]>([])
const currentSessionId = ref('')
const messages = ref<ChatMessageVO[]>([])
const inputMessage = ref('')
const sending = ref(false)
const loading = ref(false)
const streamingContent = ref<string | null>(null)
const messageListRef = ref<InstanceType<typeof MessageList>>()
const showBackToTop = ref(false)
const sidebarCollapsed = ref(false)
const userScrolling = ref(false)
const shouldAutoScroll = ref(true)

// ========== 生命周期 ==========

onMounted(async () => {
  await fetchKbList()

  // 从 URL 参数获取知识库 ID
  const urlKbId = new URLSearchParams(window.location.hash.split('?')[1] || '').get('kbId')
  if (urlKbId) {
    selectedKbId.value = Number(urlKbId)
  }

  fetchSessions()

  // P2-1: 恢复侧边栏状态
  const savedSidebarState = localStorage.getItem('chat-sidebar-collapsed')
  if (savedSidebarState !== null) {
    sidebarCollapsed.value = savedSidebarState === 'true'
  }
})

// ========== 数据获取 ==========

/**
 * 获取知识库列表
 */
const fetchKbList = async () => {
  const res = await kbApi.list()
  kbList.value = res.data
  if (kbList.value.length > 0 && !selectedKbId.value) {
    selectedKbId.value = kbList.value[0].id
  }
}

/**
 * 获取会话列表
 */
const fetchSessions = async () => {
  if (!selectedKbId.value) {
    console.log('[Chat] fetchSessions: selectedKbId 为空，跳过')
    return
  }

  loading.value = true
  try {
    const raw = await chatApi.getSessions()
    const list = Array.isArray(raw) ? raw : (raw?.data ?? [])
    const filtered = list.filter(
        s => String(s.kbId) === String(selectedKbId.value)
    )
    // 强制更新
    sessions.value = []
    await nextTick()
    sessions.value = filtered
  } catch (e) {
    console.error('[Chat] fetchSessions error:', e)
  } finally {
    loading.value = false
  }
}

// ========== 业务逻辑 ==========

/**
 * 知识库切换确认对话框
 */
const onKbChange = () => {
  // 如果当前有消息，询问用户是否确认切换
  if (messages.value.length > 0) {
    ElMessageBox.confirm(
        '切换知识库将清空当前对话，是否继续？',
        '确认切换',
        {
          confirmButtonText: '确定切换',
          cancelButtonText: '取消',
          type: 'warning',
        }
    ).then(() => {
      confirmKbChange()
    }).catch(() => {
      // 用户取消切换，恢复之前的选中状态
      // 这里需要处理恢复，但由于是双向绑定，可能需要额外逻辑
    })
  } else {
    confirmKbChange()
  }
}

/**
 * 确认切换知识库
 */
const confirmKbChange = () => {
  currentSessionId.value = ''
  messages.value = []
  fetchSessions()
}

/**
 * 切换侧边栏折叠状态
 */
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
  // 保存状态到 localStorage
  localStorage.setItem('chat-sidebar-collapsed', String(sidebarCollapsed.value))
}

/**
 * 创建新会话
 */
const createNewSession = () => {
  currentSessionId.value = ''
  messages.value = []
}

/**
 * 切换会话
 */
const switchSession = async (session: ChatSessionVO) => {
  currentSessionId.value = session.sessionId
  const res = await chatApi.getMessages(session.sessionId)
  messages.value = res.data.map((msg: any) => ({
    ...msg,
    refChunks: typeof msg.refChunks === 'string' && msg.refChunks
        ? JSON.parse(msg.refChunks)
        : msg.refChunks
  }))

  shouldAutoScroll.value = true // 切换会话时恢复自动滚动
  messageListRef.value?.scrollToBottom()
}

/**
 * 发送消息
 */
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
  messageListRef.value?.scrollToBottom()

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
    const result = await chatApi.chatStream(data, (chunk) => {
      streamingContent.value += chunk
    })

    // 流结束后，更新为最终消息
    // 更新sessionId（如果是新创建的会话）
    if (result.sessionId && result.sessionId !== currentSessionId.value) {
      currentSessionId.value = result.sessionId
      // 刷新会话列表
      fetchSessions()
    }

    const assistantMsg: ChatMessageVO = {
      id: Date.now() + 1,
      sessionId: result.sessionId || currentSessionId.value,
      role: 2,
      content: result.content || streamingContent.value || '',
      refChunks: result.refChunks ? JSON.parse(JSON.stringify(result.refChunks)) : undefined,
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
    messageListRef.value?.scrollToBottom()
  }
}

/**
 * 处理消息滚动事件
 */
const handleMessageScroll = (isAtBottom: boolean) => {
  if (!isAtBottom) {
    userScrolling.value = true
    shouldAutoScroll.value = false
  } else {
    userScrolling.value = false
    shouldAutoScroll.value = true
  }

  // 控制返回顶部按钮显示
  showBackToTop.value = isAtBottom === false
}

/**
 * 滚动到顶部
 */
const scrollToTop = () => {
  messageListRef.value?.scrollToBottom()
}

</script>

<style scoped>
.chat-page {
  height: 100%;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  padding: 16px;
}

.chat-container {
  height: calc(100vh - 140px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0; /* 确保在 flex 容器中正确收缩 */
}

.chat-input-area {
  padding: 20px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
}

/* 返回顶部按钮 */
.back-to-top {
  position: fixed;
  bottom: 100px;
  right: 40px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  border: none;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  z-index: 100;
}

.back-to-top:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
}

.back-to-top:active {
  transform: translateY(0);
}

.back-to-top .el-icon {
  font-size: 20px;
  font-weight: bold;
}

/* 侧边栏内组件样式 */
.sidebar-header-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-toggle {
  flex-shrink: 0;
}

.sidebar-header-top .el-select {
  --el-select-border-color-hover: #409eff;
}

.sidebar-header-top .el-select .el-input__wrapper {
  border-radius: 8px;
  padding: 4px 12px;
  box-shadow: 0 0 0 1px #e4e7ed inset;
  transition: all 0.3s;
}

.sidebar-header-top .el-select .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #409eff inset;
}

.sidebar-header-top .el-select .el-input__inner {
  font-size: 13px;
  color: #303133;
}

.sidebar-header-top .el-button--small {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s;
  margin-top: 8px;
}

.sidebar-header-top .el-button--small:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.sidebar-header-top .el-button--small:active {
  transform: translateY(0);
}

.session-list-header-title {
  padding: 12px 16px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.session-list .el-empty {
  padding: 40px 20px;
}

.session-list .el-empty__description {
  color: #909399;
  font-size: 13px;
  margin-top: 12px;
}
</style>
