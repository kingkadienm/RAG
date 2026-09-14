# Design Document: RAG System Optimization

## Overview

This document specifies the technical design for resolving 15 identified issues in the RAG knowledge-base Q&A service. The system is built on Spring Boot 3.5 / Java 21, Spring AI Alibaba 1.1.2.3, MyBatis-Plus 3.5.12, RocketMQ 2.3.6, Sa-Token 1.39.0, Redis, PostgreSQL + pgvector, and Lombok.

The optimizations fall into five concern groups:

1. **Security hardening** (Req 1, 2) — remove hardcoded credentials, separate log profiles
2. **Data correctness** (Req 3, 4, 9, 11, 15) — sliding-window context, null-safe metadata, no duplicate embeddings, correct creator attribution, duplicate import
3. **Production robustness** (Req 5, 7, 8, 12, 13) — sanitize debug output, unified MQ format, bounded SSE thread pool, max-retry guard, unambiguous config
4. **API completeness** (Req 6, 14) — standardised SSE events, missing document/session endpoints
5. **Admin capabilities** (Req 10) — admin bypass for KB ownership checks

No new external dependencies are introduced. All changes are contained to the 
ag module and common module.

## Architecture

The system follows a layered architecture that is unchanged by these optimizations:

```
HTTP Client
    │
    ▼
Controller Layer   (ChatController, DocumentController, ChatSessionController)
    │
    ▼
Service Layer      (ChatService, ChatMessageService, RetrievalService, EmbeddingService,
                    KnowledgeBaseService, DocumentService, UploadService, ConfigService)
    │
    ├── Redis      (SessionContext sliding window)
    ├── MySQL      (MyBatis-Plus — chat, document, session, config tables)
    ├── PostgreSQL (pgvector — vector similarity search)
    └── RocketMQ   (async document parse pipeline)
```

### Key Interaction Flows (post-optimization)

**Chat stream flow (Req 3, 5, 6)**:
```
POST /api/chat/completions/stream
  → ChatService.chatStream()
      → retrievalService.search()           — returns SearchResult list (null-safe, Req 4)
      → chatMessageService.buildPrompt()    — reads sliding-window history from Redis (Req 3)
      → chatClient.stream()
      → Flux<ServerSentEvent<String>>       — typed SSE events: ref_chunks / message / session_id / done (Req 6)
      → chatMessageService.saveMessages()   — appends to Redis list + trims to window size (Req 3)
```

**Document retry flow (Req 7, 8, 12)**:
```
DocumentRetryScheduler (scheduled)
  → retryStuckDocuments()  — checks retryCount < maxAttempts (Req 12)
  → autoRetryFailedDocuments()
      → documentService.sendParseMessage(doc)   — uses RocketMQTemplate + DocumentParseMsgDTO (Req 7)

DocumentServiceImpl.streamParseStatus()  — uses shared ScheduledExecutorService bean (Req 8)
```


## Components and Interfaces

### Req 1 — Externalize Sensitive Configuration

**Files changed**: `rag/src/main/resources/application.yaml`, new `.env.example`

Replace hardcoded credentials with `${ENV_VAR_NAME}` Spring EL references:

| Hardcoded Value | Environment Variable |
|---|---|
| MySQL password `Y5TRj54n6ArXbMEB` | `MYSQL_PASSWORD` |
| Redis password `RCtCZmzcCepnxbCW` | `REDIS_PASSWORD` |
| PostgreSQL password `postgres123456` | `POSTGRES_PASSWORD` |
| Sa-Token JWT secret `rag-secret-key-change-in-production` | `SA_TOKEN_JWT_SECRET` |

Spring Boot's `@Value` and `${…}` YAML syntax will throw `IllegalArgumentException` on startup if the variable is absent and no default is provided — this satisfies the fail-fast requirement.

`.env.example` documents all required variables with placeholder values; it is committed to version control but never contains real secrets.

---

### Req 2 — Production-Safe Logging

**Files changed**: `rag/src/main/resources/application.yaml`, new `application-dev.yaml`, new `application-prod.yaml`

