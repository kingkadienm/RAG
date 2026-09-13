# RAG MySQL 数据库实现总览

> 目标：MySQL 只负责 RAG 的业务数据，不负责向量存储。
>
> PostgreSQL + pgvector 负责：
>
> * Chunk
> * Embedding
> * HNSW
> * 向量检索
>
> MySQL 负责：
>
> * 用户
> * 知识库
> * 文档
> * 文件
> * 文档处理状态
> * 文档版本
> * 系统配置
> * 会话 / 消息

---

# 一、最终数据库架构

```text
                    MySQL
                      │
        ┌─────────────┼─────────────┐
        │             │             │
        ▼             ▼             ▼
   sys_user    kb_knowledge_base   sys_config
                      │
                      ▼
                 kb_document
                      │
                      │ doc_id
                      ▼
            PostgreSQL + pgvector
                      │
                      ▼
                document_chunk
                      │
                      ▼
                   HNSW
```

核心关系：

```text
用户
 │
 └── 知识库
       │
       └── 文档
             │
             └── Chunk
                    │
                    └── Embedding
```

其中：

```text
用户 / 知识库 / 文档
        ↓
      MySQL
```

而：

```text
Chunk / Embedding
        ↓
PostgreSQL
```

---

# 二、MySQL 表职责

最终保留：

```text
MySQL
│
├── sys_user
│
├── kb_knowledge_base
│
├── kb_document
│
├── kb_upload_record
│
├── chat_session
│
├── chat_message
│
└── sys_config
```

删除 / 废弃：

```text
kb_document_chunk
```

因为它属于 PostgreSQL 向量库。

---

# 三、Step 1：用户表

当前：

```text
sys_user
```

职责：

```text
用户身份
用户角色
用户状态
```

当前结构已经够用：

```sql
CREATE TABLE sys_user (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    username     VARCHAR(64) NOT NULL,
    password     VARCHAR(256) NOT NULL,
    nickname     VARCHAR(64),
    role         TINYINT NOT NULL DEFAULT 2,
    status       TINYINT NOT NULL DEFAULT 1,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                 ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
);
```

第一阶段不用改。

---

# 四、Step 2：知识库表

核心表：

```text
kb_knowledge_base
```

职责：

```text
一个知识库 = 一组文档的逻辑集合
```

例如：

```text
知识库 1
├── IT支持.md
├── VPN使用说明.pdf
├── 公司网络规范.docx
└── Windows故障处理.md
```

推荐结构：

```sql
CREATE TABLE kb_knowledge_base (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    name         VARCHAR(128) NOT NULL,
    description  TEXT,
    creator_id   BIGINT NOT NULL DEFAULT 0,

    status       TINYINT NOT NULL DEFAULT 1
                 COMMENT '1-启用 2-禁用',

    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                 ON UPDATE CURRENT_TIMESTAMP,

    deleted      TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    INDEX idx_creator_id (creator_id),
    INDEX idx_status_deleted (status, deleted)
);
```

增加：

```text
idx_status_deleted
```

方便查询：

```sql
WHERE status = 1
AND deleted = 0
```

---

# 五、Step 3：文档表

这是 MySQL 最核心的一张表。

```text
kb_document
```

职责：

> 描述“这个文件是什么，以及当前处理到哪一步”。

---

## 推荐结构

```sql
CREATE TABLE kb_document (
    id            BIGINT NOT NULL AUTO_INCREMENT,

    kb_id         BIGINT NOT NULL,

    title         VARCHAR(256),

    file_name     VARCHAR(256) NOT NULL,

    file_type     VARCHAR(32),

    file_size     BIGINT NOT NULL DEFAULT 0,

    file_path     VARCHAR(512),

    file_md5      CHAR(32) NOT NULL,

    parse_status  TINYINT NOT NULL DEFAULT 0,

    vector_status TINYINT NOT NULL DEFAULT 0,

    chunk_count   INT NOT NULL DEFAULT 0,

    vector_count  INT NOT NULL DEFAULT 0,

    error_msg     VARCHAR(1024),

    version       INT NOT NULL DEFAULT 1,

    creator_id    BIGINT NOT NULL DEFAULT 0,

    created_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                  ON UPDATE CURRENT_TIMESTAMP,

    deleted       TINYINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    INDEX idx_kb_id (kb_id),

    INDEX idx_kb_status
        (kb_id, parse_status, vector_status),

    INDEX idx_file_md5 (file_md5),

    INDEX idx_creator_id (creator_id)
);
```

---

# 六、文档状态

不要把所有状态塞成一个字段。

你现在：

```text
parse_status
vector_status
```

这个方向是对的。

---

## parse_status

