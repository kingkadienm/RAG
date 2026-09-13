# RAG 向量库实现总览

> 目标：构建一套适合当前 RAG 项目的独立向量存储体系。
>
> 技术栈：
>
> * Java 21
> * Spring Boot
> * Spring AI / Spring AI Alibaba
> * PostgreSQL
> * pgvector
> * MySQL：业务数据
> * RocketMQ：文档异步处理
> * Embedding：DashScope `text-embedding-v2`
> * 向量维度：1536
> * 相似度：Cosine
> * 向量索引：HNSW

---

# 一、最终目标

最终实现：

```text
                    用户上传文件
                         │
                         ▼
                  ┌──────────────┐
                  │    MySQL     │
                  │              │
                  │ knowledge_base│
                  │ document     │
                  └──────┬───────┘
                         │
                    RocketMQ
                         │
                         ▼
                文档解析 / 切片
                         │
                         ▼
                 Chunk 列表
                         │
                         ▼
                 Embedding 模型
                         │
                         ▼
              PostgreSQL + pgvector
                         │
                         ▼
                       HNSW
                         │
                         ▼
                    向量检索
                         │
                         ▼
                      Rerank
                         │
                         ▼
                     Top N Context
                         │
                         ▼
                        LLM
```

---

# 二、数据库职责划分

## 2.1 MySQL

负责业务数据：

```text
MySQL
│
├── knowledge_base
│
├── document
│
├── user
│
└── 其他业务表
```

MySQL 负责：

* 知识库
* 文档
* 用户
* 文件状态
* 文档版本
* 上传记录
* 业务权限

---

# 三、PostgreSQL + pgvector

PostgreSQL 专门负责：

```text
向量检索数据
```

核心表：

```text
document_chunk
```

负责：

* Chunk 内容
* Chunk 与知识库的关系
* Chunk 与文档的关系
* Embedding
* 向量检索
* 检索元数据

---

# 四、核心表设计

## 4.1 document_chunk

第一版直接采用：

```sql
CREATE TABLE document_chunk (
    id              BIGSERIAL PRIMARY KEY,

    -- =========================
    -- 业务关系
    -- =========================
    kb_id           BIGINT NOT NULL,
    doc_id          BIGINT NOT NULL,

    -- =========================
    -- Chunk 信息
    -- =========================
    chunk_index     INT NOT NULL,
    chunk_total     INT NOT NULL DEFAULT 0,

    -- =========================
    -- Chunk 内容
    -- =========================
    content         TEXT NOT NULL,

    -- =========================
    -- Chunk 内容 Hash
    -- =========================
    content_hash    VARCHAR(64),

    -- =========================
    -- 向量
    -- =========================
    embedding       VECTOR(1536) NOT NULL,

    -- =========================
    -- Embedding 模型
    -- =========================
    embedding_model VARCHAR(100) NOT NULL DEFAULT 'text-embedding-v2',

    -- =========================
    -- 扩展元数据
    -- =========================
    metadata        JSONB,

    -- =========================
    -- 创建时间
    -- =========================
    created_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

# 五、字段职责

## id

Chunk 唯一 ID。

```text
1
2
3
...
```

使用 PostgreSQL 自增 ID 即可。

---

## kb_id

知识库 ID。

```text
kb_id = 2
```

代表：

```text
这个 Chunk 属于知识库 2
```

这是非常重要的检索过滤条件。

---

## doc_id

文档 ID。

```text
doc_id = 10
```

代表：

```text
这个 Chunk 属于 document 10
```

用于：

* 删除文档
* 重建文档
* 文档版本
* 查询某个文档的所有 Chunk

---

## chunk_index

Chunk 顺序。

例如：

```text
chunk_index
0
1
2
3
...
17
```

用于恢复原始顺序。

---

## chunk_total

整个文档 Chunk 数量。

例如：

```text
chunk_index = 17
chunk_total = 18
```

表示：

```text
这是第 18 个 Chunk
整个文档一共有 18 个 Chunk
```

---

## content

真正发送给 LLM 的文本。

例如：

```text
拼多多开票信息：

杭州拼多多科技有限公司
纳税资质：一般纳税人
纳税人识别号：91330108MA1P2Q3R
```

---

## content_hash

Chunk 内容 Hash。

作用：

```text
判断 Chunk 内容有没有发生变化
```

注意：

```text
文件 MD5
    ↓
判断整个文件是否重复

content_hash
    ↓
