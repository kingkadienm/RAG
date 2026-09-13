<template>
  <div class="session-item" :class="{ active: isActive }" @click="onClick">
    <div class="session-title" :title="session.title">
      {{ session.title || '新对话' }}
    </div>
    <div class="session-time">{{ formatTime(session.updatedTime) }}</div>
  </div>
</template>

<script setup lang="ts">
import type {ChatSessionVO} from '@/types'

interface Props {
  session: ChatSessionVO
  isActive?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  click: [session: ChatSessionVO]
}>()

/**
 * 格式化时间
 */
const formatTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const dayDiff = Math.floor(diff / (1000 * 60 * 60 * 24))

  // 今天
  if (dayDiff === 0) {
    return date.toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit'})
  }
  // 昨天
  if (dayDiff === 1) {
    return '昨天 ' + date.toLocaleTimeString('zh-CN', {hour: '2-digit', minute: '2-digit'})
  }
  // 一周内
  if (dayDiff < 7) {
    const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return weekdays[date.getDay()]
  }
  // 更早
  return date.toLocaleDateString('zh-CN', {month: 'numeric', day: 'numeric'})
}

const onClick = () => {
  emit('click', props.session)
}
</script>

<style scoped>
.session-item {
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 6px;
  margin-bottom: 2px;
  transition: all 0.2s;
  border: 1px solid transparent;
  background: transparent;
}

.session-item:hover {
  background: #f0f2f5;
  border-color: #e4e7ed;
}

.session-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.session-title {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}

.session-time {
  font-size: 11px;
  color: #909399;
}
</style>
