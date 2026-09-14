# RAG 知识库问答系统

一个基于 **RAG（Retrieval-Augmented Generation）** 架构的企业级知识库问答系统。用户上传文档后，系统自动完成「解析 → 分块 → 向量化 → 入库」全流程，用户即可通过自然语言对话获取知识库中的精准回答。

---

## 1. 系统概览

### 核心流程图

```text
用户上传文档
    │
    ▼
┌──────────┐      RocketMQ       ┌──────────┐      PGVector       ┌───────┐
│  MySQL   │ ──────────────────▶ │  解析服务  │ ───────────────▶ │ 向量库  │
│          │◀────────────────── │          │◀───────────────── │       │
│ 业务数据  │   状态更新/查询      │ 切片+Embed │    相似度检索     │ HNSW  │
└──────────┘                     └─────┬────┘                   └───┬───┘
                                       │                            │
                                       ▼                            │
                                    LLM（通义千问）                   │
                                       │                            │
                                       ▼                            │
                                   SSE 流式响应 ◀────────────────────┘
                                       │
                                       ▼
                                    用户
```

### 整体架构

```text
┌─────────────────────────────────────────────────────────────────┐
│                        前端层 (rag-web)                          │
│  Vue 3 + TypeScript + Element Plus + Vite                       │
│                                                                 │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐   │
│  │  仪表盘     │ │ 知识库管理  │ │  智能对话   │ │  系统配置  │   │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTP / SSE
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端 API (rag)                              │
│  Spring Boot 3.5 + Java 21 + Spring AI Alibaba                  │
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ 认证授权  │ │ 知识库   │ │ 文档处理  │ │  对话 / RAG      │  │
│  │ Sa-Token │ │ Service  │ │ Service  │ │  ChatService     │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  切片引擎 (ChunkingPipeline)                               │  │
│  │  Markdown / PDF / DOCX / TXT / Excel / PPTX               │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│     MySQL        │ │    RocketMQ     │ │  PostgreSQL     │
│   (业务数据)     │ │  (异步消息)     │ │  + pgvector     │
│                  │ │                  │ │   (向量检索)     │
│ ┌──────────────┐ │ │   Topic:        │ │                  │
│ │  sys_user    │ │ │   rag-doc-parse │ │   document_chunk │
│ │  kb_*        │ │ │                 │ │   └── embedding  │
│ │  chat_*      │ │ │   消费者:       │ │   └── HNSW      │
│ │  sys_config  │ │ │   DocumentParse │ │                  │
│ └──────────────┘ │ │   Consumer      │ │   向量维度: 1536 │
│                  │ │                 │ │   相似度: Cosine │
│                  │ └─────────────────┘ └─────────────────┘
└─────────────────────────────────────────────────────────────────┘
          ▲                   ▲                   ▲
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   阿里云 DashScope │ │   AWS S3 / 本地  │ │  Spring AI     │
│   Qwen 模型      │ │   文件存储       │ │  Embedding     │
│                  │ │                  │ │  VectorStore   │
│ • text-embedding │ │ • 本地磁盘       │ │                │
│   -v2 (1536维)  │ │ • MinIO / S3    │ │ • PGVector     │
│ • qwen-max      │ │                  │ │ • HNSW 索引    │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## 2. 技术栈

### 后端技术栈

| 技术/框架 | 版本 | 用途 |
|-----------|------|------|
| Java | 21 | 开发语言 |
| Spring Boot | 3.5.8 | 应用框架 |
| Spring AI | 1.1.2 | AI 能力（Embedding、VectorStore） |
| Spring AI Alibaba | 1.1.2.3 | 阿里 DashScope 集成 |
| MyBatis-Plus | 3.5.12 | MySQL ORM |
| PostgreSQL + pgvector | - | 向量存储与相似度检索 |
| RocketMQ | 2.3.6 | 文档异步处理消息队列 |
| Sa-Token | 1.39.0 | 权限认证（JWT + Redis） |
| Redis | - | 缓存与会话上下文 |
| Apache Tika | 3.3.2 | 多格式文件解析（PDF/DOCX/PPTX/Excel/TXT/MD） |
| AWS S3 SDK | 2.25.0 | 云存储支持（MinIO/OSS/S3） |
| Lombok | - | 代码简化 |

### 前端技术栈

| 技术/框架 | 版本 | 用途 |
|-----------|------|------|
| Vue 3 | 3.5.13 | 前端框架 |
| TypeScript | 5.7.3 | 类型安全 |
| Element Plus | 2.9.8 | UI 组件库 |
| Vite | 6.3.5 | 构建工具 |
| Pinia | 3.0.2 | 状态管理 |
| Vue Router | 4.5.1 | 路由 |
| Axios | 1.8.4 | HTTP 请求 |
| Marked + DOMPurify | 18.0.12 / 3.4.15 | Markdown 渲染与 XSS 防护 |
| highlight.js | 11.12.0 | 代码高亮 |
| github-markdown-css | 5.9.0 | Markdown 样式 |

---

## 3. 核心功能

### 3.1 知识库管理

- 创建/编辑/删除知识库
- 知识库状态管理（启用/禁用）
- 知识库归属校验（基于 Sa-Token 权限）

### 3.2 文档上传与处理

- 支持格式：PDF、DOCX、PPTX、XLSX、TXT、Markdown
- 文件重复检测（MD5 + 知识库维度）
- 自动异步解析：`RocketMQ` 消息驱动 `DocumentParseConsumer`
- 文档状态机：
  ```
  待解析 → 解析中 → 解析成功 → 待向量化 → 向量化中 → 向量化完成
                      └── 解析失败                        └── 向量化失败
  ```
- 失败自动重试（可配置重试次数与间隔）
- SSE 实时推送解析进度

### 3.3 智能分块引擎

采用**两阶段切片策略**：

```
ParsedDocument（Tika 解析结果）
    │
    ▼
