<template>
  <div class="message-item" :class="['message-row', message.role === 1 ? 'user' : 'assistant']">
    <div class="message-avatar">
      <el-avatar :size="36" :icon="message.role === 1 ? UserFilled : BellFilled"
                 :type="message.role === 1 ? 'primary' : 'success'"/>
    </div>
    <div class="message-body">
      <div class="message-role">{{ message.role === 1 ? '我' : 'AI 助手' }}</div>
      <!-- 用户消息 -->
      <div v-if="message.role === 1" class="message-content">{{ message.content }}</div>
      <!-- AI 消息（Markdown） -->
      <div v-else class="message-content markdown-body" v-html="renderedContent"></div>

      <!-- 参考文档片段 -->
      <div v-if="message.refChunks && message.role === 2" class="message-refs">
        <div class="refs-title">参考文档片段：</div>
        <div
            v-for="(chunk, index) in parsedRefChunks"
            :key="`${chunk.docId}-${index}`"
            class="ref-chunk"
        >
          <div class="ref-chunk-header">
            <span class="ref-index">#{{ index + 1 }}</span>
            <span class="ref-file">{{ chunk.fileName }}</span>
            <span class="ref-score">{{ (chunk.score * 100).toFixed(1) }}%</span>
          </div>
          <div
              class="ref-chunk-content markdown-body"
              v-html="renderMarkdown(chunk.content)"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {UserFilled, BellFilled} from '@element-plus/icons-vue'
import {marked} from 'marked'
import DOMPurify from 'dompurify'
import type {ChatMessageVO} from '@/types'

interface Props {
  message: ChatMessageVO
}

const props = defineProps<Props>()

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
 * AI 消息的渲染内容
 */
const renderedContent = computed(() => {
  if (props.message.role !== 2) return ''
  return renderMarkdown(props.message.content)
})

/**
 * 解析参考文档片段
 */
const parsedRefChunks = computed(() => {
  if (!props.message.refChunks) return []
  if (typeof props.message.refChunks === 'string') {
    try {
      return JSON.parse(props.message.refChunks)
    } catch {
      return []
    }
  }
  return props.message.refChunks
})
</script>

<style scoped>
.message-item {
  display: flex;
  margin-bottom: 16px;
  gap: 10px;
  animation: messageSlideIn 0.3s ease-out;
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
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

.message-refs {
  margin-top: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e8f4fd 100%);
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  border: 1px solid #d4edfc;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.refs-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #409eff;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.ref-chunk {
  margin-bottom: 10px;
  padding: 8px;
  background: #fff;
  border-radius: 6px;
  border-left: 3px solid #409eff;
}

.ref-chunk:last-child {
  margin-bottom: 0;
}

.ref-chunk-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
}

.ref-index {
  background: #409eff;
  color: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
  font-size: 11px;
}

.ref-file {
  flex: 1;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ref-score {
  color: #67c23a;
  font-weight: 600;
  font-size: 11px;
  background: #f0f9ff;
  padding: 2px 6px;
  border-radius: 4px;
}

/* 参考片段中的 Markdown */
.ref-chunk-content.markdown-body {
  font-size: 13px;
  background: transparent;
  padding: 0;
  color: inherit;
}

.ref-chunk-content.markdown-body p {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.5;
}

.ref-chunk-content.markdown-body p:first-child {
  margin-top: 0;
}

.ref-chunk-content.markdown-body p:last-child {
  margin-bottom: 0;
}

.ref-chunk-content.markdown-body pre {
  padding: 8px;
  margin: 6px 0;
}


</style>
