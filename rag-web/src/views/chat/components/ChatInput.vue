<template>
  <div class="chat-input-wrapper">
    <!-- 知识库选择器 -->
    <div v-if="!selectedKbId" class="kb-selector">
      <el-select
          v-model="internalKbId"
          placeholder="请先选择知识库"
          size="default"
          @change="handleKbChange"
      >
        <el-option
            v-for="kb in kbList"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
        />
      </el-select>
    </div>

    <!-- 输入框和发送按钮 -->
    <div v-show="selectedKbId" class="input-container">
      <el-input
          :model-value="modelValue"
          type="textarea"
          :rows="2"
          placeholder="请输入你的问题..."
          :disabled="disabled || sending"
          @keydown.enter.ctrl="handleSend"
          @update:modelValue="handleInputChange"
          aria-label="消息输入框"
          aria-describedby="input-help"
      />
      <span id="input-help" class="visually-hidden">
        按 Ctrl+Enter 发送消息
      </span>
      <el-button
          :icon="Promotion"
          :loading="sending"
          :disabled="!canSend"
          :title="sendButtonTitle"
          :aria-disabled="!selectedKbId"
          @click="handleSend"
          type="primary"
          aria-label="发送消息"
      >
        发送
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {Promotion} from '@element-plus/icons-vue'
import type {KnowledgeBase} from '@/types'

interface Props {
  modelValue: string
  selectedKbId?: number | null
  kbList: KnowledgeBase[]
  sending?: boolean
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  sending: false,
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  'kb-change': [kbId: number]
}>()

const internalKbId = ref<number | null>(props.selectedKbId || null)

/**
 * 监听外部 selectedKbId 变化
 */
watch(() => props.selectedKbId, (newVal) => {
  internalKbId.value = newVal || null
})

/**
 * 是否可以发送
 */
const canSend = computed(() => {
  return props.modelValue.trim() && props.selectedKbId && !props.sending
})

/**
 * 发送按钮标题
 */
const sendButtonTitle = computed(() => {
  if (!props.selectedKbId) return '请先选择知识库再发送消息'
  if (!props.modelValue.trim()) return '请输入消息内容'
  return '发送消息 (Ctrl+Enter)'
})

/**
 * 处理输入变化
 */
const handleInputChange = (value: string) => {
  emit('update:modelValue', value)
}

/**
 * 处理发送
 */
const handleSend = () => {
  if (!canSend.value) return
  emit('send')
}

/**
 * 处理知识库切换
 */
const handleKbChange = () => {
  if (internalKbId.value) {
    emit('kb-change', internalKbId.value)
    emit('update:modelValue', '')
  }
}
</script>

<style scoped>
.chat-input-wrapper {
  width: 100%;
}

.kb-selector {
  margin-bottom: 16px;
}

.kb-selector :deep(.el-select) {
  width: 100%;
}

.input-container {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-container .el-textarea {
  flex: 1;
  border-radius: 12px;
}

.input-container .el-textarea__inner {
  border: 2px solid #e4e7ed;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  transition: all 0.3s;
  resize: none;
  min-height: 80px !important;
}

.input-container .el-textarea__inner:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.input-container .el-textarea__inner:disabled {
  background: #f5f7fa;
  cursor: not-allowed;
}

.input-container .el-button--primary {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border: none;
  border-radius: 12px;
  padding: 12px 24px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
  transition: all 0.3s;
  height: 80px;
}

.input-container .el-button--primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

.input-container .el-button--primary:active:not(:disabled) {
  transform: translateY(0);
}

.input-container .el-button--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 屏幕阅读器专用 */
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
