<template>
  <div>
    <el-page-header @back="goBack" content="文档管理" style="margin-bottom: 16px" />

    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>文档列表</span>
          <div>
            <el-button type="success" :icon="Upload" @click="goUpload">
              上传文档
            </el-button>
            <el-button type="primary" :icon="Refresh" @click="fetchList" :loading="loading">
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 骨架屏加载状态 -->
      <div v-if="loading && docList.length === 0" class="skeleton-doc-list">
        <div v-for="i in 10" :key="i" class="skeleton-doc-item">
          <div class="skeleton-doc-icon skeleton"></div>
          <div class="skeleton-doc-info">
            <div class="skeleton-doc-name skeleton"></div>
            <div class="skeleton-doc-meta skeleton"></div>
          </div>
          <div class="skeleton-doc-actions">
            <div class="skeleton skeleton" style="width: 60px; height: 24px; margin-right: 8px"></div>
            <div class="skeleton skeleton" style="width: 60px; height: 24px; margin-right: 8px"></div>
            <div class="skeleton skeleton" style="width: 60px; height: 24px"></div>
          </div>
        </div>
      </div>

      <!-- 实际表格内容 -->
      <el-table
        v-else
        :data="docList"
        style="width: 100%"
        v-loading="loading"
        :row-class-name="'table-row'"
        @keydown.enter="handleRowAction"
      >
        <el-table-column label="文件" min-width="300">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 12px">
              <el-icon :class="['file-icon', `file-icon-${row.fileType}`]">
                <Document />
              </el-icon>
              <div>
                <div style="font-weight: 500">{{ row.fileName }}</div>
                <el-text size="small" type="info">
                  {{ formatSize(row.fileSize) }}
                </el-text>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="解析状态" width="130">
          <template #default="{ row }">
            <el-tag :type="parseType(row.parseStatus)" size="small" :aria-label="`文档解析状态：${parseLabel(row.parseStatus)}`">
              <span class="status-dot" :class="parseDot(row.parseStatus)" aria-hidden="true" />
              <el-icon v-if="row.parseStatus === 2" class="status-icon" aria-hidden="true">
                <CircleCheck />
              </el-icon>
              <el-icon v-else-if="row.parseStatus === 3" class="status-icon" aria-hidden="true">
                <CircleClose />
              </el-icon>
              <span class="status-text">{{ parseLabel(row.parseStatus) }}</span>
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="向量化" width="130">
          <template #default="{ row }">
            <el-tag :type="vectorType(row.vectorStatus)" size="small" :aria-label="`向量化状态：${vectorLabel(row.vectorStatus)}`">
              <span class="status-dot" :class="vectorDot(row.vectorStatus)" aria-hidden="true" />
              <el-icon v-if="row.vectorStatus === 2" class="status-icon" aria-hidden="true">
                <CircleCheck />
              </el-icon>
              <el-icon v-else-if="row.vectorStatus === 3" class="status-icon" aria-hidden="true">
                <CircleClose />
              </el-icon>
              <span class="status-text">{{ vectorLabel(row.vectorStatus) }}</span>
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="分块/向量" width="120">
          <template #default="{ row }">
            {{ row.chunkCount }}/{{ row.vectorCount }}
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.chunkCount > 0"
              type="primary"
              size="small"
              link
              @click="showChunks(row)"
            >
              查看分块
            </el-button>
            <el-button
              type="warning"
              size="small"
              link
              @click="handleRetry(row)"
            >
              重试
            </el-button>
            <el-button
              type="danger"
              size="small"
              link
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, prev, pager, next"
          @current-change="fetchList"
        />
      </div>
    </el-card>

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

    <!-- 分块预览对话框 -->
    <el-dialog v-model="chunkDialogVisible" title="分块预览" width="800px">
      <div v-if="chunkPreview" class="chunk-preview">{{ chunkPreview }}</div>
      <div v-else>暂无分块数据</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import {ref, reactive, onMounted, onUnmounted, onBeforeUnmount} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Upload, Refresh, CircleCheck, CircleClose, ArrowUp } from '@element-plus/icons-vue'
import { documentApi } from '@/api/document'
import { useAuthStore } from '@/stores/auth'
import type { Document as DocType } from '@/types'
import { PARSE_STATUS_MAP, VECTOR_STATUS_MAP } from '@/types'

const authStore = useAuthStore()

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const docList = ref<DocType[]>([])
const kbId = ref<number>(Number(route.params.kbId))
const chunkDialogVisible = ref(false)
const chunkPreview = ref('')
const showBackToTop = ref(false)

// SSE 连接映射：docId -> EventSource
const sseMap = new Map<number, EventSource>()

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
})

const parseLabel = (s: number) => PARSE_STATUS_MAP[s]?.label || '未知'
const parseType = (s: number) => PARSE_STATUS_MAP[s]?.type || 'info'
const parseDot = (s: number) => `status-dot ${['pending','parsing','parsed','failed'][s - 1] || 'pending'}`

const vectorLabel = (v: number) => VECTOR_STATUS_MAP[v]?.label || '未知'
const vectorType = (v: number) => VECTOR_STATUS_MAP[v]?.type || 'info'
const vectorDot = (v: number) => `status-dot ${['pending','vectorizing','vectorized','failed'][v - 1] || 'pending'}`

const formatSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const formatDate = (d: string) => {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN')
}

/**
 * 处理表格行的键盘操作
 * Enter 键：查看分块（如果有分块）
 * Delete 键：删除文档
 */