```text
0 = 待解析
1 = 解析中
2 = 解析成功
3 = 解析失败
```

流程：

```text
上传
 ↓
待解析
 ↓
解析中
 ↓
解析成功
```

失败：

```text
解析中
 ↓
解析失败
```

---

# 七、vector_status

```text
0 = 待向量化
1 = 向量化中
2 = 向量化完成
3 = 向量化失败
```

流程：

```text
解析成功
    ↓
待向量化
    ↓
向量化中
    ↓
向量化完成
```

---

# 八、不要把 Chunk 状态放 MySQL

以前：

```text
kb_document
     │
     └── kb_document_chunk
```

现在：

```text
kb_document
     │
     │ doc_id
     ▼
PostgreSQL
     │
     └── document_chunk
```

MySQL 只保存：

```text
chunk_count
vector_count
vector_status
```

例如：

```text
kb_document

chunk_count  = 100
vector_count = 100
vector_status = 2
```

表示：

```text
文档被切成 100 个 Chunk
已经成功生成 100 个向量
向量化完成
```

---

# 九、Step 4：文件上传记录

保留：

```text
kb_upload_record
```

职责：

> 记录“用户上传过什么文件”。

它和 `kb_document` 不是一回事。

---

## kb_upload_record

```text
用户上传
    ↓
UploadRecord
    ↓
文件校验
    ↓
Document
```

推荐：

```sql
CREATE TABLE kb_upload_record (
    id                BIGINT NOT NULL AUTO_INCREMENT,

    kb_id             BIGINT NOT NULL,

    doc_id            BIGINT,

    original_filename VARCHAR(256) NOT NULL,

    stored_filename   VARCHAR(256) NOT NULL,

    file_size         BIGINT NOT NULL DEFAULT 0,

    file_md5          CHAR(32) NOT NULL,

    mime_type         VARCHAR(128),

    storage_type      VARCHAR(16) NOT NULL DEFAULT 'local',

    check_status      TINYINT NOT NULL DEFAULT 1,

    reject_reason     VARCHAR(512),

    uploader_id       BIGINT NOT NULL DEFAULT 0,

    deleted           TINYINT NOT NULL DEFAULT 0,

    created_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    INDEX idx_kb_id (kb_id),

    INDEX idx_doc_id (doc_id),

    INDEX idx_uploader_id (uploader_id),

    INDEX idx_md5_kb (file_md5, kb_id)
);
```

---

# 十、文件重复上传策略

核心规则：

```text
kb_id + file_md5
```

判断重复。

例如：

```text
知识库 2
文件 A
MD5 = ABC123
```

再次上传：

```text
知识库 2
文件 A
MD5 = ABC123
```

认为重复。

但：

```text
知识库 3
文件 A
MD5 = ABC123
```

可以允许。

因此：

```sql
UNIQUE KEY uk_md5_kb (file_md5, kb_id)
```

这个设计保留。

---

# 十一、Step 5：文档版本

你现在：

```text
version
```

已经存在。

例如：

```text
document id = 10

version = 1
```

用户重新上传同一个逻辑文档：

```text
version = 2
```

但是这里需要明确：

> `version` 是“文档处理版本”，不是数据库记录版本。

---

# 十二、推荐的版本策略

第一阶段：

```text
一个 kb_document
+
version
```

即可。

例如：

```text
id = 10
version = 1
```

重新解析：

```text
id = 10
version = 2
```

PostgreSQL：

```text
doc_id = 10
version = 2
```

旧 Chunk 删除：

```sql
DELETE FROM document_chunk
WHERE doc_id = 10;
```

然后重新生成。

---

# 十三、Step 6：删除文档

删除流程：

```text
用户删除文档
       ↓
MySQL
kb_document.deleted = 1
       ↓
PostgreSQL
DELETE FROM document_chunk
WHERE doc_id = ?
```

不要只删除 MySQL。

否则：

```text
MySQL
文档不存在

PostgreSQL
向量还存在
```

最终 RAG 仍然可能检索到已经删除的内容。

---

# 十四、Step 7：删除知识库

流程：

```text
删除知识库
      ↓
MySQL
kb_knowledge_base.deleted = 1
      ↓
查询所有 doc_id
      ↓
PostgreSQL
删除对应 Chunk
```

例如：

```sql
DELETE FROM document_chunk
WHERE kb_id = ?;
```

---

# 十五、Step 8：会话

保留：

```text
chat_session
chat_message
```

关系：

```text
chat_session
      │
      └── chat_message
```

当前设计基本可以继续使用。

---

# 十六、chat_session

职责：

```text
一次聊天会话
```

核心：

```text
session_id
user_id
kb_id
title
message_count
```

