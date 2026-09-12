<template>
  <div class="login-container">
    <el-card class="login-card">
      <h1 class="login-title">RAG 知识库系统</h1>
      <p class="login-subtitle">登录以开始使用</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
        aria-label="登录表单"
      >
        <el-form-item label="用户名" prop="username" required>
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            aria-describedby="username-help"
            autocomplete="username"
          />
          <template #help>
            <div id="username-help" class="form-help-text">3-20个字符</div>
          </template>
        </el-form-item>
        <el-form-item label="密码" prop="password" required>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            aria-describedby="password-help"
            autocomplete="current-password"
          />
          <template #help>
            <div id="password-help" class="form-help-text">6-32个字符</div>
          </template>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
            native-type="submit"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div style="text-align: center; margin-top: 16px">
        <el-text size="small" type="info">
          还没有账号？
          <el-link type="primary" @click="showRegister = true">立即注册</el-link>
        </el-text>
      </div>
    </el-card>

    <!-- 注册对话框 -->
    <el-dialog v-model="showRegister" title="注册账号" width="420px">
      <el-form ref="regFormRef" :model="regForm" :rules="regRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="regForm.nickname" placeholder="请输入昵称（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="handleRegister">
          注册
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const showRegister = ref(false)
const regFormRef = ref<FormInstance>()
const regLoading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const regForm = reactive({
  username: '',
  password: '',
  nickname: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度在 6 到 32 个字符', trigger: 'blur' },
  ],
}

const regRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度在 6 到 32 个字符', trigger: 'blur' },
  ],
}

const handleLogin = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    await authStore.login(form)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  await regFormRef.value?.validate()
  regLoading.value = true
  try {
    await authApi.register(regForm)
    ElMessage.success('注册成功，请登录')
    showRegister.value = false
    regForm.username = ''
    regForm.password = ''
    regForm.nickname = ''
  } catch (error) {
    // handled
  } finally {
    regLoading.value = false
  }
}
</script>

<style scoped>
.login-container {
  width: 100%;
  height: 100vh;
}

.login-card {
  width: 420px;
}

.login-title {
  text-align: center;
  margin-bottom: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.login-subtitle {
  text-align: center;
  margin-bottom: 32px;
  font-size: 14px;
  color: #909399;
}

/* 表单帮助文本样式 */
.form-help-text {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
