# RAG 系统开发计划

## Context

当前项目是一个 Spring Boot 3.5 + Spring AI 1.1 的 RAG 学习项目，骨架已搭建完毕：
- 使用阿里云百炼 (DashScope) 作为 LLM + Embedding 模型
- PGVector 已配置为向量存储
- Apache Tika 已声明用于文件解析
- 项目结构：parent → common（共享模块） + rag（业务模块）

本项目需要从骨架扩展为一个**完整可运行的 RAG 知识库系统**。

---

## 一、补充依赖

### parent/pom.xml — 新增 dependencyManagement

| 依赖 | 用途 |
|------|------|
| `com.baomidou:mybatis-plus-boot-starter` | MyBatis-Plus starter |
| `org.springframework.boot:spring-boot-starter-data-redis` | Redis |
| `mysql:mysql-connector-java` | MySQL JDBC 驱动 |
| `org.apache.commons:commons-io` | 文件工具 |
| `org.apache.commons:commons-lang3` | 字符串工具 |
| `org.springframework.boot:spring-boot-starter-validation` | JSR-380 校验 |
| `software.amazon.awssdk:s3` | AWS S3 SDK（支持 MinIO / OSS / S3） |
| `cn.dev33:sa-token-spring-boot3-starter` | Sa-Token 鉴权 |
| `org.apache.rocketmq:rocketmq-spring-boot-starter` | RocketMQ |
| `springdoc-openapi-starter-webmvc-ui` | API 文档（可选） |

### rag/pom.xml — 实际依赖

| 依赖 | 用途 |
|------|------|
| `com.baomidou:mybatis-plus-boot-starter` | MyBatis-Plus |
| `mysql:mysql-connector-java` | MySQL 驱动 |
| `org.springframework.boot:spring-boot-starter-data-redis` | Redis |
| `org.springframework.boot:spring-boot-starter-validation` | 校验 |
| `software.amazon.awssdk:s3` | S3 SDK（MinIO / OSS / S3 通用） |
| `cn.dev33:sa-token-spring-boot3-starter` | Sa-Token |
| `org.apache.rocketmq:rocketmq-spring-boot-starter` | RocketMQ |

---

## 二、数据库设计

### 2.1 MySQL（关系型元数据）

**知识库表 `kb_knowledge_base`**
```sql
id, name, description, creator_id, status(ENABLED/DISABLED/DELETED),
created_time, updated_time, deleted
```

**文档表 `kb_document`**
```sql
id, kb_id, title, file_name, file_type, file_size, file_path,
parse_status(RAW/PARSING/PARSED/FAILED),
chunk_count, creator_id, created_time, updated_time, deleted
```

**文件上传记录表 `kb_upload_record`**
```sql
id, original_filename, stored_filename, file_size, file_md5, mime_type,
check_status(PASS/REJECTED),
reject_reason, uploader_id, created_time
```

**用户表（可选） `sys_user`**
```sql
id, username, password_hash, role, status, created_time
```

**会话/对话表（可选） `chat_session` / `chat_message`**
用于保存对话历史和上下文。

### 2.2 PostgreSQL（向量数据）

使用 Spring AI PGVector 自动管理 `vector_store` 表，存放：
- document / chunk 的 embedding 向量
- 关联的元数据（kb_id, doc_id, chunk_index, content 等）

### 2.3 Redis

- 缓存热点知识库查询结果
- 会话上下文缓存
- 文件上传去重（MD5 去重判断）

---

## 三、核心模块划分

### common 模块
```
common/
├── exception/
│   ├── BizException.java          # 业务异常基类
│   └── ErrorCode.java             # 错误码枚举
├── util/
│   └── FileTypeDetector.java      # 基于 Tika 的文件类型识别
└── config/
    └── RedisConfig.java            # Redis 配置
```

