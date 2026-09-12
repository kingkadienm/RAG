<template>
  <div>
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span>知识库列表</span>
          <el-button type="primary" :icon="Plus" @click="showCreateDialog">
            创建知识库
          </el-button>
        </div>
      </template>

      <el-table
        :data="kbList"
        style="width: 100%"
        v-loading="loading"
        :row-class-name="'table-row'"
        @keydown.enter="handleRowAction"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip min-width="250" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :icon="Document"
              @click="goToDocuments(row)"
              aria-label="查看知识库文档"
            >
              文档
            </el-button>
            <el-button
              type="success"
              size="small"
              :icon="ChatDotRound"
              @click="goToChat(row)"
              aria-label="在知识库中开始对话"
            >
              对话
            </el-button>
            <el-button
              type="danger"
              size="small"
              :icon="Delete"
              @click="handleDelete(row)"
              :aria-label="`删除知识库 ${row.name}`"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建知识库对话框 -->
    <el-dialog v-model="dialogVisible" title="创建知识库" width="500px">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import { Plus, Document, Delete, ChatDotRound } from '@element-plus/icons-vue'
import { useKbStore } from '@/stores/knowledgeBase'
import { kbApi } from '@/api/knowledgeBase'
import type { KnowledgeBaseCreateDTO } from '@/types'

const router = useRouter()
const kbStore = useKbStore()

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const kbList = ref<any[]>([])

const form = reactive<KnowledgeBaseCreateDTO>({
  name: '',
  description: '',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' },
  ],
}

const formatDate = (date: string) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

/**
 * 处理表格行的键盘操作
 * Enter 键：查看文档
 * Delete 键：删除知识库
 */
const handleRowAction = (event: KeyboardEvent, row: any) => {
  // 键盘快捷键提示
  if (event.key === 'Enter') {
    event.preventDefault()
    goToDocuments(row)
  } else if (event.key === 'Delete' || event.key === 'Backspace') {
    event.preventDefault()
    handleDelete(row)
  }
}

const showCreateDialog = () => {
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const res = await kbApi.create(form)
    kbStore.knowledgeBases.push(res.data)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    // handled
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除知识库「${row.name}」吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await kbApi.delete(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

const goToDocuments = (row: any) => {
  router.push(`/knowledge-base/${row.id}/documents`)
}

const goToChat = (row: any) => {
  router.push(`/chat?kbId=${row.id}`)
}

const fetchList = async () => {
  loading.value = true
  try {
    kbList.value = (await kbApi.list()).data
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