其中：

```text
kb_id
```

表示：

> 当前会话默认使用哪个知识库。

---

# 十七、chat_message

职责：

```text
一条用户 / AI 消息
```

你当前：

```text
ref_chunks JSON
```

这个设计可以保留。

例如：

```json
[
  {
    "docId": 2,
    "chunkIndex": 17,
    "score": 0.87
  }
]
```

它是：

> 对话历史里的“引用记录”。

注意：

**它不是向量库。**

真正的 Chunk 仍然在 PostgreSQL。

---

# 十八、Step 9：系统配置

保留：

```text
sys_config
```

你现在这个设计可以继续。

例如：

```text
rag.chunk.size
rag.chunk.overlap

rag.retrieval.top-k
rag.retrieval.similarity-threshold

rag.file.max-size
rag.file.allowed-extensions

rag.retry.max-attempts
rag.retry.interval-minutes
```

---

# 十九、但是 Chunk 配置建议调整

你现在：

```text
rag.chunk.size = 500
rag.chunk.overlap = 50
```

只是默认配置。

后面你做：

```text
Markdown
PDF
DOCX
TXT
Excel
PPTX
```

不应该所有文件强制使用同一种切片策略。

最终应该：

```text
文件类型
   ↓
ChunkingStrategy
   │
   ├── MarkdownChunkingStrategy
   ├── PdfChunkingStrategy
   ├── DocxChunkingStrategy
   ├── TxtChunkingStrategy
   ├── ExcelChunkingStrategy
   └── PptxChunkingStrategy
```

这是后面的 RAG 切片阶段再实现。

---

# 二十、最终 MySQL ER 关系

```text
sys_user
   │
   ├──────────────┐
   │              │
   ▼              ▼
knowledge_base   chat_session
   │              │
   │              └── chat_message
   │
   ▼
kb_document
   │
   └───────────────→ PostgreSQL document_chunk
```

文件上传：

```text
sys_user
   │
   ▼
kb_upload_record
   │
   ▼
kb_document
```

---

# 二十一、MySQL 和 PostgreSQL 的边界

这是整个项目最重要的一条边界。

## MySQL

```text
knowledge_base
document
upload_record
user
session
message
config
```

负责：

```text
业务
状态
权限
版本
生命周期
```

---

## PostgreSQL

```text
document_chunk
```

负责：

```text
文本
向量
HNSW
相似度
检索
```

---

# 二十二、不要在 MySQL 存 embedding

不要：

```text
kb_document_chunk
├── content
├── embedding
└── ...
```

也不要：

```text
embedding BLOB
```

或者：

```text
embedding JSON
```

向量统一：

```text
PostgreSQL
+
pgvector
```

---

# 二十三、最终文档生命周期

```text
上传文件
    ↓
kb_upload_record
    ↓
文件校验
    ↓
kb_document
    ↓
parse_status = 0
    ↓
RocketMQ
    ↓
parse_status = 1
    ↓
文件解析
    ↓
parse_status = 2
    ↓
语义切片
    ↓
Chunk
    ↓
Embedding
    ↓
PostgreSQL
    ↓
vector_status = 2
```

---

# 二十四、失败处理

解析失败：

```text
parse_status = 3
error_msg = ...
```

向量失败：

```text
vector_status = 3
error_msg = ...
```

重试：

```text
3
 ↓
重新发送 RocketMQ
 ↓
重新处理
```

不要重新创建一条 document。

---

# 二十五、文档删除

```text
用户删除
   ↓
kb_document.deleted = 1
   ↓
删除 PostgreSQL Chunk
   ↓
完成
```

---

# 二十六、文档重新向量化

如果只是 Embedding 模型发生变化：

```text
MySQL document
        │
        │ 不需要重新上传文件
        ▼
读取原始文件
        ↓
重新解析
        ↓
重新切片
        ↓
重新 Embedding
        ↓
PostgreSQL
```

或者以后进一步优化成：

```text
旧 Chunk
   ↓
保留 content
   ↓
重新生成 embedding
```

---

# 二十七、第一阶段：数据库重构

现在只做：

```text
Step 1
sys_user
       ↓
Step 2
kb_knowledge_base
       ↓
Step 3
kb_document
       ↓
Step 4
kb_upload_record
       ↓
Step 5
删除 kb_document_chunk
       ↓
Step 6
检查 chat_session
       ↓
Step 7
检查 chat_message
       ↓
Step 8
检查 sys_config
```

---

# 二十八、第二阶段：Java Entity

对应：

```text
model/entity
│
├── SysUser
├── KnowledgeBase
├── Document
├── UploadRecord
├── ChatSession
├── ChatMessage
└── SysConfig
```

