# 待完成功能清单

> 最后更新：2026-09-12

---

## 一、后端 Controller 端点

### 1.1 文档管理 (`DocumentController`) — 路由前缀 `/api/doc`

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **文档删除** | ✅ 已实现 | `DELETE /{id}` — DocumentController:95，物理文件删除 + 逻辑删除（`deleted=1`） |
| **文档重试解析** | ✅ 已实现 | `POST /{id}/retry` — DocumentController:72，重置解析/向量化状态，重新发送 MQ 消息 |
| **文档详情查询** | ❌ 缺失 | 需新增 `GET /{id}`，前端 `documentApi.getById()` 调用此端点 |
| **文档分块查看** | ❌ 缺失 | 需新增 `GET /{id}/chunks`，返回分块列表（索引、内容、长度），前端 `showChunks()` 依赖此接口 |

### 1.2 知识库管理 (`KnowledgeBaseController`) — 路由前缀 `/api/knowledge-base`

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **知识库 CRUD** | ✅ 已实现 | 创建/查询/列表/更新/删除均已实现 |
| **删除级联清理** | ⚠️ 部分实现 | `DELETE /{id}` 仅标记 `deleted=1`，**未**级联删除 kb_document + 向量 + 上传文件 |

### 1.3 对话会话管理 — **新建 `ChatSessionController`**，路由前缀 `/api/chat`

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **会话列表查询** | ❌ 缺失 | `GET /sessions`，前端 `chatApi.getSessions()` 已调用此端点，但后端无 Controller |
| **会话消息查询** | ❌ 缺失 | `GET /sessions/{sessionId}/messages`，前端 `chatApi.getMessages()` 已调用 |
| **会话删除** | ❌ 缺失 | `DELETE /sessions/{sessionId}`，前端 `chatApi.deleteSession()` 已定义 |

> 注：`ChatService` 已实现完整的会话创建/消息保存/历史上下文逻辑，但缺少对外 HTTP 端点。

### 1.4 文件下载/预览

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **文件下载/预览** | ❌ 缺失 | 前端可能需要 `GET /api/upload/{fileKey}` 或 `GET /api/doc/{id}/preview` |

### 1.5 解析进度查询

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **解析任务进度** | ❌ 缺失 | 需新增 `GET /api/doc/{id}/parse-status`，返回当前阶段 + 进度百分比（解析中/向量化中可返回估算进度） |

---

## 二、前端缺失的页面/功能

> 前端目录：`rag-web/src/`

### 2.1 页面与组件

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **仪表盘 (Dashboard)** | ✅ 已实现 | `views/Dashboard.vue` — 知识库数量、文档总数、对话轮次、已向量化统计 |
| **知识库管理** | ✅ 已实现 | `views/know-base/KnowledgeBaseList.vue` |
| **文档列表** | ✅ 已实现 | `views/document/DocumentList.vue` |
| **文档上传** | ✅ 已实现 | `views/document/DocumentUpload.vue` |
| **智能对话** | ✅ 已实现 | `views/chat/Chat.vue` — 会话列表 + 消息展示 + 提问 |
| **系统配置** | ✅ 已实现 | `views/settings/Settings.vue` |
| **文档详情页** | ❌ 缺失 | 查看文档元信息、解析/向量化状态、分块内容 |
| **分块预览组件** | ⚠️ TODO 占位 | DocumentList.vue:180 `showChunks()` 内为硬编码文本，需接入 `/doc/{id}/chunks` 接口 |

### 2.2 API 层

| 功能 | 当前状态 | 说明 |
|------|----------|------|
| **API 基础封装** | ✅ 已实现 | `api/document.ts`、`api/chat.ts`、`api/knowledgeBase.ts`、`api/auth.ts`、`api/config.ts` |
| **请求拦截器** | ✅ 已实现 | `utils/axios.ts` — 自动携带 Token |
| **401 统一跳转** | ✅ 已实现 | `utils/axios.ts:35` — 401 时 `authStore.logout()` + `window.location.href = '/#/login'` |
| **路由守卫** | ✅ 已实现 | `router/index.ts:60` — 未登录自动跳转 Login |

### 2.3 缺失的前端功能

| 功能 | 优先级 | 说明 |
|------|--------|------|
| **分块预览替换 TODO** | P1 | DocumentList.vue `showChunks()` 需调用 `/doc/{id}/chunks` 替换硬编码文本 |
| **流式 SSE 对话接入** | P2 | 后端 `/chat/completions/stream` 已就绪，前端需切换为流式模式 |
| **解析进度实时更新** | P2 | 前端轮询 `/doc/{id}/parse-status` 或 WebSocket 推送 |
| **文件上传进度条** | P2 | 当前为直接上传，可加 `axios` `onUploadProgress` |

---

## 三、实现优先级

### P0（阻塞使用）— 后端接口缺失，前端已调用但 404

| # | 任务 | 涉及文件 |
|---|------|----------|
| 1 | **新增 `GET /api/doc/{id}`** — 文档详情查询 | `DocumentController` |
| 2 | **新增 `GET /api/doc/{id}/chunks`** — 文档分块查看 | `DocumentController` |
| 3 | **新建 `ChatSessionController`** — 会话列表/消息查询/删除 | `controller/ChatSessionController.java`（新建） |

### P1（体验完善）

| # | 任务 | 涉及文件 |
|---|------|----------|
| 4 | **知识库删除级联清理** — 删除 kb_document + 向量 + 上传文件 | `KnowledgeBaseService.delete()` |
| 5 | **解析进度查询** — `GET /api/doc/{id}/parse-status` | `DocumentController` |
| 6 | **前端分块预览替换 TODO** — 调用 `/doc/{id}/chunks` | `DocumentList.vue` `showChunks()` |
| 7 | **文件下载/预览接口** — `GET /api/upload/{fileKey}` | `UploadController` 或新建 |

### P2（优化扩展）

| # | 任务 | 说明 |
|---|------|------|
| 8 | **流式 SSE 对话接入前端** | 后端已就绪，前端 Chat.vue 切换为 `EventSource` |
| 9 | **解析进度实时推送** | WebSocket 或 SSE 推送解析状态 |
| 10 | **Dashboard 统计页面** | ✅ 已实现基础版本，可继续丰富 |

---

## 四、已实现的完整功能清单（供参考）

### 后端

| 模块 | 功能 | 路由 |
|------|------|------|
| 认证 | 登录/注册/用户信息 | `/api/auth/**` |
| 知识库 | 创建/列表/详情/更新/删除 | `/api/knowledge-base/**` |
| 文档 | 上传/列表/重试/删除 | `/api/upload/file`, `/api/doc/**` |
| 对话 | RAG问答（非流式/流式SSE） | `/api/chat/completions`, `/api/chat/completions/stream` |
| 配置 | 查询/修改/新增/删除/刷新 | `/api/settings/**` |

### 前端

| 页面 | 路径 | 功能 |
|------|------|------|
| 登录 | `/login` | 用户名密码登录 |
| 仪表盘 | `/dashboard` | 统计数据 + 最近文档 + 知识库列表 |
| 知识库管理 | `/knowledge-base` | 知识库 CRUD |
| 文档管理 | `/knowledge-base/:kbId/documents` | 文档列表 + 操作 |
| 文档上传 | `/knowledge-base/:kbId/upload` | 文件上传 |
| 智能对话 | `/chat` | 会话列表 + RAG 对话 |
| 系统配置 | `/settings` | 配置项管理 |
