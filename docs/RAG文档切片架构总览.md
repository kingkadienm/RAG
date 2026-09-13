# RAG 文档切片架构总览

> 项目：RAG
> 技术栈：Java 21 + Spring Boot + Spring AI + Spring AI Alibaba + PostgreSQL + pgvector
> 目标：建立一套可扩展的、按文档结构和语义进行切片的 RAG 文档处理架构。

---

# 一、目标

当前项目原始方案：

```text
文件
 ↓
解析
 ↓
TokenTextSplitter
 ↓
Embedding
 ↓
pgvector
```

存在的问题：

* 所有文件使用同一种切片方式
* Markdown 标题结构会丢失
* PDF 页码、标题等上下文容易丢失
* FAQ / 表格 / 代码等特殊内容无法针对性处理
* Chunk 可能在语义中间被强制截断
* 检索结果缺少文档层级信息
* 后续难以扩展不同文件类型的切片策略

最终升级为：

```text
文件
 ↓
文件解析
 ↓
统一 ParsedDocument
 ↓
文档类型识别
 ↓
ChunkingStrategy
 ↓
结构化切片
 ↓
语义边界切片
 ↓
长度约束
 ↓
Chunk
 ↓
EmbeddingText 构建
 ↓
Embedding
 ↓
pgvector
```

核心原则：

> **先保证语义完整，再控制 Chunk 长度。**

---

# 二、整体架构

```text
                    ┌──────────────────┐
                    │      File        │
                    │ PDF/MD/TXT/DOCX  │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ DocumentParser   │
                    │      文件解析      │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ ParsedDocument   │
                    │ 统一解析结果模型   │
                    └────────┬─────────┘
                             │
                             ▼
                  ┌────────────────────────┐
                  │ ChunkingStrategyFactory │
                  │       策略选择器         │
                  └────────────┬───────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
       MarkdownStrategy   PdfStrategy      TxtStrategy
              │                │                │
              └────────────────┼────────────────┘
                               │
                               ▼
                    ┌──────────────────┐
                    │ Semantic Section │
                    │    语义结构切片    │
                    └────────┬─────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │ Length Splitter  │
                    │    长度约束切片    │
                    └────────┬─────────┘
                             │
                             ▼
                         ┌───────┐
                         │ Chunk │
                         └───┬───┘
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
          embeddingText             metadata
                  │                     │
                  ▼                     │
             Embedding                  │
                  │                     │
                  └──────────┬──────────┘
                             ▼
                         pgvector
```

---

# 三、核心设计原则

## 3.1 文件解析和切片分离

Parser 只负责：

> “把文件读懂。”

Chunking 只负责：

> “怎么切。”

不要让 Parser 直接负责 Chunk。

错误：

```text
PdfParser
    ↓
直接生成 Chunk
```

正确：

```text
PdfParser
    ↓
ParsedDocument
    ↓
PdfChunkingStrategy
    ↓
Chunk
```

---

# 四、统一文档模型

不同文件经过 Parser 后，统一转换为：

```text
ParsedDocument
```

建议包含：

```text
ParsedDocument

├── documentId
├── kbId
├── fileName
├── fileType
├── title
├── content
└── elements
```

其中：

```text
elements
```

代表文档内部的结构。

例如 Markdown：

```text
Document
 ├── Heading
 ├── Paragraph
 ├── Heading
 ├── Paragraph
 └── Paragraph
```

PDF：

```text
Document
 ├── Page
 │    ├── Paragraph
 │    └── Paragraph
 ├── Page
 │    └── Paragraph
 └── Page
```

---

# 五、Chunk 模型

最终切出来的 Chunk 不应该只有：

```java
String content;
```

建议：

```text
Chunk

├── documentId
├── kbId
├── chunkIndex
├── content
├── title
├── sectionPath
├── tokenCount
├── charCount
├── metadata
└── embeddingText
```

---

# 六、content、embeddingText、metadata 的职责

这是整个设计中非常重要的一点。

## 6.1 content

真正的原始知识内容。

例如：

```text
纳税资质：小规模纳税人

纳税人识别号：91330108MA9A1B2C3X
```

用于：

```text
检索结果
 ↓
发送给 LLM
```

---

## 6.2 embeddingText

用于生成向量。

不要只使用：

```text
content
```

而是：

