# PostgreSQL 数据访问层技术选型说明

## 1. 背景

项目采用 **MySQL + PostgreSQL + pgvector** 双数据库架构：

```text
                    ┌─────────────────────┐
                    │      RAG 应用层       │
                    └──────────┬──────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
        ┌───────────────┐             ┌─────────────────┐
        │     MySQL     │             │ PostgreSQL      │
        │               │             │ + pgvector      │
        │ 业务数据       │             │                 │
        │ 文档管理       │             │ Chunk           │
        │ 知识库         │             │ Embedding       │
        │ 用户           │             │ Vector Search   │
        │ 会话/消息      │             │ Metadata        │
        └───────────────┘             └─────────────────┘
                │                             │
                ▼                             ▼
        MyBatis-Plus                    JdbcTemplate
```

其中：

* **MySQL**：负责业务数据和业务状态管理
* **PostgreSQL + pgvector**：负责 RAG 分块、向量以及相似度检索
* **MyBatis-Plus**：用于 MySQL 业务数据访问
* **JdbcTemplate**：用于 PostgreSQL 向量数据访问

PostgreSQL 侧的核心表为：

```text
document_chunk
```

该表包含：

```text
content       → 文本分块
embedding     → VECTOR(1536)
metadata      → JSONB
kb_id         → 知识库 ID
doc_id        → 文档 ID
chunk_index   → 分块序号
```

因此 PostgreSQL 侧的数据访问与普通业务 CRUD 存在明显区别。

---

# 2. 为什么 PostgreSQL 侧选择 JdbcTemplate

## 2.1 数据库职责不同，技术栈应该保持边界

项目并不是简单的“双数据源 CRUD”，而是两个数据库承担完全不同的职责：

```text
MySQL
  │
  ├── 用户
  ├── 知识库
  ├── 文档
  ├── 上传记录
  ├── 会话
  ├── 消息
  └── 系统配置
        │
        └── MyBatis-Plus


PostgreSQL + pgvector
  │
  ├── 文档 Chunk
  ├── Embedding
  ├── Metadata
  └── Vector Search
        │
        └── JdbcTemplate
```

因此项目采用明确的技术边界：

```text
MySQL       → MyBatis-Plus
PostgreSQL  → JdbcTemplate
```

这样做的目的不是为了“一个数据库使用一种框架”，而是让数据访问方式与数据库职责保持一致。

---

# 3. PostgreSQL 侧的数据访问场景非常简单

目前 `document_chunk` 主要需要以下几类操作：

### 保存 Chunk

```sql
INSERT INTO rag.document_chunk (
    kb_id,
    doc_id,
    chunk_index,
    content,
    embedding,
    metadata
)
VALUES (?, ?, ?, ?, ?::vector, ?::jsonb);
```

### 查询 Chunk

```sql
SELECT
    id,
    kb_id,
    doc_id,
    chunk_index,
    content,
    metadata
FROM rag.document_chunk
WHERE doc_id = ?;
```

### 删除文档 Chunk

```sql
DELETE FROM rag.document_chunk
WHERE doc_id = ?;
```

### 向量相似度搜索

```sql
SELECT
    id,
    kb_id,
    doc_id,
    chunk_index,
    content,
    metadata,
    1 - (embedding <=> ?::vector) AS similarity
FROM rag.document_chunk
WHERE kb_id = ?
  AND embedding IS NOT NULL
ORDER BY embedding <=> ?::vector
LIMIT ?;
```

可以看到，PostgreSQL 侧并不存在复杂的业务 CRUD。

核心操作实际上就是：

```text
保存
查询
删除
向量检索
```

因此没有必要为了 PostgreSQL 再建立一套完整的 MyBatis-Plus Mapper 体系。

---

# 4. pgvector 决定了 PostgreSQL 侧必须大量使用原生 SQL

普通 MySQL 查询通常可以抽象为：

```java
LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Document::getKbId, kbId);
```

但是 RAG 最核心的查询并不是普通条件查询，而是：

```sql
ORDER BY embedding <=> ?::vector
```

其中：

```text
<=> 
```

是 pgvector 提供的距离运算符。

例如：

```sql
1 - (embedding <=> ?::vector)
```

可以得到余弦相似度。

同时还需要：

```sql
WHERE kb_id = ?
```

配合：

```sql
ORDER BY embedding <=> ?::vector
LIMIT 10
```

最终形成典型的向量检索：