判断 Chunk 内容是否变化
```

两者职责不同。

---

## embedding

真正的向量：

```text
VECTOR(1536)
```

例如：

```text
[0.0123, -0.0382, ...]
```

用于相似度搜索。

---

## embedding_model

记录向量由哪个模型生成。

例如：

```text
text-embedding-v2
```

以后如果更换模型：

```text
text-embedding-v3
```

可以区分不同版本的向量。

---

## metadata

只存扩展信息。

例如：

```json
{
  "file_name": "IT支持.md",
  "file_type": "md",
  "title": "IT支持.md",
  "version": 1,
  "creator_id": 1
}
```

原则：

> 核心查询字段不要全部塞进 metadata。

---

# 六、索引设计

## 6.1 HNSW 向量索引

```sql
CREATE INDEX idx_chunk_embedding_hnsw
ON document_chunk
USING hnsw (embedding vector_cosine_ops);
```

作用：

```text
快速进行向量近似最近邻搜索
```

没有 HNSW：

```text
大量向量
    ↓
逐个计算距离
    ↓
排序
    ↓
Top K
```

有 HNSW：

```text
大量向量
    ↓
HNSW 图搜索
    ↓
快速找到候选
    ↓
Top K
```

---

# 七、业务过滤索引

## 7.1 kb_id

```sql
CREATE INDEX idx_chunk_kb_id
ON document_chunk (kb_id);
```

用于：

```sql
WHERE kb_id = ?
```

---

## 7.2 doc_id

```sql
CREATE INDEX idx_chunk_doc_id
ON document_chunk (doc_id);
```

用于：

```sql
WHERE doc_id = ?
```

---

## 7.3 kb_id + doc_id

```sql
CREATE INDEX idx_chunk_kb_doc
ON document_chunk (kb_id, doc_id);
```

用于：

```sql
WHERE kb_id = ?
  AND doc_id = ?
```

---

# 八、metadata 索引

如果以后需要：

```sql
WHERE metadata @> ...
```

再创建：

```sql
CREATE INDEX idx_chunk_metadata
ON document_chunk
USING gin (metadata);
```

第一阶段不是必须。

---

# 九、第一阶段：建立 pgvector

## Step 1：确认 PostgreSQL

确认：

```text
PostgreSQL
+
pgvector
```

---

## Step 2：创建 Extension

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

检查：

```sql
SELECT extname
FROM pg_extension
WHERE extname = 'vector';
```

---

# 十、第二阶段：创建 document_chunk

## Step 3：创建表

执行：

```sql
CREATE TABLE document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    kb_id           BIGINT NOT NULL,
    doc_id          BIGINT NOT NULL,
    chunk_index     INT NOT NULL,
    chunk_total     INT NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    content_hash    VARCHAR(64),
    embedding       VECTOR(1536) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL DEFAULT 'text-embedding-v2',
    metadata        JSONB,
    created_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

# 十一、第三阶段：建立索引

## Step 4：HNSW

```sql
CREATE INDEX idx_chunk_embedding_hnsw
ON document_chunk
USING hnsw (embedding vector_cosine_ops);
```

---

## Step 5：业务索引

```sql
CREATE INDEX idx_chunk_kb_id
ON document_chunk (kb_id);

CREATE INDEX idx_chunk_doc_id
ON document_chunk (doc_id);

CREATE INDEX idx_chunk_kb_doc
ON document_chunk (kb_id, doc_id);
```

---

# 十二、第四阶段：Java Entity

创建：

```text
com.wangzs.rag
└── model
    └── vector
        └── DocumentChunk.java
```

对应：

```text
document_chunk
```

注意：

> PostgreSQL 的 Entity 不需要和 MySQL 的 Document Entity 混在一起。

---

# 十三、第五阶段：VectorRepository

创建独立 Repository：

```text
vector
└── DocumentChunkRepository
```

职责只有：

```text
保存 Chunk
查询 Chunk
删除 Chunk
向量搜索
```

不要让 Controller 直接操作 PostgreSQL。

---

# 十四、第六阶段：EmbeddingService

建立：

```text
EmbeddingService
```

职责：

```text
文本
 ↓
Embedding 模型
 ↓
向量
```

例如：

```text
String
   ↓
Embedding
   ↓
List<Float>
```

---

# 十五、第七阶段：批量 Embedding

不要：

```text
Chunk 1
 ↓
Embedding
 ↓
INSERT

Chunk 2
 ↓
Embedding
 ↓
INSERT

Chunk 3
 ↓
Embedding
 ↓
INSERT
```