### rag 模块
```
rag/
├── config/
│   ├── DataSourceConfig.java           # MySQL 数据源配置
│   ├── RedisConfig.java                # Redis 配置
│   ├── VectorStoreConfig.java          # PGVector 向量存储配置
│   ├── AIConfig.java                   # DashScope Embedding + Chat 模型配置
│   ├── RocketMQConfig.java             # RocketMQ 配置
│   └── S3Config.java                   # AWS S3 / MinIO / OSS 客户端配置
├── model/
│   ├── entity/                          # MySQL 实体类 (MyBatis-Plus)
│   │   ├── KnowledgeBase.java
│   │   ├── Document.java
│   │   ├── UploadRecord.java
│   │   └── User.java
│   ├── dto/                             # 请求/响应 DTO
│   │   ├── KnowledgeBaseDTO.java
│   │   ├── DocumentDTO.java
│   │   ├── ChatRequest.java
│   │   ├── ChatResponse.java
│   │   └── UploadFileRequest.java
│   └── vo/                              # 视图对象
├── repository/                          # MyBatis-Plus Mapper
│   ├── KnowledgeBaseMapper.java
│   ├── DocumentMapper.java
│   └── UploadRecordMapper.java
├── service/
│   ├── KnowledgeBaseService.java        # 知识库 CRUD
│   ├── DocumentService.java             # 文档管理
│   ├── FileCheckService.java            # 文件合法性校验（Tika）
│   ├── FileParseService.java            # 文件解析 + 分块
│   ├── EmbeddingService.java            # 向量化 + 入库
│   ├── RetrievalService.java            # 检索逻辑
│   ├── ChatService.java                 # RAG 对话
│   ├── RedisService.java                # Redis 缓存操作
│   └── file/                            # 文件存储（策略模式）
│       ├── IFileStorageService.java     # 接口（参考 smart-admin）
│       ├── FileStorageLocalServiceImpl.java  # 本地磁盘实现
│       ├── FileStorageCloudServiceImpl.java   # S3/OSS 实现
│       └── FileStorageFactory.java      # 工厂，根据配置选择
├── strategy/                            # 文件解析策略
│   ├── FileParseStrategy.java           # 接口
│   ├── TikaParseStrategy.java           # Tika 通用解析
│   ├── PdfParseStrategy.java            # PDF 特殊处理
│   ├── DocxParseStrategy.java           # Word 处理
│   └── ParseStrategyFactory.java        # 工厂
├── chunk/                               # 文本分块
│   ├── TextSplitter.java
│   └── Chunk.java
├── consumer/                            # MQ 消费者
│   └── DocumentParseConsumer.java       # 文档解析消费
├── util/
│   ├── MD5Util.java                     # 文件 MD5
│   └── FileUtil.java
├── RagApplication.java
└── application.yaml
```

---

## 四、核心流程

### 4.1 文件上传与校验流程

```
用户上传文件
    │
    ▼
[1] 前端/网关层限制：大小、扩展名白名单
    │
    ▼
[2] FileCheckService 校验：
    - 文件名合法性（正则：不含特殊字符、路径穿越防护）
    - 文件大小上限（如 50MB）
    - 扩展名白名单（pdf/docx/txt/md/xlsx/pptx）
    - 文件 MD5 去重（Redis 查询是否已上传）
    - Tika 探测真实 MIME 类型（防止伪装）
    │
    ▼ 合法
[3] FileStorageFactory 根据配置选择存储方式：
    - file.storage.type=local → FileStorageLocalServiceImpl（本地磁盘）
    - file.storage.type=cloud → FileStorageCloudServiceImpl（AWS S3 / MinIO / OSS）
    - 接口 IFileStorageService.upload() 统一调用
    │
    ▼
[4] 记录上传记录（kb_upload_record）
    │
    ▼
[5] 发送解析消息到 RocketMQ
    - 消费者异步执行：Tika 解析 → 分块 → 向量化 → 入库 PGVector
    │
    ▼
[6] FileParseService 解析：
    - Tika 提取全文内容
    - TextSplitter 按 chunk_size/chunk_overlap 分块
    │
    ▼
[7] EmbeddingService 向量化：
    - 调用 DashScope embedding 接口
    - 向量 + 元数据存入 PGVector
    │
    ▼
[8] 更新 document 状态为 PARSED
```

### 4.2 文件存储策略（参考 smart-admin）

```
IFileStorageService (接口)
    ├── upload(MultipartFile, String path)     → FileUploadVO
    ├── getFileUrl(String fileKey)             → String
    ├── download(String fileKey)               → FileDownloadVO
    └── delete(String fileKey)                 → void

FileStorageFactory (工厂类，根据 file.storage.type 选择实现)
    ├── local → FileStorageLocalServiceImpl
    │   - 配置: file.storage.local.upload-path, url-prefix
    │   - 文件名: UUID + 时间戳 + 原始扩展名
    │   - 路径: upload-path + path + fileName
    │
    └── cloud → FileStorageCloudServiceImpl
        - 配置: file.storage.cloud.endpoint, bucket, access-key, secret-key, region
        - S3Client + S3Presigner (software.amazon.awssdk:s3)
        - 文件名: UUID + 时间戳 + 原始扩展名
        - 支持公有/私有对象（根据 path 区分）
```

### 4.3 RAG 对话流程

