<template>
  <div class="chat-sidebar" :class="{ 'sidebar-collapsed': collapsed }">
    <div class="chat-sidebar-header">
      <slot name="header"></slot>
    </div>
    <div class="sidebar-body">
      <slot name="body"></slot>
    </div>

    <!-- 侧边栏折叠提示 -->
    <div v-if="collapsed" class="sidebar-collapsed-hint" @click="toggleCollapse">
      <el-icon>
        <DArrowRight />
      </el-icon>
      <span>展开侧边栏</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {DArrowRight} from '@element-plus/icons-vue'

interface Props {
  collapsed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  collapsed: false,
})

const emit = defineEmits<{
  'toggle-collapse': []
}>()

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const {collapsed} = props

const toggleCollapse = () => {
  emit('toggle-collapse')
}
</script>

<style scoped>
.chat-sidebar {
  width: 300px;
  min-width: 300px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
  position: relative;
  transition: all 0.3s ease;
}

.chat-sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
}

.sidebar-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 折叠状态 */
.sidebar-collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border-right: none;
}

.sidebar-collapsed-hint {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  color: #fff;
  padding: 16px 10px;
  border-radius: 0 12px 12px 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  transition: all 0.3s;
  z-index: 10;
}

.sidebar-collapsed-hint:hover {
  background: linear-gradient(135deg, #337ecc 0%, #2868b8 100%);
  padding-left: 16px;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
}

.sidebar-collapsed-hint .el-icon {
  font-size: 20px;
}
</style>