Strategy: move all logging configuration out of the base `application.yaml` into profile-specific files. Set the default active profile to `prod`.

`application-prod.yaml`:
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
logging:
  level:
    root: INFO
    org.springframework.ai: INFO
    org.springframework.jdbc: INFO
```

`application-dev.yaml`:
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
logging:
  level:
    org.springframework.ai: DEBUG
    org.springframework.ai.vectorstore: DEBUG
    org.springframework.jdbc.core.JdbcTemplate: DEBUG
    org.springframework.jdbc.core.StatementCreatorUtils: TRACE
```

---

### Req 3 — Sliding-Window Session Context

**File changed**: `ChatMessageService.java`

**Current bug**: `saveMessages()` overwrites the entire Redis key with only the two new messages:
```java
redisUtil.set(redisKey, ctx, 1800);  // always a 2-element list
```

**Fix design**: Read → append → trim → write back:
```
1. existing = redisUtil.getObject(redisKey, List.class)  // may be null
2. if null: existing = new ArrayList<>()
3. existing.add(userMsg); existing.add(assistantMsg)
4. windowSize = configService.getInt("rag.chat.context-window-size", 10)
5. if existing.size() > windowSize:
       existing = existing.subList(existing.size() - windowSize, existing.size())
6. redisUtil.set(redisKey, existing, 1800)
```

`ChatService.buildMessages()` already reads the full list from Redis — no changes needed there.

---

### Req 4 — Null-Safe SearchResult Metadata Parsing

**File changed**: `RetrievalService.java`

**Current bug**: `SearchResult.from()` unconditionally calls `.longValue()`, `.intValue()` on values that may be null when a PGVector document lacks expected metadata fields, causing `NullPointerException`.

**Fix design**: Use safe helper methods with null fallback:
```java
public static SearchResult from(Document doc) {
    Map<String, Object> metadata = doc.getMetadata();
    return new SearchResult(
        getLong(metadata, "doc_id"),
        getLong(metadata, "kb_id"),
        getInt(metadata, "chunk_index"),
        getInt(metadata, "chunk_total"),
        getString(metadata, "title"),
        getString(metadata, "file_name"),
        doc.getText(),
        doc.getScore()   // Double — already nullable
    );
}

private static Long getLong(Map<String, Object> m, String key) {
    if (m == null) return null;
    Object v = m.get(key);
    return v instanceof Number n ? n.longValue() : null;
}

private static Integer getInt(Map<String, Object> m, String key) {
    if (m == null) return null;
    Object v = m.get(key);
    return v instanceof Number n ? n.intValue() : null;
}

private static String getString(Map<String, Object> m, String key) {
    if (m == null) return null;
    Object v = m.get(key);
    return v instanceof String s ? s : null;
}
```

The outer `search()` method wraps `SearchResult.from()` in a try-catch; on any exception it logs a warning with the document ID and returns an empty list for that invocation.

---

### Req 5 — Remove Debug Code / Sanitize Error Messages

**File changed**: `ChatService.java`

Remove both `TODO【RAG 调试用】` guard blocks. The intended post-fix behavior:

- If `searchResults.isEmpty()`: invoke LLM with empty-context prompt (same path as non-empty results). The system prompt instructs the model to respond gracefully when no context is found.
- User-facing message when context is empty comes from the LLM, not a hardcoded string with internal IDs.

Specifically remove:
1. The `if (searchResults.isEmpty()) { return …"调试提示：kbId=…" }` block in `chat()`
2. The equivalent block in `chatStream()` that returns `Flux.just(errorMsg + "\n\n[SESSION_ID:…]\n\n[DONE]")`

---

### Req 6 — Standardise SSE Stream Event Format

**Files changed**: `ChatService.java`, `ChatController.java`

**Current**: `chatStream()` returns `Flux<String>` with in-band markers `[REF_CHUNKS:…]`, `[SESSION_ID:…]`, `[DONE]`.

