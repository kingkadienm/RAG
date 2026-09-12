package com.wangzs.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.mapper.DocumentMapper;
import com.wangzs.rag.mapper.KnowledgeBaseMapper;
import com.wangzs.rag.mapper.UploadRecordMapper;
import com.wangzs.rag.model.dto.DocumentParseMsgDTO;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.model.entity.KnowledgeBase;
import com.wangzs.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

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
        if (kb == null || Integer.valueOf(1).equals(kb.getDeleted())) {
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
        record.setCheckStatus(1); // 通过
        record.setUploaderId(creatorId);
        record.setDeleted(0); // 未删除
        uploadRecordMapper.insert(record);
        log.info("创建上传记录成功: uploadRecordId={}, docId={}", record.getId(), doc.getId());

        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryParse(Long id) {
        Document doc = getById(id);

        int newVersion = (doc.getVersion() == null ? 0 : doc.getVersion()) + 1;

        // 局部更新，重置状态并递增版本号
        Document updateDoc = new Document();
        updateDoc.setId(id);
        updateDoc.setVersion(newVersion);
        updateDoc.setParseStatus(ParseStatusEnum.INIT);  // 重置为待解析
        updateDoc.setVectorStatus(VectorStatusEnum.INIT); // 重置为待向量化
        updateDoc.setErrorMsg("");     // 清空历史报错
        baseMapper.updateById(updateDoc);

        doc.setVersion(newVersion);

        // 事务提交后再发送 MQ
        executeAfterTransactionCommit(() -> sendParseMessage(doc));

        log.info("触发重新解析文档: id={}, newVersion={}", id, newVersion);
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
        if (doc == null || Integer.valueOf(1).equals(doc.getDeleted())) {
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return doc;
    }

    @Override
    public Page<Document> pageByKbId(Long kbId, int pageNum, int pageSize) {
        Page<Document> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, kbId)
                .eq(Document::getDeleted, 0)
                .orderByDesc(Document::getCreatedTime);

        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public List<Document> listByKbId(Long kbId) {
        return baseMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getDeleted, 0)
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
        doc.setDeleted(1);
        baseMapper.updateById(doc);
        log.info("删除文档: id={}", id);
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
}