ChunkingStrategyFactory（按 fileType 选择策略）
    │
    ├── MarkdownChunkingStrategy  → 标题层级切片
    ├── PdfChunkingStrategy       → 页面/段落切片
    ├── DocxChunkingStrategy      → 段落/表格切片
    ├── TxtChunkingStrategy       → 纯文本固定长度切片
    └── DefaultChunkingStrategy   → 兜底策略
    │
    ▼
LengthSplitter（特殊内容处理 + 长度约束 + overlap）
    │
    ├── 特殊内容检测：FAQ、表格、代码块、JSON
    ├── 超长 Section 自动切分
    └── overlap 滑动窗口
    │
    ▼
List<Chunk>
```

配置项（`application.yaml`）：
```yaml
rag:
  chunk:
    size: 500        # 分块大小（字符数）
    overlap: 50      # 重叠大小（字符数）
```

### 3.4 向量化与存储

- Embedding 模型：DashScope `text-embedding-v2`（1536 维）
- 批量向量化（配置批次大小与延迟，防止限流）
- 向量库：PostgreSQL + pgvector
- 向量索引：HNSW + `vector_cosine_ops`
- 业务过滤索引：`kb_id`、`doc_id`
- 失败自动回滚（清理已写入向量）

### 3.5 RAG 检索与对话

- **向量检索**：基于 pgvector HNSW 近似最近邻搜索
- **检索过滤**：支持按 `kb_id` 多选过滤
- **对话上下文**：Redis 缓存会话历史
- **LLM**：阿里通义千问 `qwen-max`
- **流式响应**：SSE（Server-Sent Events）
- **引用溯源**：返回引用文档片段（文件名、相似度分数）

```
用户问题
    │
    ▼
Query Embedding（text-embedding-v2）
    │
    ▼
向量检索（HNSW Top-K，kb_id 过滤）
    │
    ▼
构建 Prompt（系统提示词 + 检索上下文 + 历史消息）
    │
    ▼
LLM 生成（qwen-max）
    │
    ▼
SSE 流式返回（含引用文档元数据）
```

### 3.6 用户认证与权限

- 注册/登录/登出
- JWT + Sa-Token 认证
- 基于 Redis 的 Token 存储
- 30 天有效期，支持在线文档中查阅

### 3.7 文件存储

支持**策略模式**切换存储后端：

```yaml
file:
  storage:
    type: local  # local | cloud
    local:
      upload-path: ${user.dir}/rag-uploads/
      url-prefix: http://127.0.0.1:8080/upload/
    cloud:
      endpoint: http://127.0.0.1:9000
      bucket: rag-files
      access-key: xxxxxxx
      secret-key: xxxx
      region: cn-north-1
      public-url-prefix: http://127.0.0.1:9000/rag-files/
      path-style-access: true
