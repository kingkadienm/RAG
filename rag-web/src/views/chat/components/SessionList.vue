<template>
  <div class="session-list-wrapper">
    <div class="session-list-header">
      <slot name="header"></slot>
    </div>
    <div class="session-list" v-if="!loading">
      <SessionItem
          v-for="session in sessions"
          :key="session.sessionId"
          :session="session"
          :is-active="currentSessionId === session.sessionId"
          @click="handleSessionClick"
      />
      <el-empty
          v-if="sessions.length === 0"
          description="暂无对话"
          :image-size="60"
      />
    </div>
    <div v-if="loading" class="session-list-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {Loading} from '@element-plus/icons-vue'
import SessionItem from './SessionItem.vue'
import type {ChatSessionVO} from '@/types'

interface Props {
  sessions: ChatSessionVO[]
  currentSessionId?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  currentSessionId: '',
  loading: false,
})

const emit = defineEmits<{
  'session-select': [session: ChatSessionVO]
}>()

// 调试信息
console.log('[SessionList] 接收到的 props:', {
  sessionsCount: props.sessions.length,
  currentSessionId: props.currentSessionId,
  loading: props.loading,
  sessions: props.sessions
})

/**
 * 处理会话点击
 */
const handleSessionClick = (session: ChatSessionVO) => {
  console.log('[SessionList] 会话被点击:', session)
  emit('session-select', session)
}
</script>

<style scoped>
.session-list-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.session-list-header {
  flex-shrink: 0;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px;
}

.session-list-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}

.session-list-loading .is-loading {
  animation: rotating 1.5s linear infinite;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