```text
文档：集团开票信息汇总
章节：阿里巴巴健康集团

纳税资质：小规模纳税人
纳税人识别号：91330108MA9A1B2C3X
```

然后：

```text
embeddingText
       ↓
Embedding Model
       ↓
Vector
```

这样可以把标题和章节语义一起编码进去。

---

## 6.3 metadata

用于过滤和追踪。

例如：

```json
{
  "documentId": 1001,
  "kbId": 1,
  "fileName": "开票信息.md",
  "fileType": "md",
  "chunkIndex": 2,
  "sectionPath": "集团开票信息汇总 > 阿里巴巴健康集团"
}
```

metadata 不应该完全依赖 embedding。

---

# 七、切片的核心思想

最终采用：

```text
结构优先
 ↓
语义优先
 ↓
长度兜底
```

而不是：

```text
固定 800 Token
 ↓
强制切
```

---

# 八、第一层：文档结构切片

不同文件有不同结构。

## Markdown

```text
# 一级标题

## 二级标题

### 三级标题

正文
```

按照：

```text
Header
 ↓
Section
```

例如：

```text
Java 基础
    ↓
面向对象
    ↓
封装
```

生成：

```text
sectionPath =
Java 基础 > 面向对象 > 封装
```

---

## PDF

优先：

```text
Page
 ↓
Title
 ↓
Paragraph
```

同时尽可能保留：

```text
pageNumber
```

---

## DOCX

优先识别：

```text
Heading 1
Heading 2
Heading 3
Paragraph
Table
```

---

## TXT

TXT 没有明确结构：

```text
空行
 ↓
段落
 ↓
句子
```

作为主要结构。

---

# 九、第二层：语义切片

结构切完之后，还需要判断：

> 当前 Section 是否已经是一个完整语义单元？

例如：

```text
## 阿里巴巴健康集团

开票抬头：杭州阿里巴巴健康集团有限公司
纳税资质：小规模纳税人
纳税人识别号：91330108MA9A1B2C3X
```

这应该整体作为一个 Chunk。

而不是：

```text
Chunk 1：
开票抬头

Chunk 2：
纳税资质

Chunk 3：
纳税人识别号
```

因为这些信息共同构成：

```text
阿里巴巴健康集团的开票信息
```

---

# 十、第三层：长度约束

语义完整并不代表 Chunk 可以无限大。

例如：

```text
Section
↓
2500 tokens
```

显然太大。

因此：

```text
Section
 ↓
Paragraph
 ↓
Sentence
 ↓
Token Splitter
```

最终：

```text
Chunk 1：700 tokens
Chunk 2：650 tokens
Chunk 3：600 tokens
```

---

# 十一、TokenTextSplitter 的定位

目前项目：

```java
TokenTextSplitter
```

不要删除。

但是重新定义它的职责：

> **TokenTextSplitter 是最终长度兜底工具，而不是整个 RAG 的唯一切片策略。**

最终：

```text
Semantic Chunk
       │
       ├── <= maxTokens
       │       ↓
       │      保留
       │
       └── > maxTokens
               ↓
        TokenTextSplitter
```

---

# 十二、Overlap 策略

Overlap 也不应该全局机械使用。

推荐：

```text
语义 Chunk
    ↓
判断长度
    ↓
没有超长
    ↓
不需要 overlap
```

只有：

```text
Chunk > maxTokens
```

才使用：

```text
maxTokens = 800
overlap = 100
```

例如：

```text
Chunk A
700 tokens

Chunk B
600 tokens
```

不需要人为复制。

超长内容：

```text
Section = 2000 tokens

        ↓

Chunk 1 = 700
Chunk 2 = 700 + overlap
Chunk 3 = 700 + overlap
```

---

# 十三、特殊内容策略

不同内容不能全部按照普通文本处理。

## 13.1 FAQ

推荐：

```text
问题 + 答案
```

作为一个 Chunk。

例如：

```text
Q：如何申请发票？

A：登录系统后进入……
```

不要把 Q 和 A 分开。

---

## 13.2 表格

优先：

```text
整表作为语义单元
```

必要时：

```text
表格
 ↓
行
 ↓
多个 Chunk
```

但每个 Chunk 必须保留表头。

例如：

```text
公司 | 税号 | 地址
```

不能只保存：

