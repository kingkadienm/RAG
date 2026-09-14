# Requirements Document

## Introduction

This document defines the optimization requirements for the RAG knowledge base Q&A system built on Spring Boot 3.5, Java 21, and Spring AI Alibaba. Code analysis has identified 15 issues spanning security vulnerabilities, data correctness bugs, production-readiness gaps, and missing API endpoints. Each requirement targets a specific defect and defines measurable acceptance criteria using EARS patterns.

## Glossary

- **System**: The RAG knowledge base Q&A application (backend service).
- **ChatService**: The service responsible for RAG conversation orchestration.
- **ChatMessageService**: The service responsible for persisting and retrieving chat messages.
- **RetrievalService**: The service responsible for vector similarity search against PGVector.
- **EmbeddingService**: The service responsible for vectorizing document chunks and storing them.
- **DocumentService**: The service managing document lifecycle (parse, vectorize, delete).
- **DocumentRetryScheduler**: The scheduled task that retries failed document processing.
- **UploadService**: The service orchestrating file upload, validation, and record creation.
- **ConfigService**: The service that reads dynamic configuration from the sys_config database table.
- **RedisUtil**: The utility class wrapping Redis operations.
- **SessionContext**: The Redis-cached list of recent ChatMessage objects keyed by session ID (key pattern: chat:ctx:{sessionId}).
- **EARS**: Easy Approach to Requirements Syntax — a structured pattern for writing verifiable requirements.
- **SSE**: Server-Sent Events — the HTTP streaming protocol used for real-time responses.
- **RocketMQTemplate**: The Spring-managed RocketMQ producer wrapper.
- **DocumentParseMsgDTO**: The strongly-typed DTO used as the canonical message body for document parse events.
- **SearchResult**: The record type returned by RetrievalService representing one retrieved document chunk.
- **ThreadPoolTaskScheduler**: Spring-managed thread pool scheduler used inside streamParseStatus.
- **Sa-Token**: The authentication/authorization framework used by the System.
- **Admin**: A user with role value 1 in sys_user.role.

## Requirements

### Requirement 1: Externalize Sensitive Configuration

**User Story:** As a system operator, I want all passwords, API keys, and secret keys to be loaded from environment variables or a secrets manager, so that credentials are never committed to version control and production deployments remain secure.

#### Acceptance Criteria

1. THE System SHALL load all database passwords (MySQL, PostgreSQL, Redis) from environment variables rather than from hardcoded values in application.yaml.
2. THE System SHALL load the Sa-Token JWT secret key from an environment variable rather than from the hardcoded value "rag-secret-key-change-in-production".
3. THE System SHALL load the DashScope API key from an environment variable (the existing `${AI_DASHSCOPE_API_KEY}` pattern is correct and SHALL be preserved).
4. WHEN the System starts and a required environment variable is absent, THE System SHALL fail fast with a descriptive startup error identifying the missing variable.
5. THE System SHALL provide a documented `.env.example` file listing every required environment variable with placeholder values.

---

### Requirement 2: Production-Safe Logging Configuration

**User Story:** As a system operator, I want log verbosity to be appropriate for the deployment environment, so that SQL statements and sensitive data are not leaked to logs in production.

#### Acceptance Criteria

1. THE System SHALL define separate Spring profiles (e.g., `dev` and `prod`) for logging configuration.
2. WHILE the `prod` profile is active, THE System SHALL set the MyBatis-Plus log implementation to a no-op or SLF4J adapter instead of `StdOutImpl`, preventing SQL statement output.
3. WHILE the `prod` profile is active, THE System SHALL set the root log level to INFO or above, preventing DEBUG and TRACE output from Spring AI and JDBC components.
4. WHILE the `dev` profile is active, THE System SHALL retain the current DEBUG/TRACE logging configuration unchanged.

---

### Requirement 3: Sliding-Window Session Context in Redis

**User Story:** As a user, I want the AI assistant to remember more than one previous exchange in the current conversation, so that follow-up questions are answered with awareness of prior context.

#### Acceptance Criteria

1. WHEN ChatMessageService saves a new user message and assistant reply, THE System SHALL append both messages to the existing SessionContext list rather than replacing it.
2. THE System SHALL enforce a configurable sliding-window limit on the SessionContext list (default: 10 messages, i.e., 5 exchange pairs), removing the oldest messages when the limit is exceeded.
3. THE System SHALL read the sliding-window limit from ConfigService using the key `rag.chat.context-window-size` with a default of 10.
4. WHEN ChatService builds the prompt, THE System SHALL read all messages in the SessionContext list and include them as conversation history.
5. THE System SHALL maintain the Redis TTL of the SessionContext at 1800 seconds after each write, resetting the expiry on each append.

---

### Requirement 4: Null-Safe SearchResult Metadata Parsing

**User Story:** As a developer, I want vector search results to be parsed safely even when metadata fields are missing, so that a single malformed chunk does not crash the retrieval pipeline.

#### Acceptance Criteria

