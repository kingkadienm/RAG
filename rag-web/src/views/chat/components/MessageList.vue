<template>
  <div
      ref="messagesContainer"
      class="messages-container"
      @scroll="handleScroll"
  >
    <!-- 空状态 -->
    <div v-if="showWelcome" class="welcome-state">
      <el-icon :size="64" color="#909399">
        <ChatDotRound />
      </el-icon>
      <p class="welcome-text">{{ welcomeText }}</p>
      <p v-if="selectedKbId" class="welcome-hint">开始你的智能对话吧</p>
    </div>

    <!-- 消息列表 -->
    <template v-else>
      <MessageItem
          v-for="message in messages"
          :key="message.id"
          :message="message"
      />

      <!-- 流式输出中的助手消息 -->
      <div
          v-if="streamingContent !== null"
          class="message-item assistant"
          role="log"
          aria-live="polite"
          aria-atomic="true"
          aria-relevant="additions"
      >
        <div class="message-avatar">
          <el-avatar :size="36" :icon="BellFilled" type="success"/>
        </div>
        <div class="message-body">
          <div class="message-role">AI 助手</div>
          <div class="message-content markdown-body" v-html="renderedStreamingContent" aria-live="assertive"></div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading && streamingContent === null" class="message-item assistant">
        <div class="message-avatar">
          <el-avatar :size="36" :icon="BellFilled" type="success"/>
        </div>
        <div class="message-body">
          <div class="message-role">AI 助手</div>
          <div class="message-content">
            <span class="loading-dots">
              <span></span>
              <span></span>
              <span></span>
            </span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch, nextTick} from 'vue'
import {ChatDotRound, BellFilled} from '@element-plus/icons-vue'
import {marked} from 'marked'
import DOMPurify from 'dompurify'
import MessageItem from './MessageItem.vue'
import type {ChatMessageVO} from '@/types'

interface Props {
  messages: ChatMessageVO[]
  streamingContent?: string | null
  loading?: boolean
  selectedKbId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  streamingContent: null,
  loading: false,
  selectedKbId: null,
})

const emit = defineEmits<{
  scroll: [isAtBottom: boolean]
}>()

const messagesContainer = ref<HTMLElement>()

/**
 * 是否显示欢迎状态
 */
const showWelcome = computed(() => {
  return props.messages.length === 0 && !props.loading && props.streamingContent === null
})

/**
 * 欢迎文本
 */
const welcomeText = computed(() => {
  if (!props.selectedKbId) return '请选择一个知识库开始对话'
  return '选择一个知识库，开始智能对话'
})

/**
 * 预处理文本：合并多余的空行
 */
const normalizeText = (content: string): string => {
  return (content || '')
      .replace(/\\n/g, '\n')      // 字面 "\n" → 真实换行
      .replace(/\r\n/g, '\n')     // 统一换行符
      .replace(/\n{3,}/g, '\n\n') // 合并多余空行
}

/**
 * 渲染 Markdown 内容
 */
const renderMarkdown = (content: string) => {
  if (!content) return ''
  try {
    const normalizedContent = normalizeText(content)
    const html = marked.parse(normalizedContent) as string
    return DOMPurify.sanitize(html)
  } catch (e) {
    console.error('[Markdown] 渲染失败:', e)
    return content
  }
}

/**
 * 流式内容的渲染版本
 */
const renderedStreamingContent = computed(() => {
  if (!props.streamingContent) return ''
  return renderMarkdown(props.streamingContent)
})

/**
 * 滚动到消息列表底部
 */
const scrollToBottom = (smooth = false) => {
  if (!messagesContainer.value) return

  nextTick(() => {
    if (messagesContainer.value) {
      if (smooth) {
        messagesContainer.value.scrollTo({
          top: messagesContainer.value.scrollHeight,
          behavior: 'smooth'
        })
      } else {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    }
  })
}

/**
 * 处理滚动事件
 */
const handleScroll = () => {
  if (!messagesContainer.value) return

  const {scrollTop, scrollHeight, clientHeight} = messagesContainer.value
  const isAtBottom = scrollHeight - scrollTop - clientHeight < 50

  emit('scroll', isAtBottom)
}

// 监听消息变化，自动滚动到底部
watch(
    () => props.messages.length,
    () => {
      scrollToBottom()
    }
)

// 监听流式内容变化
watch(
    () => props.streamingContent,
    () => {
      scrollToBottom(true)
    }
)

// 暴露滚动方法
defineExpose({
  scrollToBottom
})
</script>

<style scoped>
.messages-container {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: #fafbfc;
  min-height: 0; /* 确保在 flex 容器中正确滚动 */
}

.welcome-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  gap: 16px;
  padding: 20px;
}

.welcome-text {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 1.6;
}

.welcome-hint {
  margin: 0;
  text-align: center;
  font-size: 14px;
  color: #67c23a;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  gap: 10px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-body {
  min-width: fit-content;
  max-width: 600px;
  flex-shrink: 0;
}

.message-role {
  font-size: 11px;
  color: #909399;
  margin-bottom: 6px;
  text-align: right;
  padding: 0 4px;
  font-weight: 500;
  letter-spacing: 0.3px;
  width: fit-content;
}

.message-item.user .message-role {
  text-align: left;
}

.message-content {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
  word-wrap: break-word;
  overflow-wrap: break-word;
  min-width: fit-content;
}

.message-item.assistant .message-content {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-top-left-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.message-item.user .message-content {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

/* 加载动画 */
.loading-dots {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  animation: dotPulse 1.4s ease-in-out infinite;
}

.loading-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
