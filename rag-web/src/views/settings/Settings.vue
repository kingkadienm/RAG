<template>
  <div>
    <el-page-header @back="goBack" content="系统配置" style="margin-bottom: 16px" />

    <el-card>
      <template #header>
        <div class="card-header">
          <span>系统配置管理</span>
          <div>
            <el-button @click="handleRefreshCache" :loading="refreshing">
              <el-icon><Refresh /></el-icon>
              刷新缓存
            </el-button>
            <el-button type="primary" @click="showAddDialog">
              <el-icon><Plus /></el-icon>
              新增配置
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="configList" v-loading="loading" style="width: 100%">
        <el-table-column label="配置键" min-width="220" prop="configKey">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.configKey }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="配置值" min-width="300" prop="configValue">
          <template #default="{ row }">
            <el-input
              v-if="row.isSystem === 1"
              :model-value="row.configValue"
              disabled
              size="small"
            />
            <el-input
              v-else
              v-model="row._editingValue"
              size="small"
              @blur="handleUpdate(row)"
              @keyup.enter="handleUpdate(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="类型" width="100" prop="valueType">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.valueType)" size="small">{{ row.valueType }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="描述" min-width="200" prop="description" />

        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag :type="categoryType(row.configKey)" size="small">
              {{ categoryLabel(row.configKey) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="是否系统" width="100" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.isSystem === 1" color="#67c23a"><CircleCheckFilled /></el-icon>
            <el-icon v-else color="#909399"><CircleCloseFilled /></el-icon>
          </template>
        </el-table-column>

        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatDate(row.updatedTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.isSystem !== 1"
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
    </el-card>

    <!-- 新增配置对话框 -->
    <el-dialog v-model="addDialogVisible" title="新增配置项" width="500px">
      <el-form :model="newConfig" label-width="120px">
        <el-form-item label="配置键" required>
          <el-input v-model="newConfig.configKey" placeholder="如 rag.chunk.size" />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-input v-model="newConfig.configValue" placeholder="配置值" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select v-model="newConfig.valueType">
            <el-option label="string" value="string" />
            <el-option label="int" value="int" />
            <el-option label="long" value="long" />
            <el-option label="double" value="double" />
            <el-option label="boolean" value="boolean" />
            <el-option label="json" value="json" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newConfig.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { configApi } from '@/api/config'
import type { ConfigItem } from '@/types'

const router = useRouter()

const loading = ref(false)
const refreshing = ref(false)
const configList = ref<ConfigItem[]>([])
const addDialogVisible = ref(false)

const newConfig = ref({
  configKey: '',
  configValue: '',
  valueType: 'string',
  description: '',
  isSystem: 0,
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await configApi.list()
    configList.value = res.data.map(item => ({
      ...item,
      _editingValue: item.configValue,
    }))
  } finally {
    loading.value = false
  }
}

const handleUpdate = async (row: ConfigItem) => {
  if (row._editingValue === row.configValue) return
  try {
    await configApi.update(row.configKey, row._editingValue!)
    ElMessage.success('更新成功')
    row.configValue = row._editingValue!
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
    row._editingValue = row.configValue
  }
}

const handleDelete = async (row: ConfigItem) => {
  await ElMessageBox.confirm(`确定删除配置项「${row.configKey}」吗？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await configApi.delete(row.id)
  ElMessage.success('删除成功')
  fetchList()
}

const handleAdd = async () => {
  if (!newConfig.value.configKey || !newConfig.value.configValue) {
    ElMessage.warning('请填写配置键和配置值')
    return
  }
  await configApi.create(newConfig.value as any)
  ElMessage.success('创建成功')
  addDialogVisible.value = false
  newConfig.value = { configKey: '', configValue: '', valueType: 'string', description: '', isSystem: 0 }
  fetchList()
}

const handleRefreshCache = async () => {
  refreshing.value = true
  try {
    await configApi.refresh()
    ElMessage.success('缓存已刷新')
  } catch (e: any) {
    ElMessage.error(e?.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

const goBack = () => {
  router.push('/dashboard')
}

const formatDate = (d: string) => {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN')
}

const typeTag = (t: string) => {
  const map: Record<string, string> = { string: 'info', int: 'success', long: 'success', double: 'warning', boolean: 'danger', json: '' }
  return map[t] || 'info'
}

const categoryLabel = (key: string) => {
  if (key.startsWith('rag.chunk')) return '分块'
  if (key.startsWith('rag.retrieval')) return '检索'
  if (key.startsWith('rag.file')) return '文件'
  if (key.startsWith('rag.retry')) return '重试'
  if (key.startsWith('rag.chat')) return '聊天'
  if (key.startsWith('file.storage')) return '存储'
  return '其他'
}

const categoryType = (key: string) => {
  if (key.startsWith('rag.chunk')) return ''
  if (key.startsWith('rag.retrieval')) return 'success'
  if (key.startsWith('rag.file')) return 'warning'
  if (key.startsWith('rag.retry')) return 'danger'
  if (key.startsWith('rag.chat')) return 'info'
  if (key.startsWith('file.storage')) return ''
  return 'info'
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