应该：

```text
100 chunks
    ↓
Batch Embedding
    ↓
100 vectors
    ↓
Batch INSERT
```

这是文档向量化性能优化的关键。

---

# 十六、第八阶段：Chunk → Vector

完整流程：

```text
Document
    ↓
Parser
    ↓
Document Text
    ↓
ChunkingService
    ↓
List<Chunk>
    ↓
EmbeddingService
    ↓
List<Vector>
    ↓
DocumentChunk
    ↓
Batch Insert
```

---

# 十七、第九阶段：向量检索

用户问题：

```text
电脑无法连接公司 WiFi 怎么办？
```

首先：

```text
问题
 ↓
Embedding
 ↓
Query Vector
```

然后：

```sql
SELECT
    id,
    kb_id,
    doc_id,
    chunk_index,
    content,
    metadata,
    1 - (embedding <=> :query_embedding) AS score
FROM document_chunk
WHERE kb_id = :kb_id
ORDER BY embedding <=> :query_embedding
LIMIT :top_k;
```

---

# 十八、检索流程

```text
用户问题
    ↓
Query Embedding
    ↓
kb_id 过滤
    ↓
HNSW
    ↓
Top K
    ↓
候选 Chunk
```

例如：

```text
Top K = 20
```

得到：

```text
Chunk 17   score=0.91
Chunk 23   score=0.88
Chunk 45   score=0.86
...
Chunk 72   score=0.71
```

---

# 十九、第十阶段：Rerank

不要直接：

```text
向量 Top 5
 ↓
LLM
```

推荐：

```text
向量检索 Top 20
        ↓
      Rerank
        ↓
      Top 5
        ↓
       LLM
```

原因：

```text
Embedding
```

负责：

> 快速找到可能相关的内容。

```text
Reranker
```

负责：

> 更精确地判断问题和 Chunk 是否真正相关。

---

# 二十、第十一阶段：删除文档

删除某个文档：

```sql
DELETE FROM document_chunk
WHERE doc_id = :doc_id;
```

因为：

```text
doc_id
```

是独立字段，所以非常简单。

---

# 二十一、第十二阶段：删除知识库

删除知识库：

```sql
DELETE FROM document_chunk
WHERE kb_id = :kb_id;
```

---

# 二十二、第十三阶段：重新向量化

当 Embedding 模型变化：

```text
text-embedding-v2
        ↓
text-embedding-v3
```

可以根据：

```sql
WHERE embedding_model = 'text-embedding-v2'
```

找到旧向量。

然后重新生成。

---

# 二十三、第十四阶段：重复文件

文件上传：

```text
File
 ↓
MD5
 ↓
查询 MySQL document.file_md5
```

如果存在：

```text
同知识库
+
相同 MD5
```

则：

```text
拒绝重复上传
```

或者：

```text
创建新版本
```

具体由业务层决定。

---

# 二十四、第十五阶段：增量更新

以后可以做到：

```text
原文档
 ↓
重新解析
 ↓
重新切片
 ↓
计算 Chunk Hash
 ↓
比较旧 Chunk
```

结果：

```text
Chunk A 没变化
    ↓
复用旧向量

Chunk B 变化
    ↓
重新 Embedding

Chunk C 新增
    ↓
Embedding
```

这样就不用每次整个文档重新向量化。

---

# 二十五、推荐 Java 分层

最终：

```text
com.wangzs.rag
│
├── controller
│
├── service
│   ├── DocumentService
│   ├── ChunkingService
│   ├── EmbeddingService
│   ├── VectorSearchService
│   └── RerankService
│
├── repository
│   └── DocumentChunkRepository
│
├── model
│   └── vector
│       └── DocumentChunk
│
└── mq
    └── DocumentParseConsumer
```

职责：

```text
DocumentService
    ↓
管理文档

ChunkingService
    ↓
负责切片

EmbeddingService
    ↓
负责向量化

DocumentChunkRepository
    ↓
负责 PostgreSQL

VectorSearchService
    ↓
负责向量检索

RerankService
    ↓
负责重排序

DocumentParseConsumer
    ↓
负责异步任务
```

---

# 二十六、不要一次全部实现

严格按照下面顺序：