```
用户提问
    │
    ▼
[1] 问题向量化（Embedding）
    │
    ▼
[2] RetrievalService 检索 Top-K 相关 chunk
    - PGVector 向量相似度搜索
    - 可选：Redis 缓存热点查询
    │
    ▼
[3] 构建 Prompt：
    - 系统提示词（角色定义）
    - 检索到的相关文档片段
    - 用户问题
    │
    ▼
[4] ChatService 调用 LLM（DashScope qwen-max）
    - 流式返回（可选 SSE）
    │
    ▼
[5] 保存对话记录到 MySQL
```

---

## 五、补充建议（你未提到但实际需要开发的）

| 序号 | 模块/功能 | 说明 |
|------|-----------|------|
| 1 | **分块策略** | RecursiveCharacterTextSplitter，支持按段落/句子/固定长度切分，需配置 chunk_size(如 500) 和 chunk_overlap(如 50) |
| 2 | **Prompt 模板管理** | 系统提示词、检索增强提示词的模板化，支持自定义 |
| 3 | **检索重排序** | 可选 Cross-Encoder 重排序，提高检索精度 |
| 4 | **元数据过滤** | PGVector 支持 metadata filter，可按知识库、标签等过滤 |
| 5 | **鉴权模块** | 用户登录/注册（JWT），知识库权限控制 |
| 6 | **异步处理** | 文件解析和向量化耗时较长，需异步 + 进度查询 |
| 7 | **文件去重** | MD5 查重，避免重复入库 |
| 8 | **重试与幂等** | 向量化接口调用重试、解析失败重试机制 |
| 9 | **日志与监控** | 请求日志、解析耗时、向量化成功率等指标 |
| 10 | **配置管理** | chunk_size、top_k、temperature 等参数 externalized 到配置或数据库 |
| 11 | **知识库删除/重建** | 删除知识库时需级联删除向量 + 文档 + 元数据 |
| 12 | **敏感内容过滤** | 接入内容安全 API 或关键词过滤，防止上传非法内容 |

---

## 六、实现顺序建议

```
第一阶段：基础设施
  - 添加所有依赖，配置 application.yaml（MySQL/Redis/PGVector/AI/MQ/S3/SaToken）
  - 创建 MySQL 5 张表 SQL 脚本（kb_knowledge_base, kb_document, kb_upload_record, sys_user, chat_session/chat_message）
  - MyBatis-Plus 实体类 + Mapper
  - RedisConfig、S3Config、RocketMQConfig

第二阶段：知识库 + 文件上传
  - 知识库 CRUD（KnowledgeBaseService + Controller）
  - 文件上传接口（UploadController）
  - 文件校验（FileCheckService：文件名合法性 + MD5 + 扩展名白名单 + Tika MIME 探测）
  - 文件存储策略（IFileStorageService + LocalImpl + CloudImpl + Factory）
  - S3 文件上传
  - 发送 RocketMQ 消息触发异步解析

第三阶段：MQ 消费者 + 解析 + 向量化
  - RocketMQ 消费者（DocumentParseConsumer）
  - Tika 解析策略（TikaParseStrategy）
  - 文本分块（TextSplitter: chunk_size=500, overlap=50）
  - Embedding + 入库 PGVector（EmbeddingService）
  - 更新 document 状态

第四阶段：RAG 对话
  - 检索服务（RetrievalService：PGVector 向量相似度搜索）
  - Prompt 构建
  - Chat 接口（流式 + 非流式）
  - Sa-Token 鉴权接入

第五阶段：优化与扩展
  - 重排序、监控、敏感内容过滤等
```

---

## 五、application.yaml 配置（实际环境）