**Target**: Return `Flux<ServerSentEvent<String>>` with typed SSE events.

Event sequence:
```
event: ref_chunks
data: [{docId:1,fileName:"...",content:"...",score:0.85},...]\n\n

event: message
data: <token1>\n\n

event: message
data: <token2>\n\n
...

event: session_id
data: <uuid>\n\n

event: done
data: \n\n
```

`ChatService.chatStream()` signature change:
```java
// before
public Flux<String> chatStream(ChatRequest request)

// after
public Flux<ServerSentEvent<String>> chatStream(ChatRequest request)
```

`ChatController` endpoint:
```java
@PostMapping(value = "/completions/stream",
             produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> completionsStream(@Valid @RequestBody ChatRequest request) {
    return chatService.chatStream(request);
}
```

Internal building blocks (helper methods on `ChatService`):
```java
private ServerSentEvent<String> sse(String eventName, String data) {
    return ServerSentEvent.<String>builder()
            .event(eventName)
            .data(data)
            .build();
}
```

---

### Req 7 — Unify RocketMQ Message Format in DocumentRetryScheduler

**File changed**: `DocumentRetryScheduler.java`

**Current bug**: `sendParseMessage()` uses `DefaultMQProducer` and sends a raw `Map<String,Object>` serialised as bytes. `DocumentParseConsumer` expects `DocumentParseMsgDTO` — deserialization fails.

**Fix**: Remove `sendParseMessage(Document, int)` entirely from `DocumentRetryScheduler`. Delegate to the already-correct `DocumentService.sendParseMessage(doc)`:

```java
// autoRetryFailedDocuments() — before retry send:
documentService.sendParseMessage(doc);

// retryStuckDocuments() — before retry send:
documentService.sendParseMessage(doc);
```

Remove `DefaultMQProducer`, `ObjectMapper` from `DocumentRetryScheduler` field list.

---

### Req 8 — Fix ThreadPoolTaskScheduler Leak in streamParseStatus

**Files changed**: `DocumentServiceImpl.java`, new or existing `AppConfig.java` (or `SseConfig.java`)

**Current bug**: Each call to `streamParseStatus()` creates a new `ThreadPoolTaskScheduler`, initialises it, and never releases it until the SSE connection closes — unbounded thread creation under concurrent load.

**Fix design**:

1. Declare a shared `ScheduledExecutorService` bean:

```java
// in AppConfig.java (or SseConfig.java)
@Bean(name = "sseStatusExecutor", destroyMethod = "shutdown")
public ScheduledExecutorService sseStatusExecutor(
        @Value("${rag.sse.status-pool-size:4}") int poolSize) {
    return Executors.newScheduledThreadPool(poolSize);
}
```

Add `rag.sse.status-pool-size: 4` to `application.yaml`.

2. Inject `ScheduledExecutorService sseStatusExecutor` into `DocumentServiceImpl`.

3. Replace `scheduler.scheduleAtFixedRate(…)` with:
```java
ScheduledFuture<?> future = sseStatusExecutor.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);
emitter.onCompletion(() -> future.cancel(false));
emitter.onError(e -> future.cancel(false));
emitter.onTimeout(() -> future.cancel(false));
```

The shared executor's lifecycle is managed by Spring via `destroyMethod = "shutdown"`.

---

### Req 9 — Eliminate Redundant Embedding Computation

**File changed**: `EmbeddingService.java`

**Current bug**: `embedBatch()` calls `embeddingModel.embed(contents)` to get `List<float[]>` vectors, then creates `Document` objects without setting the embedding, causing `PgVectorStore.add()` to call the embedding model a second time.

**Investigation**: Spring AI's `Document` class has an `embedding` field (`List<Double>`). `PgVectorStore.add()` checks: if `document.getEmbedding() != null && !document.getEmbedding().isEmpty()`, it skips re-embedding.

**Fix design**: After computing `float[] embedding`, set it on the `Document` before calling `vectorStore.add()`:

```java
for (int i = 0; i < batch.size(); i++) {
    Chunk chunk = batch.get(i);
    float[] rawEmbedding = embeddings.get(i);
    
    // Convert float[] to List<Double> (Spring AI Document.embedding type)
    List<Double> embeddingList = new ArrayList<>(rawEmbedding.length);
    for (float f : rawEmbedding) {
        embeddingList.add((double) f);
    }
    
    org.springframework.ai.document.Document vectorDoc = 
        new org.springframework.ai.document.Document(
            buildContent(chunk, doc),
            buildMetadata(doc, chunk)
        );
    vectorDoc.setEmbedding(embeddingList);
    vectorDocs.add(vectorDoc);
}
vectorStore.add(vectorDocs);
```

---

### Req 10 — Admin Role Bypass for KB Ownership Verification

**Files changed**: `KnowledgeBaseService.java`, `UserMapper.java` (or use existing `BaseMapper.selectById`)

**Current**: `verifyOwnership()` only checks `creatorId == currentUserId`.

**Fix design**: Fetch the current user's role via the existing `UserMapper` (already available via `BaseMapper<User>.selectById(userId)`) and allow admin users through:

```java
public KnowledgeBase verifyOwnership(Long id) {
    KnowledgeBase kb = getById(id);
    Long currentUserId = AuthUtil.getLoginUserId();
    
    // Admin bypass: role == 1 (ADMIN)
    User currentUser = userMapper.selectById(currentUserId);
    if (currentUser != null && currentUser.getRole() == UserRoleEnum.ADMIN) {
        return kb;
    }
    
    if (!currentUserId.equals(kb.getCreatorId())) {
        throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
    }
    return kb;
}
```

`KnowledgeBaseService` already imports `AuthUtil`. Add `UserMapper` injection.

---

### Req 11 — Populate creatorId from Authenticated User in UploadService

**File changed**: `UploadServiceImpl.java`

**Current bug**: `documentService.createWithUploadRecord(…, 0L, …)` — hardcoded `0L` as creatorId.

**Fix**: Replace `0L` with `AuthUtil.getLoginUserId()`:

```java
Long creatorId = AuthUtil.getLoginUserId();
doc = documentService.createWithUploadRecord(
    kbId, originalFilename, originalFilename,
    extension, file.getSize(), storedFileName, md5,
    creatorId, storedFileName, mimeType, storageType
);
```

`AuthUtil` is already on the classpath (common module). No new imports needed beyond the existing ones in scope.

---

### Req 12 — Maximum Retry Limit in retryStuckDocuments

**File changed**: `DocumentRetryScheduler.java`

**Current bug**: `retryStuckDocuments()` calls `sendParseMessage()` without checking `retryCount` against the maximum.

**Fix**: Add the same retry-count guard already present in `autoRetryFailedDocuments()`:

```java
for (Document doc : stuckDocs) {
    if (doc.getUpdatedTime() != null
            && doc.getUpdatedTime().isBefore(LocalDateTime.now().minusMinutes(30))) {
        
        int retryCount = doc.getRetryCount() != null ? doc.getRetryCount() : 0;
        int maxAttempts = configService.getInt("rag.retry.max-attempts", 3);
        
        if (retryCount >= maxAttempts) {
            log.warn("卡住文档已达最大重试次数，跳过: docId={}, retries={}/{}",
                    doc.getId(), retryCount, maxAttempts);
            continue;
        }
        
        log.warn("文档解析超时，重置为待解析: docId={}", doc.getId());
        documentService.updateParseStatus(doc.getId(), ParseStatusEnum.INIT, 0, "解析超时，自动重试");
        documentService.incrementRetryCount(doc.getId());
        documentService.sendParseMessage(doc);  // unified call (Req 7 fix already applied)
    }
}
```

---

### Req 13 — Clarify similarity-threshold Configuration

**File changed**: `rag/src/main/resources/application.yaml`

Remove:
```yaml
#    similarity-threshold: 0.0   # ⚠️ 临时设置为 0.0 绕过 Spring AI Bug（原值 0.7）
```