```text
Step 1
  ↓
确认 pgvector
  ↓
Step 2
  ↓
创建 document_chunk
  ↓
Step 3
  ↓
创建 HNSW + 业务索引
  ↓
Step 4
  ↓
Java Entity
  ↓
Step 5
  ↓
Repository
  ↓
Step 6
  ↓
单条 Vector 插入
  ↓
Step 7
  ↓
单条向量检索
  ↓
Step 8
  ↓
Batch Embedding
  ↓
Step 9
  ↓
Batch Insert
  ↓
Step 10
  ↓
kb_id 过滤检索
  ↓
Step 11
  ↓
删除文档
  ↓
Step 12
  ↓
重新向量化
  ↓
Step 13
  ↓
Rerank
  ↓
Step 14
  ↓
接入完整 RAG
```

---

# 二十七、第一阶段验收标准

完成 Step 1～3 后，只检查数据库。

必须能够：

```text
1. PostgreSQL 有 vector extension

2. document_chunk 创建成功

3. embedding 是 VECTOR(1536)

4. HNSW 创建成功

5. kb_id 索引创建成功

6. doc_id 索引创建成功
```

然后插入一条测试数据：

```text
kb_id = 1
doc_id = 1
chunk_index = 0
content = "测试文本"
embedding = 1536维向量
```

能够查询出来。

---

# 二十八、第二阶段验收标准

完成：

```text
Entity
+
Repository
+
EmbeddingService
```

实现：

```text
文本
 ↓
Embedding
 ↓
DocumentChunk
 ↓
PostgreSQL
```

能够成功保存。

---

# 二十九、第三阶段验收标准

实现：

```text
问题
 ↓
Embedding
 ↓
HNSW
 ↓
Top K
```

例如：

```text
问题：
什么是公司 WiFi？

返回：

Chunk A score=0.91
Chunk B score=0.87
Chunk C score=0.83
```

---

# 三十、最终 RAG

最终完整链路：

```text
                    ┌─────────────┐
                    │    用户     │
                    └──────┬──────┘
                           │
                           ▼
                        问题
                           │
                           ▼
                    Query Embedding
                           │
                           ▼
                ┌─────────────────────┐
                │ PostgreSQL pgvector │
                │                     │
                │ kb_id Filter        │
                │        ↓            │
                │      HNSW           │
                │        ↓            │
                │      Top 20         │
                └─────────┬───────────┘
                          │
                          ▼
                       Rerank
                          │
                          ▼
                        Top 5
                          │
                          ▼
                     Context
                          │
                          ▼
                         LLM
                          │
                          ▼
                         SSE
                          │
                          ▼
                        用户
```

---

# 三十一、核心原则

整个向量库只记住这 7 条：

### 1. MySQL 管业务

```text
知识库
文档
用户
版本
状态
```

### 2. PostgreSQL 管检索

```text
Chunk
Embedding
Metadata
```

### 3. 核心过滤字段独立成列

```text
kb_id
doc_id
```

不要全部塞 metadata。

### 4. 向量必须建立 HNSW

```text
embedding
    ↓
HNSW
```

### 5. Embedding 必须记录模型

```text
embedding_model
```

方便以后升级模型。

### 6. Embedding 和数据库写入必须批量化

```text
Batch Embedding
        ↓
Batch Insert
```

不要一个 Chunk 调一次、写一次。

### 7. 先 Vector Search，再 Rerank

```text
HNSW Top 20
     ↓
Rerank Top 5
     ↓
LLM
```

---

# 三十二、当前实施顺序

现在只做：

```text
★★★★★ Step 1
确认 pgvector

★★★★★ Step 2
创建 document_chunk

★★★★★ Step 3
创建 HNSW / kb_id / doc_id 索引

★★★★☆ Step 4
创建 Java Entity

★★★★☆ Step 5
创建 Repository

★★★★☆ Step 6
实现单条向量保存

★★★★☆ Step 7
实现单条向量检索

★★★★★ Step 8
实现 Batch Embedding

★★★★★ Step 9
实现 Batch Insert

★★★★★ Step 10
实现 kb_id + HNSW 检索

★★★☆☆ Step 11
删除 / 更新 / 重建

★★★☆☆ Step 12
Rerank

★★★☆☆ Step 13
混合检索
```

> **不要跳步骤。**
>
> 先把 `PostgreSQL + pgvector + document_chunk + HNSW + 单条增删查` 跑通，再进入批量向量化。
>
> 这样每一步出了问题都能定位，不会再出现“Spring AI、RocketMQ、切片、Embedding、PostgreSQL、Mapper 全部混在一起，不知道到底哪里有问题”的情况。
