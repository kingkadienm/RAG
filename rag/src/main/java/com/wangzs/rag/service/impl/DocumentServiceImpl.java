package com.wangzs.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.enums.CheckStatusEnum;
import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.mapper.DocumentMapper;
import com.wangzs.rag.mapper.KnowledgeBaseMapper;
import com.wangzs.rag.mapper.UploadRecordMapper;
import com.wangzs.rag.model.dto.DocumentParseMsgDTO;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.model.entity.KnowledgeBase;
import com.wangzs.rag.model.vector.VectorDocumentChunk;
import com.wangzs.rag.repository.VectorDocumentChunkRepository;
import com.wangzs.rag.service.DocumentService;
import com.wangzs.rag.service.EmbeddingService;
import com.wangzs.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 文档服务实现类（标准 MyBatis-Plus 风格）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    // 直接使用继承自 ServiceImpl 的 baseMapper，无需再定义 private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final UploadRecordMapper uploadRecordMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final EmbeddingService embeddingService;
    private final FileStorageService fileStorageService;
    private final com.wangzs.rag.util.RedisUtil redisUtil;
    private final VectorDocumentChunkRepository vectorDocumentChunkRepository;

    @org.springframework.beans.factory.annotation.Autowired
    @Qualifier("sseStatusExecutor")
    private ScheduledExecutorService sseStatusExecutor;

    @Value("${rag.rocketmq.topic}")
    private String parseTopic;

    @Value("${file.storage.type:local}")
    private String storageType;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Document create(Long kbId, String title, String fileName, String fileType,
                           Long fileSize, String filePath, String fileMd5, Long creatorId) {
        // 1. 校验知识库是否存在
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeleted() == DeletedEnum.YES) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }

        // 2. 构造文档实体
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setTitle(title);
        doc.setFileName(fileName);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc.setFilePath(filePath);
        doc.setFileMd5(fileMd5);
        doc.setParseStatus(ParseStatusEnum.INIT);  // 0-待解析
        doc.setVectorStatus(VectorStatusEnum.INIT); // 0-待向量化
        doc.setChunkCount(0);
        doc.setVectorCount(0);
        doc.setVersion(1);      // 初始版本号为 1
        doc.setCreatorId(creatorId);
        doc.setRetryCount(0);   // 初始重试次数为 0

        baseMapper.insert(doc);
        log.info("创建文档记录成功: id={}, kbId={}, fileName={}, fileMd5={}", doc.getId(), kbId, fileName, fileMd5);

        // 3. 事务提交之后再异步发送 MQ 消息，防止消费端查不到 DB 记录
        executeAfterTransactionCommit(() -> sendParseMessage(doc));

        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Document createWithUploadRecord(Long kbId, String title, String fileName, String fileType,
                                            Long fileSize, String filePath, String fileMd5, Long creatorId,
                                            String storedFilename, String mimeType, String storageType) {
        // 1. 创建 Document（回填 storedFilename）
        Document doc = create(kbId, title, fileName, fileType, fileSize, filePath, fileMd5, creatorId);

        // 2. 创建 UploadRecord（关联 docId）
        com.wangzs.rag.model.entity.UploadRecord record = new com.wangzs.rag.model.entity.UploadRecord();
        record.setKbId(kbId);
        record.setDocId(doc.getId());
        record.setOriginalFilename(fileName);
        record.setStoredFilename(storedFilename);
        record.setFileSize(fileSize);
        record.setFileMd5(fileMd5);
        record.setMimeType(mimeType);
        record.setStorageType(storageType);
        record.setCheckStatus(CheckStatusEnum.PASSED); // 通过
        record.setUploaderId(creatorId);
        record.setDeleted(DeletedEnum.NO); // 未删除
        uploadRecordMapper.insert(record);
        log.info("创建上传记录成功: uploadRecordId={}, docId={}", record.getId(), doc.getId());

        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryParse(Long id) {
        Document doc = getById(id);

        // 清除旧版本去重键，避免残留的 dedup key 阻塞新消息的幂等链路
        int oldVersion = doc.getVersion() == null ? 0 : doc.getVersion();
        String oldDedupKey = "rag:msg:dedup:" + id + ":" + oldVersion;
        redisUtil.delete(oldDedupKey);

        // 清理旧向量（避免重试后残留历史向量）
        embeddingService.deleteVectorsByDocId(id);

        // 判断失败阶段，决定重置范围
        boolean parseFailed = doc.getParseStatus() == ParseStatusEnum.FAILED;
        boolean vectorFailed = doc.getVectorStatus() == VectorStatusEnum.FAILED;

        if (parseFailed) {
            // 解析失败：全量重置，删除旧分块，version+1
            int newVersion = oldVersion + 1;
            vectorDocumentChunkRepository.deleteByDocId(id);

            Document updateDoc = new Document();
            updateDoc.setId(id);
            updateDoc.setVersion(newVersion);
            updateDoc.setParseStatus(ParseStatusEnum.INIT);
            updateDoc.setVectorStatus(VectorStatusEnum.INIT);
            updateDoc.setErrorMsg("");
            updateDoc.setChunkCount(0);
            updateDoc.setVectorCount(0);
            baseMapper.updateById(updateDoc);

            doc.setVersion(newVersion);
            executeAfterTransactionCommit(() -> sendParseMessage(doc));
            log.info("手动重试（解析失败）: id={}, newVersion={}", id, newVersion);

        } else if (vectorFailed) {
            // 向量化失败：只重置向量化阶段，保留分块和 version
            Document updateDoc = new Document();
            updateDoc.setId(id);
            updateDoc.setVectorStatus(VectorStatusEnum.INIT);
            updateDoc.setErrorMsg("");
            // parseStatus 保持 SUCCESS，chunkCount 保持原值，version 不递增
            baseMapper.updateById(updateDoc);

            executeAfterTransactionCommit(() -> sendParseMessage(doc));
            log.info("手动重试（向量化失败）: id={}, version={}", id, oldVersion);
        }
    }

    @Override
    public void sendParseMessage(Document doc) {
        if (doc == null || doc.getId() == null) {
            log.warn("文档对象或 ID 为空，取消发送解析消息");
            return;
        }

        // 1. 构建强类型传输 DTO
        DocumentParseMsgDTO msgDTO = DocumentParseMsgDTO.builder()
                .docId(doc.getId())
                .kbId(doc.getKbId())
                .filePath(doc.getFilePath())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .mimeType("")
                .fileMd5(doc.getFileMd5())
                .storageType(storageType)
                .version(doc.getVersion()) // 透传版本号
                .build();

        // 2. 转换为 Spring Message
        Message<DocumentParseMsgDTO> message = MessageBuilder
                .withPayload(msgDTO)
                .setHeader("docId", String.valueOf(doc.getId()))
                .setHeader("kbId", String.valueOf(doc.getKbId()))
                .setHeader("version", String.valueOf(doc.getVersion()))
                .build();

        // 3. 异步发送消息
        rocketMQTemplate.asyncSend(parseTopic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("文档解析消息发送成功: docId={}, version={}, msgId={}",
                        doc.getId(), doc.getVersion(),
                        sendResult != null ? sendResult.getMsgId() : "UNKNOWN");
            }

            @Override
            public void onException(Throwable ex) {
                log.error("文档解析消息发送失败: docId={}, version={}", doc.getId(), doc.getVersion(), ex);
                updateParseStatus(doc.getId(), ParseStatusEnum.FAILED, 0, "发送 MQ 消息失败: " + truncateErrorMsg(ex.getMessage()));
            }
        });
    }

    @Override
    public Document getById(Long id) {
        Document doc = baseMapper.selectById(id);
        if (doc == null || doc.getDeleted() == DeletedEnum.YES) {
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return doc;
    }

    @Override
    public Page<Document> pageByKbId(Long kbId, int pageNum, int pageSize) {
        Page<Document> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, kbId)
                .eq(Document::getDeleted, DeletedEnum.NO)
                .orderByDesc(Document::getCreatedTime);

        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public List<Document> listByKbId(Long kbId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getDeleted, DeletedEnum.NO)
                        .orderByDesc(Document::getCreatedTime)
        );
    }

    @Override
    public void updateParseStatus(Long id, ParseStatusEnum status, Integer chunkCount, String errorMsg) {
        Document updateDoc = new Document();
        updateDoc.setId(id);
        updateDoc.setParseStatus(status);
        if (chunkCount != null) {
            updateDoc.setChunkCount(chunkCount);
        }
        updateDoc.setErrorMsg(truncateErrorMsg(errorMsg));

        baseMapper.updateById(updateDoc);
        log.info("更新文档解析状态: id={}, parseStatus={}, chunkCount={}", id, status, chunkCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVectorStatus(Long id, VectorStatusEnum status, Integer vectorCount, String errorMsg) {
        Document updateDoc = new Document();
        updateDoc.setId(id);
        updateDoc.setVectorStatus(status);
        if (vectorCount != null) {
            updateDoc.setVectorCount(vectorCount);
        }
        updateDoc.setErrorMsg(truncateErrorMsg(errorMsg));

        baseMapper.updateById(updateDoc);
        log.info("更新文档向量化状态: id={}, vectorStatus={}, vectorCount={}", id, status, vectorCount);
    }

    @Override
    public void updateFilePath(Long id, String filePath) {
        Document updateDoc = new Document();
        updateDoc.setId(id);
        updateDoc.setFilePath(filePath);
        baseMapper.updateById(updateDoc);
        log.info("更新文档存储路径: id={}, filePath={}", id, filePath);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Document doc = getById(id);

        // 清理已入库的向量
        embeddingService.deleteVectorsByDocId(id);
        // 清理 PostgreSQL 分块数据
        vectorDocumentChunkRepository.deleteByDocId(id);

        doc.setDeleted(DeletedEnum.YES);
        baseMapper.updateById(doc);
        log.info("删除文档及关联数据: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKbId(Long kbId) {
        baseMapper.deleteByKbId(kbId);
        log.info("批量删除知识库下的文档: kbId={}", kbId);
    }

    @Override
    public void incrementRetryCount(Long id) {
        baseMapper.incrementRetryCount(id);
        log.info("递增文档重试次数: docId={}", id);
    }

    /**
     * 辅助工具：保证任务在 Spring 事务 Commit 后再执行
     */
    private void executeAfterTransactionCommit(Runnable task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 异常文本截断保护，防止存库超长引发 SQL 报错
     */
    private String truncateErrorMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) + "..." : msg;
    }

    @Override
    public java.util.Map<String, Object> getParseProgress(Long id) {
        Document doc = getById(id);
        ParseStatusEnum parseStatus = doc.getParseStatus();
        String errorMsg = doc.getErrorMsg();

        int progress = switch (parseStatus) {
            case INIT -> 0;
            case PARSING -> 30;
            case SUCCESS -> 80;
            case FAILED -> -1;
            default -> 0;
        };

        String status = switch (parseStatus) {
            case SUCCESS -> "parsed";
            case PARSING -> "parsing";
            case FAILED -> "failed";
            default -> "pending";
        };

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("progress", progress);
        result.put("status", status);
        result.put("message", errorMsg);
        return result;
    }

    @Override
    public List<Chunk> getChunks(Long id) {
        Document doc = getById(id);

        if (doc.getParseStatus() == ParseStatusEnum.SUCCESS) {
            List<VectorDocumentChunk> entities = vectorDocumentChunkRepository.findByDocId(id);
            if (!entities.isEmpty()) {
                return toChunks(entities);
            }
        }

        if (doc.getParseStatus() != ParseStatusEnum.SUCCESS) {
            throw BizException.of(ErrorCode.DOCUMENT_PARSE_FAILED.getCode(),
                    "文档尚未解析完成，无法查看分块");
        }

        throw BizException.of(ErrorCode.DOCUMENT_CHUNK_FAILED.getCode(),
                "分块数据缺失");
    }

    /**
     * VectorDocumentChunk 列表转换为业务 Chunk 列表
     */
    private List<Chunk> toChunks(List<VectorDocumentChunk> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(e -> Chunk.builder()
                        .index(e.getChunkIndex())
                        .content(e.getContent())
                        .length(e.getMetadata() != null ?
                                ((Number) e.getMetadata().getOrDefault("char_length", 0)).intValue() : 0)
                        .documentId(e.getDocId())
                        .kbId(e.getKbId())
                        .title(e.getMetadata() != null ?
                                (String) e.getMetadata().get("title") : null)
                        .sectionPath(e.getMetadata() != null ?
                                (String) e.getMetadata().get("section_path") : null)
                        .tokenCount(e.getMetadata() != null ?
                                ((Number) e.getMetadata().getOrDefault("token_count", 0)).intValue() : 0)
                        .embeddingText(e.getMetadata() != null ?
                                (String) e.getMetadata().get("embedding_text") : null)
                        .build())
                .toList();
    }

    @Override
    public void deleteWithFile(Long id) {
        Document doc = getById(id);
        // 清理物理文件
        if (doc.getFilePath() != null) {
            try {
                fileStorageService.delete(doc.getFilePath());
            } catch (Exception e) {
                log.warn("删除物理文件失败: {}", doc.getFilePath(), e);
            }
        }
        // 清理数据库记录（含向量和分块）
        delete(id);
    }

    @Override
    public void streamParseStatus(Long id, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        Runnable task = () -> {
            try {
                Document currentDoc = getById(id);
                if (currentDoc == null) {
                    emitter.complete();
                    return;
                }

                Map<String, Object> progressData = getParseProgress(id);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("status")
                        .data(progressData));

                String stage = (String) progressData.get("stage");
                if ("completed".equals(stage) || "parse_failed".equals(stage) || "vector_failed".equals(stage)) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.debug("SSE 流结束或发送失败: docId={}", id, e);
                emitter.complete();
            }
        };

        ScheduledFuture<?> future = sseStatusExecutor.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(false));
        emitter.onError(e -> future.cancel(false));
        emitter.onTimeout(() -> future.cancel(false));
    }
}