Add (under `rag.retrieval`):
```yaml
rag:
  retrieval:
    top-k: 5
    # 向量相似度阈值：检索结果必须达到此分值才会返回给 LLM。
    # 运行时优先读取 sys_config 表中的 rag.retrieval.similarity-threshold 键值；
    # 如数据库中无该配置项，则使用此处的默认值 0.6。
    similarity-threshold: 0.6
```

`RetrievalService` already reads `configService.getDouble("rag.retrieval.similarity-threshold", 0.6)` — no code change needed.

---

### Req 14 — Document and Session API Endpoints

**Status of existing endpoints**:

| Endpoint | Status |
|---|---|
| `GET /api/doc/{id}` | ✅ Already implemented in `DocumentController` |
| `GET /api/doc/{id}/chunks` | ✅ Already implemented (throws BizException for non-SUCCESS parse) |
| `GET /api/chat/sessions` | ✅ Already implemented in `ChatSessionController` |
| `GET /api/chat/sessions/{sessionId}/messages` | ✅ Already implemented |
| `DELETE /api/chat/sessions/{sessionId}` | ✅ Already implemented with ownership check |

**Gap**: `DocumentController.getById()` and `getChunks()` do not verify document ownership, violating Req 14.6. Fix:

```java
// DocumentController.getById()
@GetMapping("/{id}")
public ApiResult<Document> getById(@PathVariable Long id) {
    Document doc = documentService.getById(id);
    verifyDocumentAccess(doc);
    return ApiResult.success(doc);
}

// DocumentController.getChunks()
@GetMapping("/{id}/chunks")
public ApiResult<List<Chunk>> getChunks(@PathVariable Long id) {
    Document doc = documentService.getById(id);
    verifyDocumentAccess(doc);
    List<Chunk> chunks = documentService.getChunks(id);
    return ApiResult.success(chunks);
}

// Private helper
private void verifyDocumentAccess(Document doc) {
    Long currentUserId = AuthUtil.getLoginUserId();
    User currentUser = userMapper.selectById(currentUserId);
    boolean isAdmin = currentUser != null && currentUser.getRole() == UserRoleEnum.ADMIN;
    if (!isAdmin && !currentUserId.equals(doc.getCreatorId())) {
        throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND);
    }
}
```

`DocumentController` needs `UserMapper` injected. Alternatively, a `DocumentService.verifyOwnership(id)` method can encapsulate this logic to keep the controller thin.

---

### Req 15 — Remove Duplicate Import in DocumentServiceImpl

**File changed**: `DocumentServiceImpl.java`

Remove the duplicate `import com.wangzs.rag.chunk.Chunk;` line. This is a one-line change.

## Data Models

No new tables or schema changes are required. The affected data models are:

### SessionContext (Redis)
**Key**: `chat:ctx:{sessionId}`
**Type**: Serialised `List<ChatMessage>` object (using Spring `RedisTemplate<String, Object>` with Jackson serialisation)
**Current behaviour**: Replaced on each `saveMessages()` call (always 2 elements).
**Post-fix behaviour**: Sliding window list of up to `rag.chat.context-window-size` (default 10) `ChatMessage` records, trimmed from the front. TTL reset to 1800 s on each write.

### DocumentParseMsgDTO (RocketMQ)
No changes to the DTO fields. `DocumentRetryScheduler` will now produce properly typed messages matching this schema, ensuring consumer deserialisation succeeds:

| Field | Type | Description |
|---|---|---|
| `docId` | Long | Document primary key |
| `kbId` | Long | Knowledge base ID |
| `filePath` | String | Storage path / S3 key |
| `fileName` | String | Original file name |
| `fileType` | String | File extension |
| `fileMd5` | String | Content hash |
| `storageType` | String | `local` or `cloud` |
| `version` | Integer | Document version for idempotency |

### application.yaml (configuration)
New keys added:

| Key | Default | Purpose |
|---|---|---|
| `rag.chat.context-window-size` | 10 | Sliding-window message count (Req 3) |
| `rag.retrieval.similarity-threshold` | 0.6 | Vector similarity threshold (Req 13) |
| `rag.sse.status-pool-size` | 4 | SSE status executor thread count (Req 8) |


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

The following properties are selected from acceptance criteria that involve logic which varies meaningfully with input and where 100+ iterations would reveal edge cases that 2-3 examples would miss. Infrastructure, configuration, and UI-only criteria use example-based or smoke tests instead.

---

### Property 1: Sliding-window context does not exceed configured window size

*For any* session ID and any number of consecutive `saveMessages()` calls N, after each call the Redis context list size SHALL be `min(N * 2, windowSize)` where `windowSize = configService.getInt("rag.chat.context-window-size", 10)`.

**Validates: Requirements 3.1, 3.2**

---

### Property 2: Null-safe SearchResult construction never throws

*For any* `org.springframework.ai.document.Document` with an arbitrary subset of metadata keys present (including the empty map and null map), `SearchResult.from(doc)` SHALL return a non-null `SearchResult` without throwing any exception, and absent numeric/string fields SHALL be `null` in the result.

**Validates: Requirements 4.1, 4.2, 4.3**

---

### Property 3: SSE stream event ordering is always ref_chunks → message* → session_id → done

*For any* valid `ChatRequest`, the `chatStream()` `Flux<ServerSentEvent<String>>` SHALL emit events in the order: zero or one `ref_chunks` event, followed by zero or more `message` events, followed by exactly one `session_id` event, followed by exactly one `done` event as the terminal element.

**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**

---

### Property 4: DocumentParseMsgDTO round-trip preserves all fields

*For any* `DocumentParseMsgDTO` with valid field values, serialising to JSON (via RocketMQ/Jackson) and deserialising back SHALL produce a `DocumentParseMsgDTO` where all fields are equal to the original.

**Validates: Requirements 7.3, 7.4**

---

### Property 5: Embedding model called exactly once per batch

*For any* list of `Chunk` objects submitted to `EmbeddingService.embedAndSave()`, the underlying `EmbeddingModel.embed()` method SHALL be called at most `ceil(chunks.size() / batchSize)` times — equal to the number of batches, not double that.

**Validates: Requirements 9.1, 9.3**

---

### Property 6: Admin users always pass KB ownership verification

*For any* knowledge base ID and any user ID with `UserRoleEnum.ADMIN` role, `KnowledgeBaseService.verifyOwnership(kbId)` SHALL return the `KnowledgeBase` without throwing, regardless of the KB's `creatorId`.

**Validates: Requirements 10.1**

---

### Property 7: Non-admin non-owner users always fail KB ownership verification

*For any* knowledge base ID where `kb.creatorId != currentUserId` and the current user does not have `UserRoleEnum.ADMIN` role, `verifyOwnership(kbId)` SHALL throw `BizException` with code `KNOWLEDGE_BASE_NOT_FOUND`.

**Validates: Requirements 10.2**

---

### Property 8: Uploaded document creatorId equals the authenticated user ID

*For any* authenticated user with ID `userId` who calls `UploadServiceImpl.uploadFile()`, the resulting `Document.creatorId` SHALL equal `userId`.

**Validates: Requirements 11.1**

---

### Property 9: Stuck documents at max retries are never retried again

*For any* document whose `retryCount >= configService.getInt("rag.retry.max-attempts", 3)`, calling `DocumentRetryScheduler.retryStuckDocuments()` SHALL NOT invoke `documentService.sendParseMessage()` for that document.

**Validates: Requirements 12.1, 12.2**


## Error Handling

### Configuration startup failure (Req 1)
If a required environment variable is absent, Spring's `${ENV_VAR}` binding throws `IllegalArgumentException` during context refresh. No additional code needed — this is the natural fail-fast behaviour.

### Null metadata in SearchResult (Req 4)
The outer `RetrievalService.search()` method wraps `SearchResult.from(doc)` in a try-catch. On `Exception`, it logs `WARN "SearchResult construction failed for doc {}: {}"` and returns an empty `List.of()` for that invocation. The call site (`ChatService`) handles empty results via the normal empty-context prompt path.