```text
杭州拼多多科技有限公司 | xxx | xxx
```

应该：

```text
公司 | 税号 | 地址
杭州拼多多科技有限公司 | xxx | xxx
```

---

## 13.3 代码

优先：

```text
类
 ↓
方法
 ↓
代码块
```

不要像普通文章一样按句子切。

---

## 13.4 JSON

优先：

```text
Object
 ↓
Array Item
```

保证 JSON 结构完整。

---

# 十四、策略模式设计

最终建议：

```text
ChunkingStrategy
```

作为统一接口。

```java
public interface ChunkingStrategy {

    boolean supports(String fileType);

    List<Chunk> chunk(ParsedDocument document);
}
```

实现：

```text
ChunkingStrategy
        │
        ├── MarkdownChunkingStrategy
        ├── PdfChunkingStrategy
        ├── DocxChunkingStrategy
        ├── TxtChunkingStrategy
        └── DefaultChunkingStrategy
```

---

# 十五、策略工厂

不要在 Service 中大量：

```java
if ("md".equals(fileType)) {
    ...
} else if ("pdf".equals(fileType)) {
    ...
} else if ("txt".equals(fileType)) {
    ...
}
```

使用：

```text
ChunkingStrategyFactory
```

负责：

```text
fileType
   ↓
选择 Strategy
```

例如：

```text
md
 ↓
MarkdownChunkingStrategy

pdf
 ↓
PdfChunkingStrategy

docx
 ↓
DocxChunkingStrategy

txt
 ↓
TxtChunkingStrategy
```

---

# 十六、推荐的代码包结构

当前项目：

```text
com.wangzs.rag
```

建议：

```text
rag
├── controller
│
├── service
│
├── parser
│   ├── DocumentParser
│   ├── MarkdownParser
│   ├── PdfParser
│   └── DocxParser
│
├── chunk
│   ├── ChunkingStrategy
│   ├── ChunkingStrategyFactory
│   │
│   ├── markdown
│   │   └── MarkdownChunkingStrategy
│   │
│   ├── pdf
│   │   └── PdfChunkingStrategy
│   │
│   ├── docx
│   │   └── DocxChunkingStrategy
│   │
│   ├── txt
│   │   └── TxtChunkingStrategy
│   │
│   └── common
│       ├── SemanticSplitter
│       ├── LengthSplitter
│       └── ChunkBuilder
│
├── model
│   ├── ParsedDocument
│   ├── DocumentElement
│   └── Chunk
│
├── embedding
│
├── retrieval
│
└── repository
```

---

# 十七、实现顺序

不要一次全部实现。

严格按照下面顺序。

## Step 1：定义统一模型

先实现：

```text
ParsedDocument
DocumentElement
Chunk
```

目标：

```text
任何文件解析后
 ↓
都能转换成统一结构
```

---

## Step 2：抽象 ChunkingStrategy

实现：

```java
public interface ChunkingStrategy
```

然后：

```text
ChunkingStrategyFactory
```

目标：

```text
fileType
 ↓
Strategy
```

---

## Step 3：先实现 Markdown

**第一种只实现 Markdown。**

因为 Markdown 最容易验证。

实现：

```text
# Header
## Header
### Header
```

解析为：

```text
sectionPath
content
```

例如：

```text
Java
Java > 集合
Java > 集合 > ArrayList
```

---

## Step 4：加入长度限制

实现：

```text
Semantic Section
       ↓
是否超过 maxTokens
       ↓
否 → 直接 Chunk
是 → LengthSplitter
```

这一步完成后，基本形成第一版真正可用的 Chunking Pipeline。

---

## Step 5：加入 embeddingText

实现：

```text
title
+
sectionPath
+
content
```

形成：

```text
embeddingText
```

然后再进入：

```text
Embedding
```

---

## Step 6：加入 metadata

统一保存：

```text
kbId
documentId
fileName
fileType
chunkIndex
sectionPath
pageNumber
```

---

## Step 7：实现 TXT

TXT：

```text
空行
 ↓
段落
 ↓
长度限制
```

作为没有结构信息时的基础策略。

---

## Step 8：实现 DOCX

识别：

```text
Heading
Paragraph
Table
```

---

## Step 9：实现 PDF

重点解决：

```text
pageNumber
title
paragraph
table
```

PDF 是最复杂的，不建议最开始做。