```

---

## 4. 数据库设计

### MySQL（业务数据）

```
MySQL (rag_v1)
│
├── sys_user                    # 用户表
│   ├── id, username, password (BCrypt)
│   ├── nickname, role (1-管理员/2-普通用户)
│   └── status, created_time, deleted
│
├── kb_knowledge_base           # 知识库表
│   ├── id, name, description
│   ├── creator_id, status (1-启用/2-禁用)
│   └── created_time, deleted
│
├── kb_document                 # 文档表
│   ├── id, kb_id, title, file_name, file_type
│   ├── file_size, file_path, file_md5
│   ├── parse_status (0-待解析/1-解析中/2-成功/3-失败)
│   ├── vector_status (0-待向量化/1-向量化中/2-完成/3-失败)
│   ├── chunk_count, vector_count
│   ├── error_msg, version, retry_count
│   └── creator_id, created_time, deleted
│
├── kb_upload_record            # 上传记录表
│   ├── kb_id, doc_id, original_filename, stored_filename
│   ├── file_size, file_md5, mime_type, storage_type
│   ├── check_status (1-通过/2-拒绝), reject_reason
│   └── uploader_id, deleted
│
├── chat_session                # 对话会话表
│   ├── session_id (UUID, 唯一)
│   ├── user_id, kb_id, title, message_count
│   └── created_time, updated_time, deleted
│
├── chat_message                # 对话消息表
│   ├── session_id, role (1-用户/2-助手/3-系统)
│   ├── content, ref_chunks (JSON)
│   └── token_count, created_time
│
└── sys_config                  # 系统配置表（动态配置缓存）
```

### PostgreSQL + pgvector（向量数据）

```sql
-- pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 向量存储表
CREATE TABLE document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    kb_id           BIGINT NOT NULL,           -- 知识库 ID（检索过滤）
    doc_id          BIGINT NOT NULL,           -- 文档 ID（关联 MySQL）
    chunk_index     INT NOT NULL,              -- 分块序号
    chunk_total     INT NOT NULL DEFAULT 0,    -- 总分块数
    content         TEXT NOT NULL,             -- 分块文本
    content_hash    VARCHAR(64),               -- 内容 Hash（变更检测）
    embedding       VECTOR(1536) NOT NULL,     -- 1536 维向量
    embedding_model VARCHAR(100) NOT NULL      -- 模型名称
                    DEFAULT 'text-embedding-v2',
    metadata        JSONB,                     -- 扩展元数据
    created_time    TIMESTAMP NOT NULL
                    DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 向量索引（Cosine 相似度）
CREATE INDEX idx_chunk_embedding_hnsw
ON document_chunk
USING hnsw (embedding vector_cosine_ops);