```text
Query
  │
  ▼
Embedding
  │
  ▼
PostgreSQL
  │
  ├── kb_id 过滤
  │
  ├── pgvector 距离计算
  │
  ├── HNSW 索引
  │
  └── Top K
```

这种 SQL 本身就是 PostgreSQL + pgvector 的核心能力。

因此这里更适合：

```java
jdbcTemplate.query(...)
jdbcTemplate.update(...)
```

而不是强行通过 ORM/Wrapper 抽象。

---

# 5. 为什么不继续使用 MyBatis-Plus

## 5.1 MyBatis-Plus 的优势在这里利用率很低

MyBatis-Plus 非常适合：

```text
Entity
   ↓
Mapper
   ↓
CRUD
   ↓
条件构造
   ↓
分页
```

例如 MySQL：

```java
knowledgeBaseMapper.selectList(
    new LambdaQueryWrapper<KnowledgeBase>()
        .eq(KnowledgeBase::getCreatorId, userId)
);
```

这些能力对 MySQL 业务表非常有价值。

但是 PostgreSQL 的 `document_chunk` 主要是：

```text
INSERT
DELETE
Vector Search
```

其中最重要的查询反而是 pgvector 原生 SQL。

因此 MyBatis-Plus 在这里的使用率很低。

---

# 6. VECTOR 类型的处理

`document_chunk` 中：

```sql
embedding VECTOR(1536)
```

是 pgvector 提供的 PostgreSQL 扩展类型。

它不是 MySQL 中普通的：

```text
INT
VARCHAR
DECIMAL
TEXT
```

这类标准关系型字段。

如果使用 MyBatis / MyBatis-Plus，需要考虑：

```text
Java Vector
      ↓
TypeHandler
      ↓
PostgreSQL VECTOR
```

尤其是：

```text
查询
插入
更新
```

都需要考虑 Java 类型与 PostgreSQL `vector` 类型之间的转换。

而 JdbcTemplate 可以直接围绕 SQL 和 JDBC 参数处理这一过程。

例如：

```java
String vector = embedding.toString();

jdbcTemplate.update(
    """
    INSERT INTO document_chunk (
        kb_id,
        doc_id,
        content,
        embedding
    )
    VALUES (?, ?, ?, ?::vector)
    """,
    kbId,
    docId,
    content,
    vector
);
```

核心逻辑非常直接：

```text
Java List<Float>
       ↓
"[0.12,0.34,0.56,...]"
       ↓
?::vector
       ↓
PostgreSQL VECTOR
```

---

# 7. JSONB 的处理

Chunk 还包含：

```sql
metadata JSONB
```

例如：

```json
{
  "title": "IT支持",
  "section_path": "服务器 > 网络",
  "token_count": 420,
  "file_type": "md"
}
```

这个字段属于扩展元数据，而不是核心检索条件。

Java 中可以使用 Jackson：

```java
String metadataJson =
        objectMapper.writeValueAsString(metadata);
```

然后：

```sql
?::jsonb
```

写入 PostgreSQL。

查询时再进行反序列化。

因此：

```text
Java Object
    ↓
Jackson
    ↓
JSON String
    ↓
PostgreSQL JSONB
```

即可满足当前需求。

---

# 8. PostgreSQL Repository 应该保持简单

因此 PostgreSQL 侧建议直接建立：

```text
repository
    └── DocumentChunkRepository
```

例如：

```java
@Repository
public class DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(...) {
        // INSERT
    }

    public List<DocumentChunk> search(...) {
        // Vector Search
    }

    public void deleteByDocId(Long docId) {
        // DELETE
    }
}
```

调用关系：

```text
DocumentService
       │
       ▼
DocumentChunkRepository
       │
       ▼
JdbcTemplate
       │
       ▼
PostgreSQL + pgvector
```

不需要为了三几个 SQL 再建立：

```text
DocumentChunkMapper
DocumentChunkMapper.xml
DocumentChunkService
DocumentChunkServiceImpl
```

这样反而增加了结构复杂度。

---

# 9. 两套数据访问技术的最终边界

项目最终形成：

```text
                    Application
                         │
          ┌──────────────┴──────────────┐
          │                             │
          ▼                             ▼
   Business Service              RAG Service
          │                             │
          ▼                             ▼
 MyBatis-Plus                    Repository
          │                             │
          ▼                             ▼
        MySQL                     JdbcTemplate
          │                             │
          │                       PostgreSQL
          │                         + pgvector
          │                             │
          │                       Vector Search
          │                             │
          └──────────────┬──────────────┘
                         ▼
                    RAG Pipeline
```