1. WHEN RetrievalService constructs a SearchResult from a Spring AI Document, THE System SHALL return null (not throw NullPointerException) for numeric metadata fields (doc_id, kb_id, chunk_index, chunk_total) when the field is absent from metadata.
2. WHEN RetrievalService constructs a SearchResult from a Spring AI Document, THE System SHALL return null for string metadata fields (title, file_name, file_type) when the field is absent from metadata.
3. WHEN the score field on the Spring AI Document is null, THE System SHALL set SearchResult.score to null and downstream code SHALL treat null score as 0.0 for display purposes.
4. IF a NullPointerException occurs during SearchResult construction, THEN THE System SHALL log a warning with the document ID and return an empty result list for that search invocation rather than propagating the exception.

---

### Requirement 5: Remove Debug Code and Sanitize User-Facing Error Messages

**User Story:** As a product owner, I want debug information (internal IDs, thresholds, technical hints) to be absent from responses returned to end users, so that the application behaves as a production service.

#### Acceptance Criteria

1. THE System SHALL remove the TODO debug guard blocks from ChatService.chat() and ChatService.chatStream() that return raw technical diagnostic text to the caller.
2. WHEN vector retrieval returns zero results, THE System SHALL return a user-friendly message such as "未在知识库中找到与您问题相关的内容，请尝试换一种提问方式或确认文档已上传。" without including internal fields such as kbId, threshold values, or stack traces.
3. THE System SHALL not include any internal identifiers (database IDs, configuration keys, class names) in HTTP response bodies returned to clients.
4. IF vector retrieval returns zero results, THEN THE System SHALL still invoke the LLM with the empty-context prompt so the model can respond gracefully, unless the system prompt explicitly instructs otherwise.

---

### Requirement 6: Standardize SSE Stream Event Format

**User Story:** As a frontend developer, I want the SSE stream to use standard event fields so that the client can reliably parse structured data without fragile string-splitting.

#### Acceptance Criteria

1. THE System SHALL use the SSE `event:` field to distinguish event types in the chat stream, replacing the in-band text markers `[REF_CHUNKS:...]`, `[SESSION_ID:...]`, and `[DONE]`.
2. THE System SHALL emit a named SSE event `ref_chunks` carrying the reference document JSON as the data payload before the first text token.
3. THE System SHALL emit text content tokens as SSE events with event name `message` (or no event name, using the default).
4. THE System SHALL emit a named SSE event `session_id` carrying the session ID string after the last text token.
5. THE System SHALL emit a named SSE event `done` with empty or null data as the final event to signal stream completion.
6. THE System SHALL set the SSE `Content-Type` response header to `text/event-stream;charset=UTF-8`.

---

### Requirement 7: Unify RocketMQ Message Format in DocumentRetryScheduler

**User Story:** As a developer, I want all document parse messages to use the same strongly-typed DTO format, so that the consumer can deserialize every retry message correctly.

#### Acceptance Criteria

1. THE System SHALL replace the raw-byte Map-based message sending in DocumentRetryScheduler.autoRetryFailedDocuments() with RocketMQTemplate sending a DocumentParseMsgDTO, matching the format used by DocumentServiceImpl.sendParseMessage().
2. THE System SHALL remove the use of DefaultMQProducer directly within DocumentRetryScheduler; all MQ sends SHALL go through RocketMQTemplate.
3. WHEN DocumentRetryScheduler sends a retry message, THE System SHALL populate DocumentParseMsgDTO with the same fields (docId, kbId, filePath, fileName, fileType, fileMd5, storageType, version) as the initial parse message.
4. THE System SHALL verify that DocumentParseConsumer can deserialize messages sent by DocumentRetryScheduler without deserialization errors.

---

### Requirement 8: Fix ThreadPoolTaskScheduler Leak in streamParseStatus

**User Story:** As a system operator, I want SSE parse-status streams to use a shared thread pool, so that concurrent document status subscriptions do not create unbounded threads.

#### Acceptance Criteria

1. THE System SHALL replace the per-request `new ThreadPoolTaskScheduler()` in DocumentServiceImpl.streamParseStatus() with a Spring-managed, application-scoped scheduled executor.
2. THE System SHALL declare the shared scheduler as a Spring bean with a bounded thread pool size configurable via application.yaml (default: 4 threads).
3. WHEN an SSE connection is closed (completion, error, or timeout), THE System SHALL cancel the associated polling task without shutting down the shared scheduler.
4. WHEN the application shuts down, THE System SHALL gracefully terminate the shared scheduler after all in-flight tasks complete.

---

### Requirement 9: Eliminate Redundant Embedding Computation in EmbeddingService

**User Story:** As a developer, I want the embedding pipeline to compute each vector exactly once per chunk, so that API quota and latency are not wasted on duplicate calls.

#### Acceptance Criteria

1. THE System SHALL pass the pre-computed embedding vectors from EmbeddingModel.embed() directly into the Spring AI Document objects rather than discarding them and letting VectorStore.add() recompute them.
2. THE System SHALL use the Spring AI Document constructor or builder that accepts an explicit embedding vector when one is available.
3. WHEN embeddings are pre-supplied, THE System SHALL not make a second call to the embedding model for the same batch.