-- 业务过滤索引
CREATE INDEX idx_chunk_kb_id ON document_chunk (kb_id);
CREATE INDEX idx_chunk_doc_id ON document_chunk (doc_id);
CREATE INDEX idx_chunk_kb_doc ON document_chunk (kb_id, doc_id);
```

**MySQL 与 PostgreSQL 的职责划分：**

| 数据类型 | 存储位置 | 原因 |
|----------|----------|------|
| 用户、知识库、文档、上传记录、会话、消息、配置 | MySQL | 业务数据，需要事务支持、关系查询 |
| Chunk 内容、Embedding、向量检索 | PostgreSQL + pgvector | 向量专用存储，支持 HNSW 索引和相似度运算 |

---

## 5. 项目结构

```
RAG/
├── pom.xml                          # Maven 父 POM（多模块）
├── CLAUDE.md                        # 开发规范
├── common/                          # 公共模块
│   └── src/main/java/com/wangzs/rag/common/
│       ├── enums/                   # 通用枚举
│       ├── exception/               # 异常定义（BizException、ErrorCode）
│       ├── result/                  # 统一响应（ApiResult）
│       └── util/                    # 工具类（AuthUtil 等）
│
├── rag/                             # 后端核心模块
│   ├── pom.xml
│   ├── src/main/java/com/wangzs/rag/
│   │   ├── RagApplication.java      # 启动类
│   │   │
│   │   ├── config/                  # 配置类
│   │   │   ├── DataSourceConfig     # 多数据源（MySQL + PGVector）
│   │   │   ├── JacksonConfig        # JSON 序列化
│   │   │   ├── RedisConfig          # Redis 配置
│   │   │   ├── S3Config              # AWS S3 配置
│   │   │   └── SecurityConfig       # Sa-Token 安全配置
│   │   │
│   │   ├── controller/              # 控制器层
│   │   │   ├── auth/AuthController           # 登录/注册/登出
│   │   │   ├── ChatController                # 对话接口（SSE）
│   │   │   ├── ChatSessionController         # 会话管理
│   │   │   ├── KnowledgeBaseController       # 知识库 CRUD
│   │   │   ├── DocumentController            # 文档管理
│   │   │   ├── UploadController              # 文件上传
│   │   │   └── ConfigController              # 系统配置
│   │   │
│   │   ├── service/                 # 服务层
│   │   │   ├── AuthService / AuthServiceImpl
│   │   │   ├── ChatService                       # RAG 对话核心逻辑
│   │   │   ├── ChatSessionService                # 会话管理
│   │   │   ├── ChatMessageService                # 消息管理
│   │   │   ├── KnowledgeBaseService              # 知识库管理
│   │   │   ├── DocumentService                   # 文档生命周期管理
│   │   │   ├── UploadService / UploadServiceImpl  # 上传流程
│   │   │   ├── FileParseService                  # 文件解析策略调用
│   │   │   ├── FileStorageService                # 文件存储策略
│   │   │   │   ├── FileStorageFactory
│   │   │   │   ├── FileStorageLocalServiceImpl
│   │   │   │   └── FileStorageCloudServiceImpl
│   │   │   ├── FileCheckService                  # 文件校验（MD5/大小/类型）
│   │   │   ├── EmbeddingService                  # 向量化 + 批量入库
│   │   │   ├── RetrievalService                  # 向量检索
│   │   │   ├── ConfigService                     # 动态配置读取
│   │   │   ├── DocumentRetryScheduler            # 定时重试任务
│   │   │   └── UploadRecordService / UploadRecordServiceImpl
│   │   │
│   │   ├── mapper/                  # MyBatis-Plus Mapper
│   │   │   ├── UserMapper
│   │   │   ├── KnowledgeBaseMapper
│   │   │   ├── DocumentMapper
│   │   │   ├── UploadRecordMapper
│   │   │   ├── ChatSessionMapper
│   │   │   ├── ChatMessageMapper
│   │   │   └── ConfigMapper
│   │   │
│   │   ├── repository/              # PostgreSQL 数据访问
│   │   │   └── VectorDocumentChunkRepository  # 向量 Chunk CRUD
│   │   │
│   │   ├── model/                   # 数据模型
│   │   │   ├── entity/              # MySQL 实体
│   │   │   │   ├── User, KnowledgeBase, Document
│   │   │   │   ├── UploadRecord, ChatSession, ChatMessage, Config
│   │   │   ├── vector/              # PostgreSQL 实体
│   │   │   │   └── VectorDocumentChunk
│   │   │   ├── dto/                 # 请求/响应 DTO
│   │   │   │   ├── LoginRequest, RegisterRequest
│   │   │   │   ├── ChatRequest, ChatResponse
│   │   │   │   ├── ChunkVO, KnowledgeBaseCreateDTO
│   │   │   │   ├── UploadFileRequest, DocumentParseMsgDTO
│   │   │   └── vo/                  # 视图对象
│   │   │
│   │   ├── chunk/                   # 文档分块引擎
│   │   │   ├── Chunk                # 分块结果模型
│   │   │   ├── ChunkingPipeline     # 分块流程编排
│   │   │   ├── ChunkingStrategy     # 策略接口
│   │   │   ├── ChunkingStrategyFactory  # 策略工厂
│   │   │   ├── DefaultChunkingStrategy  # 默认策略
│   │   │   ├── ParsedDocumentBuilder    # 文档模型构建
│   │   │   ├── TextSplitter             # 纯文本切分
│   │   │   ├── common/                  # 通用切分逻辑
│   │   │   │   ├── LengthSplitter       # 长度约束 + overlap
│   │   │   │   ├── SpecialContentDetector / Handler
│   │   │   ├── markdown/                # Markdown 切片
│   │   │   ├── pdf/                     # PDF 切片
│   │   │   ├── docx/                    # DOCX 切片
│   │   │   └── txt/                     # TXT 切片
│   │   │
│   │   ├── strategy/                # 文件解析策略
│   │   │   ├── FileParseStrategy    # 策略接口
│   │   │   ├── ParseStrategyFactory # 工厂
│   │   │   └── TikaParseStrategy    # Tika 通用解析
│   │   │
│   │   ├── enums/                   # 业务枚举
│   │   │   ├── ChatRoleEnum
│   │   │   ├── CheckStatusEnum
│   │   │   ├── DeletedEnum
│   │   │   ├── KnowledgeBaseStatusEnum
│   │   │   ├── ParseStatusEnum
│   │   │   ├── SystemConfigFlagEnum
│   │   │   ├── UserRoleEnum / UserStatusEnum
│   │   │   └── VectorStatusEnum
│   │   │
│   │   ├── consumer/                # RocketMQ 消费者
│   │   │   └── DocumentParseConsumer  # 文档解析主消费者
│   │   │
│   │   ├── filter/                  # 过滤器
│   │   │   └── TraceIdFilter         # 链路追踪
│   │   │
│   │   └── util/                    # 工具类
│   │       ├── FileUtil, IdUtil, MD5Util, RedisUtil
│   │
│   └── src/main/resources/
│       ├── application.yaml         # 主配置文件
│       ├── mapper/                  # MyBatis XML
│       │   ├── chat/
│       │   ├── kb/
│       │   └── sys/
│       └── sql/                     # 数据库脚本
│           ├── schema.sql           # MySQL 建表
│           ├── schema_document_chunk.sql
│           ├── schema_pgvector_document_chunk.sql
│           ├── schema_config.sql
│           ├── mysql_clear_all.sql
│           ├── pgvector_clear_all.sql
│           └── update_*.sql         # 版本升级脚本
│
├── rag-web/                         # 前端项目
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.ts                  # 入口
│       ├── App.vue                  # 根组件
│       ├── layout/Index.vue         # 布局容器
│       ├── router/index.ts          # 路由配置
│       ├── stores/                  # Pinia 状态
│       │   ├── auth.ts              # 认证状态
│       │   └── knowledgeBase.ts     # 知识库状态
│       ├── api/                     # API 接口
│       │   ├── auth.ts, chat.ts, config.ts
│       │   ├── document.ts, knowledgeBase.ts
│       ├── views/                   # 页面组件
│       │   ├── Dashboard.vue        # 仪表盘
│       │   ├── auth/Login.vue       # 登录页
│       │   ├── knowledge-base/      # 知识库管理
│       │   ├── document/            # 文档列表/上传
│       │   ├── chat/                # 智能对话
│       │   │   ├── Chat.vue
│       │   │   └── components/      # 对话子组件
│       │   └── settings/            # 系统配置
│       ├── assets/css/main.scss
│       ├── styles/chat-markdown.css
│       ├── types/index.ts           # TypeScript 类型
│       └── utils/axios.ts           # HTTP 封装
│
├── docs/                            # 设计文档
│   ├── RAG MySQL 数据库实现总览.md
│   ├── RAG向量库实现总览.md
│   ├── RAG文档切片架构总览.md
│   ├── PostgreSQL数据访问层技术选型说明.md
│   ├── Phase-*-修复总结.md
│   └── TODO.md
│
└── test/                            # 集成测试
    └── ...
