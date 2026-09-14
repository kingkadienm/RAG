# Implementation Plan: RAG System Optimization

## Overview

This plan converts the 15-requirement optimization spec into discrete coding tasks. Each top-level group corresponds to one or more closely related requirements. Sub-tasks are sized to touch 1–3 files each so a single agent can complete them independently.

Tasks marked `*` are optional test tasks that can be skipped for a faster MVP pass. The dependency graph at the bottom ensures parallel execution where possible.

## Tasks

- [x] 1. Externalize sensitive configuration (Req 1)
  - [x] 1.1 Replace hardcoded credentials in application.yaml with environment variable references
    - Replace MySQL password with `${MYSQL_PASSWORD}`
    - Replace Redis password with `${REDIS_PASSWORD}`
    - Replace PostgreSQL password with `${POSTGRES_PASSWORD}`
    - Replace Sa-Token jwt-secret-key with `${SA_TOKEN_JWT_SECRET}`
    - Preserve the existing `${AI_DASHSCOPE_API_KEY}` pattern unchanged
    - _Files: `rag/src/main/resources/application.yaml`_
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 1.2 Create .env.example file documenting all required environment variables
    - List every required env var with placeholder values and a short description
    - _Files: `.env.example` (new file at project root)_
    - _Requirements: 1.4, 1.5_

- [x] 2. Production-safe logging profiles (Req 2)
  - [x] 2.1 Create application-dev.yaml and application-prod.yaml, remove logging config from base application.yaml
    - Move existing DEBUG/TRACE logging block into `application-dev.yaml` unchanged
    - Create `application-prod.yaml` with `log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl` and root log level INFO
    - Add `spring.profiles.active: prod` as default in base `application.yaml`
    - Remove the `mybatis-plus.configuration.log-impl` and `logging` sections from base `application.yaml`
    - _Files: `rag/src/main/resources/application.yaml`, new `application-dev.yaml`, new `application-prod.yaml`_
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 3. Fix similarity-threshold configuration (Req 13)
  - [x] 3.1 Remove commented-out similarity-threshold line and add explicit uncommented entry
    - Delete the `#    similarity-threshold: 0.0   # ⚠️ 临时设置...` comment block
    - Add `similarity-threshold: 0.6` with a bilingual comment explaining the DB-override fallback
    - _Files: `rag/src/main/resources/application.yaml`_
    - _Requirements: 13.1, 13.2, 13.3, 13.4_

- [x] 4. Fix sliding-window session context in Redis (Req 3)
  - [x] 4.1 Rewrite ChatMessageService.saveMessages() to append-and-trim instead of replace
    - Read existing list from Redis (key `chat:ctx:{sessionId}`)
    - Append new userMsg and assistantMsg to existing list
    - Read window size via `configService.getInt("rag.chat.context-window-size", 10)`
    - Trim list to last `windowSize` elements when over limit
    - Write back with 1800 s TTL
    - _Files: `rag/src/main/java/com/wangzs/rag/service/ChatMessageService.java`_
    - _Requirements: 3.1, 3.2, 3.3, 3.5_
  - [ ]* 4.2 Write property test for sliding-window context
    - **Property 1: Sliding-window context does not exceed configured window size**
    - **Validates: Requirements 3.1, 3.2**
    - _Files: new `ChatMessageServicePropertyTest.java` (test sources)_

- [x] 5. Fix null-safe SearchResult metadata parsing (Req 4)
  - [x] 5.1 Add null-safe helper methods and rewrite SearchResult.from() in RetrievalService
    - Add private static `getLong()`, `getInt()`, `getString()` helpers with null/type-mismatch safety
    - Rewrite `SearchResult.from(doc)` to use these helpers
    - Wrap `search()` stream mapping in try-catch; on exception log WARN with doc ID and return empty list
    - _Files: `rag/src/main/java/com/wangzs/rag/service/RetrievalService.java`_
    - _Requirements: 4.1, 4.2, 4.3, 4.4_
  - [ ]* 5.2 Write property test for null-safe SearchResult construction
    - **Property 2: Null-safe SearchResult construction never throws**
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - _Files: new `SearchResultPropertyTest.java` (test sources)_

- [x] 6. Remove debug guards and sanitize error messages (Req 5)
  - [x] 6.1 Delete TODO debug guard blocks from ChatService.chat() and chatStream()
    - Remove the `if (searchResults.isEmpty()) { … "调试提示：kbId=…" }` block from `chat()`
    - Remove the equivalent early-return block from `chatStream()`
    - Ensure both methods continue to the LLM call when results are empty
    - _Files: `rag/src/main/java/com/wangzs/rag/service/ChatService.java`_
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

- [x] 7. Checkpoint — ensure unit tests pass before SSE refactor
  - Run `./mvnw test -pl rag -Dtest="ChatMessageService*,RetrievalService*,ChatService*"` and confirm no failures.
  - Ask the user if any issues arise before proceeding.

