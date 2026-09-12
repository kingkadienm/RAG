import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/layout/Index.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '仪表盘', icon: 'Odometer' },
        },
        {
          path: 'knowledge-base',
          name: 'KnowledgeBase',
          component: () => import('@/views/knowledge-base/KnowledgeBaseList.vue'),
          meta: { title: '知识库管理', icon: 'Files' },
        },
        {
          path: 'knowledge-base/:kbId/documents',
          name: 'DocumentList',
          component: () => import('@/views/document/DocumentList.vue'),
          meta: { title: '文档管理', icon: 'Document', hidden: true },
        },
        {
          path: 'knowledge-base/:kbId/upload',
          name: 'DocumentUpload',
          component: () => import('@/views/document/DocumentUpload.vue'),
          meta: { title: '上传文档', icon: 'Upload', hidden: true },
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/chat/Chat.vue'),
          meta: { title: '智能对话', icon: 'ChatDotRound' },
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('@/views/settings/Settings.vue'),
          meta: { title: '系统配置', icon: 'Setting' },
        },
      ],
    },
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isLoggedIn()) {
    next({ name: 'Login' })
  } else if (to.name === 'Login' && authStore.isLoggedIn()) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
