# 代码变更日志

## 2026-09-10

### 编译修复

| 文件 | 问题 | 修复 |
|------|------|------|
| `rag/pom.xml` | Tika 依赖 artifactId 错误（`t` → `tika-core`） | 修正依赖坐标 |
| `FileStorageLocalServiceImpl.java` | `FileUtils.forceDelete()` 返回 void，不能赋值给 boolean | 移除赋值，直接调用后返回 true |
| `ParseStrategyFactory.java` | `Collectors.toMap()` 类型推断失败 | 改为 for 循环构建 map |
| `UploadController.java` | 缺少 `DocumentMapper`、`FileUploadVO` 导入 | 补全 import |
| `UploadController.java` | `MessageBuilder.withMap()` 方法不存在 | 改用 `GenericMessage<>` 构造 |

### 文件相关代码变更（适配新 SQL Schema）

| 文件 | 变更 |
|------|------|
| `DocumentMapper.xml` | 新增字段：fileMd5, vectorStatus, vectorCount, errorMsg, version |
| `ChatSessionMapper.xml` | 新增字段：kbId, messageCount |
| `ChatMessageMapper.xml` | references → refChunks, 新增 tokenCount |
| `DocumentService.java` | create() 签名新增 fileMd5；初始化 vectorStatus=0, version=1；新增 updateVectorStatus() |
| `FileCheckService.java` | createUploadRecord() 新增 storageType 参数 |
| `UploadController.java` | 调用 upload() 后回填 doc.filePath 和 record.storedFilename；MQ 消息新增 fileMd5/storageType |
| `DocumentParseConsumer.java` | 完整双状态机：parse_status → vector_status；新增 localUploadPath 配置 |
| `ChatService.java` | 会话绑定 kbId；refChunks 存储参考文档；tokenCount 估算；message_count +2 |
| `RetrievalService.java` | similarityThreshold 改为配置注入（默认 0.7） |

### SQL Schema 更新

| 文件 | 变更 |
|------|------|
| `rag/src/main/resources/sql/schema.sql` | 更新为用户优化的 SQL 脚本，新增双状态机字段（parse_status + vector_status）、file_md5、version、storage_type、ref_chunks、token_count、message_count 等 |