职责明确：

| 数据             | 数据库                   | 数据访问         |
| -------------- | --------------------- | ------------ |
| 用户             | MySQL                 | MyBatis-Plus |
| 知识库            | MySQL                 | MyBatis-Plus |
| 文档             | MySQL                 | MyBatis-Plus |
| 上传记录           | MySQL                 | MyBatis-Plus |
| 会话             | MySQL                 | MyBatis-Plus |
| 消息             | MySQL                 | MyBatis-Plus |
| 系统配置           | MySQL                 | MyBatis-Plus |
| Document Chunk | PostgreSQL            | JdbcTemplate |
| Embedding      | PostgreSQL            | JdbcTemplate |
| Metadata       | PostgreSQL            | JdbcTemplate |
| Vector Search  | PostgreSQL + pgvector | JdbcTemplate |

---

# 10. 与 MyBatis-Plus 对比

| 维度                | MyBatis-Plus | JdbcTemplate      |
| ----------------- | ------------ | ----------------- |
| 普通 CRUD           | 很强           | 足够                |
| 条件构造器             | 很强           | 手写 SQL            |
| 分页                | 很强           | 手写 SQL            |
| 自动填充              | 支持           | 手动处理              |
| `VECTOR`          | 需要额外适配       | 直接围绕 JDBC/SQL 处理  |
| `JSONB`           | 需要额外适配       | Jackson + JDBC 即可 |
| pgvector `<=>`    | 最终仍需要原生 SQL  | 原生 SQL            |
| HNSW 查询           | 原生 SQL       | 原生 SQL            |
| SQL 可控性           | 高            | 很高                |
| PostgreSQL RAG 检索 | 偏重           | 更直接               |
| 当前项目适配度           | 中            | 高                 |

---

# 11. 一个重要的架构原则

这里真正应该坚持的不是：

> “MySQL 必须 MyBatis-Plus，PostgreSQL 必须 JdbcTemplate。”

而是：

> **数据访问技术应该由数据职责和访问模式决定。**

MySQL 是：

```text
业务数据
+
大量 CRUD
+
条件查询
+
分页
```

所以：

```text
MyBatis-Plus
```

非常合适。

PostgreSQL 是：

```text
Chunk
+
Embedding
+
JSONB Metadata
+
Vector Search
+
pgvector
+
HNSW
```

所以：

```text
JdbcTemplate
+
原生 SQL
```

更加直接。

---

# 12. 最终结论

PostgreSQL + pgvector 侧选择 `JdbcTemplate`，主要基于以下原因：

### ① 数据职责明确

```text
MySQL
→ 业务数据库
→ MyBatis-Plus

PostgreSQL
→ RAG 向量数据库
→ JdbcTemplate
```

### ② PostgreSQL 操作数量少

目前主要就是：

```text
INSERT
DELETE
Vector Search
```

没有必要引入完整 ORM CRUD 能力。

### ③ RAG 检索天然依赖原生 SQL

核心查询：

```sql
ORDER BY embedding <=> ?::vector
```

以及：

```sql
HNSW
JSONB
VECTOR
```

本身就是 PostgreSQL/pgvector 的原生能力。

### ④ Repository 足够承担数据访问职责

最终保持：

```text
Service
   ↓
DocumentChunkRepository
   ↓
JdbcTemplate
   ↓
PostgreSQL
```

即可。

这样既保持了项目的 **单一职责**，又避免为了少量 PostgreSQL 操作引入额外的 Mapper/XML/Service 层。

---

## 推荐最终目录

```text
com.wangzs.rag
│
├── controller
│
├── service
│   ├── KnowledgeBaseService
│   ├── DocumentService
│   ├── ChunkingService
│   ├── EmbeddingService
│   └── RetrievalService
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
│   └── vector
│
└── mq
    └── DocumentParseConsumer
```

最终形成一个非常清晰的边界：

```text
                    RAG Application
                           │
          ┌────────────────┴────────────────┐
          │                                 │
          ▼                                 ▼
       MySQL                         PostgreSQL
          │                            + pgvector
          │                                 │
          ▼                                 ▼
   MyBatis-Plus                     JdbcTemplate
          │                                 │
          ▼                                 ▼
   Business CRUD                    Vector Retrieval
```

这套设计对于你现在这个 RAG 项目来说已经足够，不建议为了“统一技术栈”强行把 PostgreSQL 也改成 MyBatis-Plus。
