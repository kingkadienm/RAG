<template>
  <div class="dashboard-page">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #ecf5ff; color: #409eff">
              <el-icon :size="32"><Files /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.kbCount }}</div>
              <div class="stat-label">知识库数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #f0f9ff; color: #67c23a">
              <el-icon :size="32"><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.docCount }}</div>
              <div class="stat-label">文档总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #fdf6ec; color: #e6a23c">
              <el-icon :size="32"><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.chatCount }}</div>
              <div class="stat-label">对话轮次</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #fef0f0; color: #f56c6c">
              <el-icon :size="32"><Check /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.vectorizedCount }}</div>
              <div class="stat-label">已向量化</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card header="最近上传的文档" class="page-card">
          <el-table :data="recentDocs" style="width: 100%" size="small" max-height="350">
            <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getParseType(row.parseStatus)" size="small">
                  {{ getParseLabel(row.parseStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="上传时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="知识库列表" class="page-card">
          <el-table :data="kbList" style="width: 100%" size="small" max-height="350">
            <el-table-column prop="name" label="知识库名称" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { kbApi } from '@/api/knowledgeBase'
import { documentApi } from '@/api/document'
import type { KnowledgeBase, Document } from '@/types'
import { PARSE_STATUS_MAP } from '@/types'

const kbList = ref<KnowledgeBase[]>([])
const recentDocs = ref<Document[]>([])
const stats = ref({
  kbCount: 0,
  docCount: 0,
  chatCount: 0,
  vectorizedCount: 0,
})

const getParseLabel = (status: number) => PARSE_STATUS_MAP[status]?.label || '未知'
const getParseType = (status: number) => PARSE_STATUS_MAP[status]?.type || 'info'

onMounted(async () => {
  try {
    kbList.value = await kbApi.list()
    stats.value.kbCount = kbList.value.filter((kb) => kb.deleted === 0).length

    // 获取最近文档
    const docsResult = await documentApi.list({ pageNum: 1, pageSize: 5 })
    recentDocs.value = docsResult.list || []
    stats.value.docCount = docsResult.total || 0
    stats.value.vectorizedCount = (docsResult.list || []).filter(
      (d) => d.vectorStatus === 2
    ).length
  } catch (e) {
    // 首次加载可能无数据
  }
})
</script>

<style scoped>
.stat-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
</style>