```

---

## 6. 快速开始

### 环境要求

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 21 | [Eclipse Temurin](https://adoptium.net/) |
| Maven | 3.8+ | 构建工具 |
| MySQL | 8.0+ | 业务数据库 |
| PostgreSQL | 14+ + pgvector | 向量数据库 |
| Redis | 6+ | 缓存与会话 |
| RocketMQ | 4.9+ | 异步消息 |
| Node.js | 18+ | 前端构建 |
| DashScope API Key | - | 阿里云灵积 API |

### 6.1 数据库初始化

#### MySQL

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE rag_v1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行建表脚本
mysql -u root -p rag_v1 < rag/src/main/resources/sql/schema.sql
```

#### PostgreSQL + pgvector

```bash
# 创建数据库（需先安装 pgvector 扩展）
sudo apt install postgresql-14-vector  # Ubuntu
# 或 brew install pgvector            # macOS

# 创建扩展和表
psql -U postgres -d vector_db -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U postgres -d vector_db < rag/src/main/resources/sql/schema_document_chunk.sql
```

> 详细向量库设计文档见 `docs/RAG向量库实现总览.md`

### 6.2 后端启动

```bash
# 1. 配置环境变量
export AI_DASHSCOPE_API_KEY="your-dashscope-api-key"

# 2. 修改 application.yaml 中的数据库连接和文件存储路径

# 3. 构建并启动
mvn spring-boot:run -pl rag
```