---

## Step 10：特殊内容

最后再增加：

```text
FAQ
Table
Code
JSON
List
```

不要第一版就全部做。

---

# 十八、第一版 MVP

第一版实际上只需要：

```text
Markdown
   ↓
Header
   ↓
Paragraph
   ↓
Semantic Section
   ↓
maxTokens
   ↓
TokenTextSplitter
   ↓
Chunk
   ↓
embeddingText
   ↓
Embedding
   ↓
pgvector
```

做到这里，你的 RAG 已经比：

```text
整个文件
 ↓
TokenTextSplitter(800,100)
```

合理很多。

---

# 十九、推荐参数

第一版建议：

```yaml
rag:
  chunk:
    max-tokens: 800
    overlap-tokens: 100
    min-tokens: 100
```

但是这些参数不要写死在代码里面。

最终：

```java
ChunkProperties
```

统一管理。

---

# 二十、测试策略

每实现一个阶段，都不要直接依赖 LLM 检查。

首先输出：

```text
文件：
开票信息.md

Chunk 0
------------------
sectionPath:
集团开票信息汇总

tokenCount:
120

content:
...

embeddingText:
...
------------------

Chunk 1
------------------
sectionPath:
集团开票信息汇总 > 阿里巴巴健康集团

tokenCount:
80

content:
...
------------------
```

重点检查：

```text
1. 有没有丢文字
2. 标题有没有丢
3. sectionPath 是否正确
4. Chunk 是否过大
5. Chunk 是否过碎
6. chunkIndex 是否连续
7. metadata 是否正确
```

---

# 二十一、最终目标

最终完整 Pipeline：

```text
                    文件上传
                       │
                       ▼
                 DocumentParser
                       │
                       ▼
                ParsedDocument
                       │
                       ▼
             ChunkingStrategyFactory
                       │
           ┌───────────┼───────────┐
           ▼           ▼           ▼
          MD          PDF         DOCX
           │           │           │
           └───────────┼───────────┘
                       ▼
                 Structure Split
                       │
                       ▼
                 Semantic Split
                       │
                       ▼
                  Length Split
                       │
                       ▼
                     Chunk
                       │
              ┌────────┴────────┐
              ▼                 ▼
         embeddingText       metadata
              │
              ▼
          Embedding
              │
              ▼
           pgvector
              │
              ▼
          Vector Search
              │
              ▼
          Reranking
              │
              ▼
        Context Assembly
              │
              ▼
             LLM
```

---

# 二十二、核心原则总结

整个切片系统只记住 6 条原则：

### 1. Parser 不负责 Chunk

```text
Parser = 解析
Chunker = 切片
```

### 2. 结构优先

```text
标题 > 段落 > 句子 > Token
```

### 3. 语义完整优先

一个问题和答案尽量不要拆开。

### 4. 长度只是约束

```text
TokenTextSplitter
```

是兜底，不是核心。

### 5. Chunk 必须带上下文

```text
title
sectionPath
metadata
```

不要只有正文。

### 6. 不同文件使用不同策略

```text
MD ≠ PDF ≠ DOCX ≠ TXT
```

但最终都输出统一：

```text
Chunk
```

---

# 二十三、我们的实际开发路线

后续实现严格按照：

```text
Step 1
统一模型
        ↓
Step 2
ChunkingStrategy
        ↓
Step 3
MarkdownStrategy
        ↓
Step 4
LengthSplitter
        ↓
Step 5
EmbeddingText
        ↓
Step 6
Metadata
        ↓
Step 7
TXT
        ↓
Step 8
DOCX
        ↓
Step 9
PDF
        ↓
Step 10
FAQ / Table / Code / JSON
        ↓
Step 11
Chunk 质量评估
        ↓
Step 12
检索效果优化
```

> **不要跨步骤实现。**
>
> 每完成一个 Step，先写测试验证，再进入下一个 Step。

第一阶段只解决一个问题：

> **“一个文件经过解析后，如何形成统一的 `ParsedDocument → Chunk` 数据模型。”**

暂时不要碰 Embedding、pgvector、Rerank、LLM 语义判断。

这样可以保证整个 RAG 切片模块职责清晰，而且后面即使换 PDF 解析器、换 Embedding 模型、换向量数据库，也不会把 Chunking 逻辑搞乱。
