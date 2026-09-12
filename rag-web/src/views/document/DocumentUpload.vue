<template>
  <div>
    <el-page-header @back="goBack" content="上传文档" style="margin-bottom: 16px" />

    <el-card>
      <el-alert
        title="支持格式：PDF、DOCX、TXT、MD、XLSX、PPTX | 最大文件大小：50MB"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
      />

      <el-upload
        drag
        :auto-upload="false"
        :limit="10"
        multiple
        accept=".pdf,.docx,.txt,.md,.xlsx,.pptx"
        :on-change="handleFileChange"
        :file-list="fileList"
      >
        <el-icon class="el-icon--upload" :size="60"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            文件将上传到知识库并自动进行解析和向量化
          </div>
        </template>
      </el-upload>

      <div style="margin-top: 24px">
        <div style="margin-bottom: 12px; font-weight: 500">知识库信息</div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="知识库名称">
            {{ kbInfo.name }}
          </el-descriptions-item>
          <el-descriptions-item label="描述">
            {{ kbInfo.description || '无' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div style="margin-top: 24px; text-align: right">
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          开始上传
        </el-button>
      </div>
    </el-card>

    <!-- 上传进度对话框 -->
    <el-dialog v-model="progressDialogVisible" title="上传进度" width="500px" :close-on-click-modal="false">
      <div v-for="(item, idx) in uploadResults" :key="idx" style="margin-bottom: 12px">
        <div style="display: flex; justify-content: space-between; margin-bottom: 4px">
          <span>{{ item.fileName }}</span>
          <el-tag :type="item.success ? 'success' : 'danger'" size="small">
            {{ item.success ? '成功' : '失败' }}
          </el-tag>
        </div>
        <el-text v-if="item.message" size="small" type="info">{{ item.message }}</el-text>
      </div>
      <template #footer>
        <el-button type="primary" @click="goToList">查看文档列表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { documentApi } from '@/api/document'
import { kbApi } from '@/api/knowledgeBase'
import type { UploadFile } from 'element-plus'
import type { KnowledgeBase } from '@/types'

const route = useRoute()
const router = useRouter()

const kbId = ref<number>(Number(route.params.kbId))
const kbInfo = ref<KnowledgeBase>({ id: 0, name: '', description: '', creatorId: 0, status: 1, createdTime: '', updatedTime: '', deleted: 0 })
const fileList = ref<UploadFile[]>([])
const uploading = ref(false)
const progressDialogVisible = ref(false)
const uploadResults = ref<Array<{ fileName: string; success: boolean; message: string }>>([])

onMounted(async () => {
  const kbs = (await kbApi.list()).data
  const kb = kbs.find((k) => k.id === kbId.value)
  if (kb) kbInfo.value = kb
})

const handleFileChange = (_file: UploadFile, files: UploadFile[]) => {
  fileList.value = files
}

const handleUpload = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  uploadResults.value = []

  for (const file of fileList.value) {
    try {
      const res = await documentApi.upload(file.raw!, kbId.value)
      uploadResults.value.push({
        fileName: file.name,
        success: true,
        message: `文档 ID: ${res.docId}`,
      })
    } catch (e: any) {
      uploadResults.value.push({
        fileName: file.name,
        success: false,
        message: e?.message || '上传失败',
      })
    }
  }

  uploading.value = false
  progressDialogVisible.value = true
  fileList.value = []
}

const goBack = () => {
  router.push(`/knowledge-base/${kbId.value}/documents`)
}

const goToList = () => {
  progressDialogVisible.value = false
  router.push(`/knowledge-base/${kbId.value}/documents`)
}
</script>

<style scoped>
.el-upload {
  width: 100%;
}

.el-upload-dragger {
  width: 100% !important;
}
</style>