注意：

```text
DocumentChunk
```

不要再放 MySQL Entity。

它应该属于：

```text
model/vector/DocumentChunk
```

对应 PostgreSQL。

---

# 二十九、第三阶段：MySQL Service

```text
KnowledgeBaseService
        ↓
管理知识库

DocumentService
        ↓
管理文档

UploadService
        ↓
管理文件上传

ChatSessionService
        ↓
管理会话

ChatMessageService
        ↓
管理消息
```

---

# 三十、第四阶段：文档处理状态

完整状态：

```text
上传
 ↓
UploadRecord
 ↓
Document
 ↓
待解析
 ↓
解析中
 ↓
解析成功
 ↓
待向量化
 ↓
向量化中
 ↓
向量化完成
```

---

# 三十一、第五阶段：和 RocketMQ 对接

消息只负责：

> 通知“哪个文档需要处理”。

消息：

```json
{
  "docId": 2,
  "kbId": 2
}
```

消费者：

```text
docId
 ↓
MySQL 查询 Document
 ↓
读取文件
 ↓
Parser
 ↓
Chunking
 ↓
Embedding
 ↓
PostgreSQL
 ↓
更新 MySQL 状态
```

不要把完整 Chunk 文本塞进 RocketMQ。

---

# 三十二、第六阶段：和 PostgreSQL 对接

最终：

```text
DocumentService
       ↓
RocketMQ
       ↓
DocumentParseConsumer
       ↓
ChunkingService
       ↓
EmbeddingService
       ↓
DocumentChunkRepository
       ↓
PostgreSQL
```

---

# 三十三、最终项目结构

```text
com.wangzs.rag
│
├── controller
│
├── service
│   ├── KnowledgeBaseService
│   ├── DocumentService
│   ├── UploadService
│   ├── ChunkingService
│   ├── EmbeddingService
│   └── VectorSearchService
│
├── mapper
│   ├── KnowledgeBaseMapper
│   ├── DocumentMapper
│   ├── UploadRecordMapper
│   ├── ChatSessionMapper
│   ├── ChatMessageMapper
│   └── SysConfigMapper
│
├── repository
│   └── DocumentChunkRepository
│
├── model
│   ├── entity
│   │   ├── KnowledgeBase
│   │   ├── Document
│   │   ├── UploadRecord
│   │   ├── ChatSession
│   │   ├── ChatMessage
│   │   └── SysConfig
│   │
│   └── vector
│       └── DocumentChunk
│
└── mq
    └── DocumentParseConsumer
```

---

# 三十四、现在你的实现顺序

不要再同时改十几个地方。

严格按照：

```text
★★★★★ 1. MySQL 表结构确定
          ↓
★★★★★ 2. 删除 MySQL kb_document_chunk
          ↓
★★★★★ 3. PostgreSQL document_chunk
          ↓
★★★★★ 4. HNSW
          ↓
★★★★★ 5. Java Entity
          ↓
★★★★★ 6. Mapper / Repository
          ↓
★★★★★ 7. 单条 Chunk 保存
          ↓
★★★★★ 8. 单条向量检索
          ↓
★★★★★ 9. 文档解析流程
          ↓
★★★★★ 10. Chunking
          ↓
★★★★★ 11. Batch Embedding
          ↓
★★★★★ 12. Batch Insert
          ↓
★★★★★ 13. RocketMQ
          ↓
★★★★★ 14. 文档状态管理
          ↓
★★★★★ 15. 删除 / 重建
          ↓
★★★★☆ 16. Rerank
          ↓
★★★★☆ 17. 混合检索
```

---

# 三十五、你当前 SQL 最重要的调整

你现在最需要做的其实只有三件事：

```text
① 保留
kb_knowledge_base
kb_document
kb_upload_record
chat_session
chat_message
sys_user
sys_config

② 废弃
kb_document_chunk

③ 新增
PostgreSQL.document_chunk
```

最终形成：

```text
                  MySQL
                    │
       ┌────────────┴────────────┐
       │                         │
知识库 / 文档 / 文件         会话 / 用户
       │
       │ doc_id
       ▼
              PostgreSQL
                   │
             document_chunk
                   │
              embedding
                   │
                 HNSW
                   │
                检索
```

**这就是你现在最应该确定的数据库边界。**

后面你每实现一个模块，都只问自己一句：

> “这个数据是业务数据，还是检索数据？”

业务数据 → **MySQL**

向量检索数据 → **PostgreSQL**

这样你后面就不会再出现 `kb_document_chunk` 到底应该放 MySQL 还是 PostgreSQL、metadata 应该放哪里、VectorStore 应该管什么这种反复纠结。