- [x] 8. Standardise SSE stream event format (Req 6)
  - [x] 8.1 Refactor ChatService.chatStream() to return Flux<ServerSentEvent<String>>
    - Change return type to `Flux<ServerSentEvent<String>>`
    - Replace `[REF_CHUNKS:…]` string with `ServerSentEvent` with `event("ref_chunks")`
    - Replace raw text tokens with `ServerSentEvent` with `event("message")`
    - Replace `[SESSION_ID:…]` string with `ServerSentEvent` with `event("session_id")`
    - Replace `[DONE]` string with `ServerSentEvent` with `event("done")`
    - Add private `sse(String eventName, String data)` builder helper
    - _Files: `rag/src/main/java/com/wangzs/rag/service/ChatService.java`_
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  - [x] 8.2 Update ChatController.completionsStream() to match new return type
    - Change return type to `Flux<ServerSentEvent<String>>`
    - Set `produces = MediaType.TEXT_EVENT_STREAM_VALUE`
    - _Files: `rag/src/main/java/com/wangzs/rag/controller/ChatController.java`_
    - _Requirements: 6.6_
  - [ ]* 8.3 Write property test for SSE event ordering
    - **Property 3: SSE stream event ordering is always ref_chunks → message* → session_id → done**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**
    - _Files: new `ChatServiceSsePropertyTest.java` (test sources)_

- [x] 9. Unify RocketMQ message format in DocumentRetryScheduler (Req 7)
  - [x] 9.1 Remove private sendParseMessage() from DocumentRetryScheduler and delegate to DocumentService
    - Delete the `private void sendParseMessage(Document doc, int attempt)` method
    - Remove `DefaultMQProducer`, `ObjectMapper` field injections from `DocumentRetryScheduler`
    - Replace all call sites (`autoRetryFailedDocuments`, `retryStuckDocuments`) with `documentService.sendParseMessage(doc)`
    - _Files: `rag/src/main/java/com/wangzs/rag/service/DocumentRetryScheduler.java`_
    - _Requirements: 7.1, 7.2, 7.3_
  - [ ]* 9.2 Write property test for DocumentParseMsgDTO JSON round-trip
    - **Property 4: DocumentParseMsgDTO round-trip preserves all fields**
    - **Validates: Requirements 7.3, 7.4**
    - _Files: new `DocumentParseMsgDtoPropertyTest.java` (test sources)_

- [x] 10. Fix ThreadPoolTaskScheduler leak in streamParseStatus (Req 8)
  - [x] 10.1 Declare shared ScheduledExecutorService bean in config
    - Add `@Bean(name = "sseStatusExecutor", destroyMethod = "shutdown")` method returning `Executors.newScheduledThreadPool(poolSize)`
    - Read pool size from `@Value("${rag.sse.status-pool-size:4}")`
    - Add `rag.sse.status-pool-size: 4` entry to `application.yaml`
    - _Files: `rag/src/main/java/com/wangzs/rag/config/AppConfig.java` (new or existing config class), `rag/src/main/resources/application.yaml`_
    - _Requirements: 8.1, 8.2_
  - [x] 10.2 Refactor DocumentServiceImpl.streamParseStatus() to use shared executor
    - Inject `ScheduledExecutorService sseStatusExecutor` via constructor
    - Replace `new ThreadPoolTaskScheduler()` creation + initialization with `sseStatusExecutor.scheduleAtFixedRate(...)`
    - Store returned `ScheduledFuture<?>` and call `future.cancel(false)` in `onCompletion`, `onError`, `onTimeout` callbacks
    - _Files: `rag/src/main/java/com/wangzs/rag/service/impl/DocumentServiceImpl.java`_
    - _Requirements: 8.3, 8.4_

- [x] 11. Eliminate redundant embedding computation (Req 9)
  - [x] 11.1 Set pre-computed embeddings on Document objects before calling vectorStore.add()
    - After `embeddingModel.embed(contents)` returns `List<float[]> embeddings`
    - For each Document in the batch, convert `float[]` to `List<Double>` and call `vectorDoc.setEmbedding(embeddingList)`
    - Ensure this happens before `vectorStore.add(vectorDocs)`
    - _Files: `rag/src/main/java/com/wangzs/rag/service/EmbeddingService.java`_
    - _Requirements: 9.1, 9.2, 9.3_
  - [ ]* 11.2 Write property test for embedding call count
    - **Property 5: Embedding model called exactly once per batch**
    - **Validates: Requirements 9.1, 9.3**
    - _Files: new `EmbeddingServicePropertyTest.java` (test sources)_

- [x] 12. Admin role bypass for KB ownership (Req 10)
  - [x] 12.1 Add UserMapper injection and admin bypass logic to KnowledgeBaseService.verifyOwnership()
    - Inject `UserMapper userMapper` into `KnowledgeBaseService`
    - At the start of `verifyOwnership()`, load current user via `userMapper.selectById(currentUserId)`
    - If `user.getRole() == UserRoleEnum.ADMIN`, return `kb` immediately without the ownership check
    - _Files: `rag/src/main/java/com/wangzs/rag/service/KnowledgeBaseService.java`_
    - _Requirements: 10.1, 10.2, 10.3_
  - [ ]* 12.2 Write property tests for admin bypass and non-owner deny
    - **Property 6: Admin users always pass KB ownership verification**
    - **Property 7: Non-admin non-owner users always fail KB ownership verification**
    - **Validates: Requirements 10.1, 10.2**
    - _Files: new `KnowledgeBaseServicePropertyTest.java` (test sources)_