### Zero retrieval results (Req 5)
When `searchResults.isEmpty()`, `ChatService` no longer returns early. It constructs an empty-context prompt string `"（未找到相关参考文档）"` and passes it to the LLM. The LLM uses its system prompt instruction to respond gracefully. No internal IDs are included in the response.

### SSE connection lifecycle (Req 8)
When an SSE emitter completes, errors, or times out, the `ScheduledFuture.cancel(false)` callback fires, stopping the polling task. The shared executor is unaffected.

### Authentication errors (Req 11)
`AuthUtil.getLoginUserId()` throws `BizException(PARAM_ERROR)` when the user is not authenticated. `UploadServiceImpl.uploadFile()` calls this before any file I/O, so no orphaned storage files are created.

### Max retry exceeded (Req 12)
Documents at max retry are logged at WARN level and skipped. They remain in `FAILED` / `PARSING` state pending manual intervention.

## Testing Strategy

### Dual Testing Approach

These optimizations span configuration, data correctness, service logic, and API format. The appropriate test types are:

- **Property-based tests** (using [jqwik](https://jqwik.net/) for Java): for universally-quantified properties (Properties 1–9 above). Minimum 100 iterations per property.
- **Unit tests** (JUnit 5 + Mockito): for specific examples, error conditions, and smoke checks.
- **Integration tests**: for MQ round-trip (Req 7), Redis TTL (Req 3.5), and SSE header (Req 6.6).

### Property-Based Tests (jqwik)

Each property test is tagged with its property reference:

| Property | Test class | Annotation tag |
|---|---|---|
| P1 Sliding-window | `ChatMessageServicePropertyTest` | `Feature: rag-system-optimization, Property 1: sliding-window context` |
| P2 Null-safe SearchResult | `SearchResultPropertyTest` | `Feature: rag-system-optimization, Property 2: null-safe SearchResult` |
| P3 SSE event order | `ChatServiceSsePropertyTest` | `Feature: rag-system-optimization, Property 3: SSE event ordering` |
| P4 DTO round-trip | `DocumentParseMsgDtoPropertyTest` | `Feature: rag-system-optimization, Property 4: DTO round-trip` |
| P5 Embedding call count | `EmbeddingServicePropertyTest` | `Feature: rag-system-optimization, Property 5: embedding call count` |
| P6 Admin KB bypass | `KnowledgeBaseServicePropertyTest` | `Feature: rag-system-optimization, Property 6: admin KB bypass` |
| P7 Non-owner KB deny | `KnowledgeBaseServicePropertyTest` | `Feature: rag-system-optimization, Property 7: non-owner KB deny` |
| P8 Upload creatorId | `UploadServicePropertyTest` | `Feature: rag-system-optimization, Property 8: upload creatorId` |
| P9 Max retry guard | `DocumentRetrySchedulerPropertyTest` | `Feature: rag-system-optimization, Property 9: max retry guard` |

### Unit / Example Tests

- Req 1: Test `ApplicationContext` fails to load when a required env var is absent.
- Req 2: Verify `application-prod.yaml` sets `log-impl` to `NoLoggingImpl` and root level to `INFO`.
- Req 5: Mock `retrievalService.search()` returning empty; verify LLM is still invoked.
- Req 6: Verify `Content-Type: text/event-stream;charset=UTF-8` header on `/completions/stream`.
- Req 8: Open two mock SSE emitters; close one; verify the other continues polling.
- Req 11: Call `uploadFile()` without a Sa-Token session; verify `BizException` thrown before any `IFileStorageService` call.
- Req 13: Verify `RetrievalService` uses `0.6` default when `sys_config` has no entry for `rag.retrieval.similarity-threshold`.
- Req 14: Verify `GET /api/doc/{id}` returns 404 when called by a non-owner non-admin user.
- Req 15: Compile `DocumentServiceImpl` and verify zero duplicate-import warnings.