const handleRowAction = (event: KeyboardEvent, row: DocType) => {
  if (event.key === 'Enter') {
    event.preventDefault()
    if (row.chunkCount > 0) {
      showChunks(row)
    } else {
      ElMessage.info('该文档暂无分块数据')
    }
  } else if (event.key === 'Delete' || event.key === 'Backspace') {
    event.preventDefault()
    handleDelete(row)
  }
}

const fetchList = async () => {
  // 先清理旧的 SSE 连接
  sseMap.forEach(es => es.close())
  sseMap.clear()

  loading.value = true
  try {
    const res = await documentApi.list(kbId.value, {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    })
    docList.value = res.data.records || []
    pagination.total = res.data.total || 0

    // 为正在解析/向量化的文档订阅 SSE 进度推送
    docList.value.forEach(doc => {
      if (doc.parseStatus === 1 || doc.parseStatus === 2 && (doc.vectorStatus === 1 || doc.vectorStatus === 0)) {
        subscribeParseStatus(doc.id)
      }
    })
  } finally {
    loading.value = false
  }
}

/**
 * 订阅文档解析进度 SSE 推送
 */
const subscribeParseStatus = (docId: number) => {
  // 避免重复订阅
  if (sseMap.has(docId)) return

  const token = authStore.token
  const url = `/api/doc/${docId}/parse-status/stream`
  const eventSource = new EventSource(url, {
    headers: { Authorization: `Bearer ${token}` },
  } as any)

  eventSource.addEventListener('status', (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data)
      const idx = docList.value.findIndex(d => d.id === docId)
      if (idx !== -1) {
        // 更新文档状态
        docList.value[idx] = {
          ...docList.value[idx],
          parseStatus: data.parseStatus,
          vectorStatus: data.vectorStatus,
          chunkCount: data.chunkCount,
          vectorCount: data.vectorCount,
          errorMsg: data.errorMsg,
        }
        // 解析完成后关闭 SSE
        if (data.stage === 'completed' || data.stage === 'parse_failed' || data.stage === 'vector_failed') {
          eventSource.close()
          sseMap.delete(docId)
        }
      }
    } catch (e) {
      console.error('[SSE] 解析状态更新失败:', e)
    }
  })

  eventSource.onerror = () => {
    console.warn(`[SSE] 文档 ${docId} 连接断开`)
    eventSource.close()
    sseMap.delete(docId)
  }

  sseMap.set(docId, eventSource)
}

const goUpload = () => {
  router.push(`/knowledge-base/${kbId.value}/upload`)
}

const goBack = () => {
  router.push('/knowledge-base')
}

const showChunks = async (row: DocType) => {
  chunkDialogVisible.value = true
  chunkPreview.value = '正在加载分块数据...'
  try {
    const res = await documentApi.getChunks(row.id)
    const chunks = res.data
    if (chunks && chunks.length > 0) {
      chunkPreview.value = chunks
        .map((c: any) => `【分块 ${c.index + 1}】长度: ${c.length}\n${c.content}`)
        .join('\n\n' + '─'.repeat(40) + '\n\n')
    } else {
      chunkPreview.value = '该文档暂无分块数据'
    }
  } catch (e: any) {
    chunkPreview.value = '加载分块失败: ' + (e?.message || '未知错误')
  }
}

const handleDelete = async (row: DocType) => {
  await ElMessageBox.confirm(`确定删除文档「${row.fileName}」吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await documentApi.delete(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

const handleRetry = async (row: DocType) => {
  try {
    await documentApi.retry(row.id)
    ElMessage.success('已重新发起解析')
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '重试失败')
  }
}

/**
 * 处理滚动事件，控制返回顶部按钮显示
 */
const handleScroll = (event: Event) => {
  const target = event.target as HTMLElement
  if (target) {
    const scrollTop = target.scrollTop
    showBackToTop.value = scrollTop > 300
  }
}

/**
 * 返回顶部
 */
const scrollToTop = () => {
  const layoutMain = document.querySelector('.layout-main')
  if (layoutMain) {
    layoutMain.scrollTo({
      top: 0,
      behavior: 'smooth'
    })
  }
}

onMounted(() => {
  fetchList()

  // 监听主内容区域滚动
  const layoutMain = document.querySelector('.layout-main')
  layoutMain?.addEventListener('scroll', handleScroll)
})

onBeforeUnmount(() => {
  // 清理滚动事件监听
  const layoutMain = document.querySelector('.layout-main')
  layoutMain?.removeEventListener('scroll', handleScroll)
})

// 组件卸载时清理 SSE 连接
onUnmounted(() => {
  sseMap.forEach(es => es.close())
  sseMap.clear()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-icon {
  font-size: 32px;
}

.file-icon-pdf { color: #f56c6c; }
.file-icon-docx { color: #409eff; }
.file-icon-txt { color: #67c23a; }
.file-icon-md { color: #e6a23c; }
.file-icon-xlsx { color: #67c23a; }
.file-icon-pptx { color: #e6a23c; }

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

/* 状态图标样式 */
.status-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
  vertical-align: middle;
}

.status-text {
  vertical-align: middle;
}

/* 骨架屏样式 */
.skeleton-doc-list {
  padding: 16px 0;
}

.skeleton-doc-item {
  display: flex;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.skeleton-doc-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  margin-right: 16px;
}

.skeleton-doc-info {
  flex: 1;
}

.skeleton-doc-name {
  height: 16px;
  width: 60%;
  margin-bottom: 8px;
}

.skeleton-doc-meta {
  height: 14px;
  width: 40%;
}

.skeleton-doc-actions {
  display: flex;
  align-items: center;
}
</style>