应用启动在 `http://localhost:8080`。

### 6.3 前端启动

```bash
cd rag-web

# 安装依赖
npm install

# 开发模式（自动代理到后端 8080）
npm run dev
```

前端运行在 `http://localhost:5173`。

### 6.4 默认账号

```
用户名: admin
密码: 123456（初始化数据在 schema.sql 中）
```

> 生产环境请立即修改密码。

---

## 7. 核心流程详解

### 7.1 文档上传流程

```text
用户选择文件
    │
    ▼
文件校验（FileCheckService）
    │   ├── 大小限制（默认 50MB）
    │   ├── 扩展名白名单
    │   └── MD5 重复检测（同知识库内）
    ▼
创建 UploadRecord + Document（同一事务）
    │
    ▼
保存物理文件（本地 / S3）
    │
    ▼
发送 RocketMQ 消息（docId + version）
    │
    ▼
DocumentParseConsumer 消费消息
    │
    ├── 幂等去重（Redis SETNX）
    ├── 版本校验（拦截过时消息）
    │
    ▼
阶段一：文件解析 + 分块
    │   ├── Tika 解析 → 文本
    │   ├── ChunkingPipeline 切片 → List<Chunk>
    │   ├── 持久化 Chunk 到 PostgreSQL（恢复点）
    │   └── 更新 MySQL parse_status = 2
    │
    ▼
阶段二：向量化入库
    │   ├── Batch Embedding（批次大小可配置）
    │   ├── 构建 metadata（doc_id, kb_id, title, version...）
    │   ├── Batch Insert 到 pgvector
    │   └── 更新 MySQL vector_status = 2
    │
    ▼
完成
```

### 7.2 失败处理与重试

```text
解析失败
    │
    ▼
parse_status = 3, error_msg = "..."
    │
    ▼
DocumentRetryScheduler（定时任务）
    │   ├── 查询解析失败/向量化失败的文档
    │   ├── 检查重试次数 < 最大次数
    │   └── 重新发送 RocketMQ 消息
    ▼
重新处理（版本号不变，幂等）
```

配置：
```yaml
rag:
  retry:
    max-attempts: 3           # 最大重试次数
    interval-minutes: 5       # 重试间隔（分钟）
```

### 7.3 RAG 对话流程

```text
用户输入问题 + 选择知识库
    │
    ▼
ChatService.chatStream()（SSE 流式）
    │
    ├── 1. 确保会话存在（Sa-Token 获取用户 ID）
    ├── 2. RetrievalService.search()
    │   │   ├── 问题 Embedding（text-embedding-v2）
    │   │   ├── 向量检索（HNSW, Top-K, kb_id 过滤）
    │   │   └── 返回 List<SearchResult>
    │
    ├── 3. 构建 Prompt
    │   ├── 系统提示词（ConfigService 读取，可自定义）
    │   ├── 参考文档片段（Top-K 检索结果）
    │   └── Redis 历史上下文
    │
    ├── 4. LLM 流式调用（qwen-max）
    │
    ├── 5. SSE 返回
    │   ├── [REF_CHUNKS: ...]  引用文档元数据
    │   ├── 流式文本内容
    │   ├── [SESSION_ID: xxx]  会话标识
    │   └── [DONE]            结束标记
    │
    └── 6. 异步保存对话消息（含引用记录）
```

---

## 8. API 接口概览

### 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/logout` | 用户登出 |
| GET | `/api/auth/me` | 获取当前用户信息 |

### 知识库接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge-base` | 创建知识库 |
| GET | `/api/knowledge-base` | 知识库列表 |
| GET | `/api/knowledge-base/{id}` | 知识库详情 |
| PUT | `/api/knowledge-base/{id}` | 更新知识库 |
| DELETE | `/api/knowledge-base/{id}` | 删除知识库 |

### 文档接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge-base/{kbId}/documents` | 文档列表 |
| POST | `/api/upload` | 上传文件 |
| GET | `/api/documents/{id}/progress` | 解析进度（SSE） |
| DELETE | `/api/documents/{id}` | 删除文档 |

### 对话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 对话（非流式） |
| POST | `/api/chat/stream` | 对话（SSE 流式） |
| GET | `/api/chat/sessions` | 会话列表 |
| GET | `/api/chat/sessions/{sessionId}/messages` | 消息历史 |
| DELETE | `/api/chat/sessions/{sessionId}` | 删除会话 |