- [x] 13. Populate creatorId from authenticated user in UploadService (Req 11)
  - [x] 13.1 Replace hardcoded 0L with AuthUtil.getLoginUserId() in UploadServiceImpl.uploadFile()
    - Add `Long creatorId = AuthUtil.getLoginUserId();` before the `documentService.createWithUploadRecord(...)` call
    - Replace the `0L` argument with `creatorId`
    - _Files: `rag/src/main/java/com/wangzs/rag/service/impl/UploadServiceImpl.java`_
    - _Requirements: 11.1, 11.2_
  - [ ]* 13.2 Write property test for upload creatorId attribution
    - **Property 8: Uploaded document creatorId equals the authenticated user ID**
    - **Validates: Requirements 11.1**
    - _Files: new `UploadServicePropertyTest.java` (test sources)_

- [x] 14. Add max-retry guard to retryStuckDocuments (Req 12)
  - [x] 14.1 Add retryCount < maxAttempts check in DocumentRetryScheduler.retryStuckDocuments()
    - Read `maxAttempts = configService.getInt("rag.retry.max-attempts", 3)` inside the loop
    - Before any status update or `sendParseMessage()` call, check `retryCount >= maxAttempts` and log WARN + continue if true
    - _Files: `rag/src/main/java/com/wangzs/rag/service/DocumentRetryScheduler.java`_
    - _Requirements: 12.1, 12.2, 12.3_
  - [ ]* 14.2 Write property test for max-retry guard
    - **Property 9: Stuck documents at max retries are never retried again**
    - **Validates: Requirements 12.1, 12.2**
    - _Files: new `DocumentRetrySchedulerPropertyTest.java` (test sources)_

- [x] 15. Add document ownership verification to DocumentController (Req 14)
  - [x] 15.1 Inject UserMapper into DocumentController and add verifyDocumentAccess() helper
    - Inject `UserMapper userMapper` into `DocumentController`
    - Add private `verifyDocumentAccess(Document doc)` that checks `currentUserId == doc.creatorId` or `user.role == ADMIN`; throws `BizException(DOCUMENT_NOT_FOUND)` on failure
    - Call `verifyDocumentAccess(doc)` in `getById()` and `getChunks()` after loading the document
    - _Files: `rag/src/main/java/com/wangzs/rag/controller/DocumentController.java`_
    - _Requirements: 14.6_

- [x] 16. Remove duplicate import in DocumentServiceImpl (Req 15)
  - [x] 16.1 Delete the duplicate import statement for com.wangzs.rag.chunk.Chunk
    - Keep exactly one `import com.wangzs.rag.chunk.Chunk;` line
    - _Files: `rag/src/main/java/com/wangzs/rag/service/impl/DocumentServiceImpl.java`_
    - _Requirements: 15.1, 15.2_

- [x] 17. Final checkpoint — ensure all tests pass
  - Run `./mvnw test -pl rag` and confirm zero test failures.
  - Run `./mvnw compile -pl rag` and confirm zero duplicate-import warnings.
  - Ask the user if any issues arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster delivery
- Tasks 1.1, 1.2, 2.1, 3.1, 16.1 are safe to run in parallel (different files)
- Tasks 8.1 and 8.2 are sequential (controller depends on service return type)
- Tasks 9.1 and 14.1 both modify `DocumentRetryScheduler.java` — run sequentially
- Tasks 10.1 and 10.2 are sequential (DocumentServiceImpl needs the bean to exist first)
- Property tests require jqwik on the test classpath; add `net.jqwik:jqwik:1.8.4` to `pom.xml` test scope if not already present



## Task Dependency Graph

Notes:
- Tasks 9.1 and 14.1 both modify DocumentRetryScheduler.java — placed in separate waves to avoid conflicts.
- Task 10.2 depends on the bean declared in task 10.1 — placed in a later wave.
- Task 8.2 depends on the return-type change in task 8.1 — placed in a later wave.
- All property test sub-tasks (*) are placed after their implementation counterparts.

```json
{
  "waves": [
    {
      "id": 0,
      "tasks": ["1.1", "1.2", "2.1", "3.1", "5.1", "6.1", "16.1"]
    },
    {
      "id": 1,
      "tasks": ["4.1", "8.1", "9.1", "10.1", "11.1", "12.1", "13.1", "15.1"]
    },
    {
      "id": 2,
      "tasks": ["4.2", "5.2", "8.2", "9.2", "10.2", "11.2", "12.2", "13.2"]
    },
    {
      "id": 3,
      "tasks": ["8.3", "14.1"]
    },
    {
      "id": 4,
      "tasks": ["14.2"]
    }
  ]
}
```