---

### Requirement 10: Admin Role Bypass for Knowledge Base Ownership Verification

**User Story:** As an administrator, I want to be able to view and manage all knowledge bases regardless of ownership, so that I can perform support and maintenance tasks.

#### Acceptance Criteria

1. WHEN KnowledgeBaseService.verifyOwnership() is called and the requesting user has Admin role (sys_user.role = 1), THE System SHALL skip the ownership check and allow the operation to proceed.
2. WHEN KnowledgeBaseService.verifyOwnership() is called and the requesting user is not the creator and is not Admin, THE System SHALL throw a KNOWLEDGE_BASE_NOT_FOUND exception (preserving the existing security-through-obscurity pattern).
3. THE System SHALL retrieve the current user role via Sa-Token without making additional database queries beyond the existing user session.

---

### Requirement 11: Populate creatorId from Authenticated User in UploadService

**User Story:** As an auditor, I want uploaded documents to be attributed to the actual uploading user, so that ownership and access control are correctly enforced.

#### Acceptance Criteria

1. WHEN UploadServiceImpl.uploadFile() creates a document record, THE System SHALL retrieve the currently authenticated user ID from Sa-Token and pass it as creatorId instead of the hardcoded value 0L.
2. IF the user is not authenticated when uploadFile() is called, THEN THE System SHALL throw an authentication error before attempting any file operations.

---

### Requirement 12: Maximum Retry Limit in retryStuckDocuments

**User Story:** As a system operator, I want stuck document retries to stop after the configured maximum number of attempts, so that permanently broken documents do not consume resources indefinitely.

#### Acceptance Criteria

1. WHEN DocumentRetryScheduler.retryStuckDocuments() identifies a document that has been in PARSING status for more than 30 minutes, THE System SHALL check that the document's retryCount is less than the configured maximum before scheduling a retry.
2. IF the document's retryCount has reached or exceeded the configured maximum (rag.retry.max-attempts), THEN THE System SHALL log a warning and skip the document without sending a new MQ message.
3. THE System SHALL read the maximum retry limit from ConfigService using the key `rag.retry.max-attempts` with a default of 3, consistent with autoRetryFailedDocuments().

---

### Requirement 13: Clarify and Fix similarity-threshold Configuration

**User Story:** As a developer, I want the similarity threshold configuration to be unambiguous and reflect the actual runtime value, so that search behavior is predictable and tunable.

#### Acceptance Criteria

1. THE System SHALL remove the commented-out `similarity-threshold: 0.0` line and its associated misleading comment from application.yaml.
2. THE System SHALL add an explicit, uncommented `rag.retrieval.similarity-threshold` entry in application.yaml with the intended default value (0.6).
3. THE System SHALL ensure ConfigService returns the value from the database if present, or falls back to the application.yaml default value, with no contradiction between the two sources.
4. THE System SHALL document the effective similarity threshold value in a comment adjacent to the configuration entry.

---

### Requirement 14: Implement Missing Document and Session API Endpoints

**User Story:** As a frontend developer, I want the backend to expose document detail, chunk listing, and session management endpoints, so that the UI features that already call these endpoints do not return 404 errors.

#### Acceptance Criteria

1. THE System SHALL expose `GET /api/doc/{id}` returning document metadata (id, kbId, fileName, fileType, fileSize, parseStatus, vectorStatus, chunkCount, vectorCount, createdTime, errorMsg) as a JSON object wrapped in ApiResult.
2. THE System SHALL expose `GET /api/doc/{id}/chunks` returning the list of parsed chunks for the document (chunkIndex, content, length, tokenCount) as a JSON array wrapped in ApiResult.
3. IF `GET /api/doc/{id}/chunks` is called for a document whose parseStatus is not SUCCESS, THEN THE System SHALL return a 400 error with message "文档尚未解析完成，无法查看分块".
4. THE System SHALL verify that ChatSessionController exposes `GET /api/chat/sessions`, `GET /api/chat/sessions/{sessionId}/messages`, and `DELETE /api/chat/sessions/{sessionId}`, creating any missing handler methods.
5. WHEN `DELETE /api/chat/sessions/{sessionId}` is called, THE System SHALL verify that the requesting user owns the session before deleting it.
6. WHEN `GET /api/doc/{id}` or `GET /api/doc/{id}/chunks` is called for a document the requesting user did not upload and the user is not Admin, THE System SHALL return a 404 error.

---

### Requirement 15: Remove Duplicate Import in DocumentServiceImpl

**User Story:** As a developer, I want the codebase to be free of trivial compile warnings, so that meaningful warnings are not buried in noise.

#### Acceptance Criteria

1. THE System SHALL have exactly one import statement for `com.wangzs.rag.chunk.Chunk` in DocumentServiceImpl.java.
2. THE System SHALL compile without any duplicate-import warnings in the DocumentServiceImpl class.