```yaml
server:
  port: 8080

spring:
  application:
    name: rag-service

  # MySQL 数据源
  datasource:
    rag:
      url: jdbc:mysql://127.0.0.1:3306/rag_v1?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
      username: root
      password: Y5TRj54n6ArXbMEB
      driver-class-name: com.mysql.cj.jdbc.Driver

  # Redis
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: RCtCZmzcCepnxbCW
      database: 0
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

  # PostgreSQL（PGVector 由 Spring AI 自动配置）
  # 需设置以下环境变量或 application.yaml：
  ai:
    vectorstore:
      pgvector:
        url: jdbc:postgresql://127.0.0.1:5432/rag_vector
        username: postgres
        password: postgres123456

  # RocketMQ
  rocketmq:
    name-server: 127.0.0.1:9876
    producer:
      group: rag-producer-group

  # 文件存储配置（策略模式切换）
  file:
    storage:
      type: local  # local | cloud
      local:
        upload-path: ./uploads/
        url-prefix: http://127.0.0.1:8080/upload/
      cloud:
        endpoint: http://127.0.0.1:9000       # MinIO / S3 endpoint
        bucket: rag-files
        access-key: <your-access-key>
        secret-key: <your-secret-key>
        region: cn-north-1
        public-url-prefix: http://127.0.0.1:9000/rag-files/
        path-style-access: true               # MinIO 需要开启

  # Sa-Token 配置
  sa-token:
    token-name: Authorization
    token-prefix: Bearer
    timeout: 2592000          # 30 天
    active-timeout: -1        # 不活跃不超时
    is-read-cookie: false
    is-read-header: true
    is-write-header: true
    is-store-http-only: true
    token-style: uuid
    jwt-secret-key: <your-secret-key>
    is-read-body: false

  # AI（DashScope）
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max
          temperature: 0.7
          top-p: 0.8
      embedding:
        options:
          model: text-embedding-v2

# RAG 业务配置
rag:
  chunk:
    size: 500           # 分块大小
    overlap: 50         # 重叠大小
  retrieval:
    top-k: 5            # 检索 Top-K
    similarity-threshold: 0.7   # 相似度阈值
  file:
    max-size: 52428800  # 50MB
    allowed-extensions: pdf,docx,txt,md,xlsx,pptx
  rocketmq:
    topic: rag-document-parse
    consumer-group: rag-parse-consumer
```

---

## 六、Redis 使用场景

| 场景 | Key 设计 | 过期时间 |
|------|----------|---------|
| 文件 MD5 去重 | `upload:md5:{md5}` → JSON(上传记录) | 7 天 |
| 会话上下文 | `chat:ctx:{sessionId}` → List<消息> | 30 分钟 |
| 热点检索缓存 | `rag:search:{hash(question)}` → 检索结果 | 5 分钟 |
| 解析任务进度 | `rag:parse:{docId}` → 状态百分比 | 24 小时 |
| Sa-Token Session | `sa:session:{token}` | 配置项 |
| S3 私有文件 URL | `file:url:{fileKey}` → 预签名 URL | 按 cloud-private-url-expire 配置 |

---

## 七、技术选型

| 问题 | 选型 | 理由 |
|------|------|------|
| MySQL ORM | **MyBatis-Plus** | 轻量灵活，适合学习 |
| 文件存储 | **AWS S3 SDK 策略模式** | 用户指定，`file.storage.type` 切换 local/cloud |
| 异步 | **RocketMQ** | 用户指定，文件解析和向量化通过 MQ 异步解耦 |
| 鉴权 | **Sa-Token** | 用户指定，轻量无侵入 |
| 向量模型 | DashScope text-embedding-v2 | 已配置 |
| 文件解析 | Apache Tika | 已声明，支持多格式 |

### 依赖补充明细

**parent/pom.xml 新增 dependencyManagement**
| 依赖 | 用途 |
|------|------|
| `com.baomidou:mybatis-plus-boot-starter` | MyBatis-Plus starter |
| `org.springframework.boot:spring-boot-starter-data-redis` | Redis |
| `mysql:mysql-connector-java` | MySQL JDBC 驱动 |
| `org.apache.commons:commons-io` | 文件工具 |
| `org.apache.commons:commons-lang3` | 字符串工具 |
| `org.springframework.boot:spring-boot-starter-validation` | JSR-380 校验 |
| `software.amazon.awssdk:s3` | AWS S3 SDK（支持 MinIO / OSS / S3） |
| `cn.dev33:sa-token-spring-boot3-starter` | Sa-Token 鉴权 |
| `org.apache.rocketmq:rocketmq-spring-boot-starter` | RocketMQ |
| `springdoc-openapi-starter-webmvc-ui` | API 文档（可选） |

**rag/pom.xml 新增实际依赖**
| 依赖 | 用途 |
|------|------|
| `com.baomidou:mybatis-plus-boot-starter` | MyBatis-Plus |
| `mysql:mysql-connector-java` | MySQL 驱动 |
| `org.springframework.boot:spring-boot-starter-data-redis` | Redis |
| `org.springframework.boot:spring-boot-starter-validation` | 校验 |
| `software.amazon.awssdk:s3` | S3 SDK（MinIO / OSS / S3 通用） |
| `cn.dev33:sa-token-spring-boot3-starter` | Sa-Token |
| `org.apache.rocketmq:rocketmq-spring-boot-starter` | RocketMQ |

---

## Critical Files

- `pom.xml` / `rag/pom.xml` — 依赖管理
- `common/pom.xml` — 共享模块
- `application.yaml` — 数据源、Redis、AI、MQ、S3、Sa-Token 配置
- `rag/src/main/resources/` — 需新增 SQL 脚本目录