### 系统配置接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/config/rag` | RAG 配置 |
| PUT | `/api/config/rag` | 更新配置 |

---

## 9. 核心设计原则

### 9.1 数据库职责分离

```
MySQL ──→ 业务数据（用户、知识库、文档、会话、配置）
PostgreSQL ──→ 检索数据（Chunk、Embedding、HNSW）
```

**不要**在 MySQL 存储向量，**不要**在 PostgreSQL 存储业务关系。

### 9.2 异步处理

文档解析和向量化通过 RocketMQ 异步处理，避免阻塞上传请求，支持失败重试和进度追踪。

### 9.3 策略模式

- **文件解析**：`FileParseStrategy` + `ParseStrategyFactory`
- **文件存储**：`IFileStorageService` + `FileStorageFactory`
- **文档切片**：`ChunkingStrategy` + `ChunkingStrategyFactory`

### 9.4 幂等性保证

- RocketMQ 消息携带 `(docId, version)`，通过 Redis SETNX 去重
- 版本号校验拦截过时/乱序消息
- 重复上传检测：同知识库内 `(file_md5, kb_id)` 唯一

### 9.5 失败安全

- 向量化失败自动回滚（清理已写入的向量）
- 解析失败保留分块数，支持定向重试
- 定时任务自动重试失败任务

### 9.6 配置外部化

所有业务参数通过 `ConfigService` 从数据库读取，支持运行时动态调整：

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `rag.chunk.size` | 500 | 分块大小（字符） |
| `rag.chunk.overlap` | 50 | 重叠大小（字符） |
| `rag.chunk.max-tokens` | 800 | 最大 Token 数 |
| `rag.chunk.overlap-tokens` | 100 | 重叠 Token 数 |
| `rag.retrieval.top-k` | 5 | 检索 Top-K |
| `rag.retrieval.similarity-threshold` | 0.6 | 相似度阈值 |
| `rag.file.max-size` | 52428800 | 文件大小限制（50MB） |
| `rag.file.allowed-extensions` | pdf,docx,txt,md,xlsx,pptx | 允许的扩展名 |
| `rag.retry.max-attempts` | 3 | 最大重试次数 |
| `rag.retry.interval-minutes` | 5 | 重试间隔（分钟） |
| `rag.embedding.batch-size` | 25 | Embedding 批次大小 |
| `rag.embedding.batch-delay-ms` | 200 | 批次间延迟（毫秒） |

---

## 10. 开发规范

项目遵循严格的**单一职责原则**（见 `CLAUDE.md`）：

- 一次只做一件事
- 一个文件/类/方法只承担一个职责
- 最小变更原则
- 禁止过度设计
- Controller 中不允许编写业务逻辑

---

## 11. 常见问题

### 向量检索无结果怎么办？

1. 检查文档是否已上传并解析完成（`parse_status = 2`）
2. 检查向量化是否成功（`vector_status = 2`）
3. 检查 `vector_store` 表是否有数据：
   ```sql
   SELECT COUNT(*) FROM document_chunk WHERE kb_id = ?;
   ```
4. 检查相似度阈值是否过高（默认 0.6，临时调试可设为 0.0）

### 如何清空数据重新开始？

```bash
# MySQL（保留用户）
mysql -u root -p rag_v1 < rag/src/main/resources/sql/mysql_clear_all.sql

# PostgreSQL
psql -U postgres -d vector_db -f rag/src/main/resources/sql/pgvector_clear_all.sql
```

### 如何更换 Embedding 模型？

修改 `application.yaml`：
```yaml
spring:
  ai:
    dashscope:
      embedding:
        options:
          model: text-embedding-v3  # 新模型
```

然后重新向量化已有文档（系统会根据 `embedding_model` 字段区分版本）。

---

## 12. 扩展方向

- **Rerank 重排序**：向量检索 Top-20 → Reranker → Top-5
- **混合检索**：向量检索 + 关键词检索（BM25）
- **多模态支持**：图片 OCR + 向量化
- **文档版本管理**：增量更新（只重新 Embedding 变化的 Chunk）
- **多知识库对话**：同时检索多个知识库
- **知识库权限**：团队协作与知识库共享

---

## 13. 许可证

本项目仅供学习与研究使用。
