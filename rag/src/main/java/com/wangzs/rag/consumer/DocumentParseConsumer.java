package com.wangzs.rag.consumer;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.model.dto.DocumentParseMsgDTO;
import com.wangzs.rag.model.entity.Document;

import com.wangzs.rag.service.ConfigService;
import com.wangzs.rag.service.DocumentService;
import com.wangzs.rag.service.EmbeddingService;
import com.wangzs.rag.service.FileParseService;
import com.wangzs.rag.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 文档解析 RocketMQ 消费者
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${rag.rocketmq.topic}",
        consumerGroup = "${rag.rocketmq.consumer-group}"
)
@RequiredArgsConstructor
public class DocumentParseConsumer implements RocketMQListener<DocumentParseMsgDTO> {

    private final FileParseService fileParseService;
    private final EmbeddingService embeddingService;
    private final DocumentService documentService;
    private final com.wangzs.rag.service.DocumentChunkService chunkService;
    private final S3Client s3Client;
    private final ConfigService configService;
    private final RedisUtil redisUtil;

    @Value("${file.storage.type:local}")
    private String storageType;

    private static final String USER_DIR_PLACEHOLDER = "${user.dir}";
    private static final String USER_DIR_VALUE = System.getProperty("user.dir");


    private String getLocalUploadPath() {
        String rawPath = configService.getString("file.storage.local.upload-path", "${user.dir}/rag-uploads/");
        return resolveUserDir(rawPath);
    }

    private String resolveUserDir(String value) {
        if (value == null) return null;
        if (value.contains(USER_DIR_PLACEHOLDER)) {
            return value.replace(USER_DIR_PLACEHOLDER, USER_DIR_VALUE);
        }
        return value;
    }

    @Override
    public void onMessage(DocumentParseMsgDTO message) {
        if (message == null || message.getDocId() == null) {
            log.warn("收到无效的解析消息: {}", message);
            return;
        }

        Long docId = message.getDocId();
        Integer msgVersion = message.getVersion();
        log.info("开始处理文档解析: docId={}, msgVersion={}", docId, msgVersion);

        // 0. 幂等去重：同一 (docId, version) 的消息只处理一次
        String dedupKey = "rag:msg:dedup:" + docId + ":" + msgVersion;
        if (redisUtil.hasKey(dedupKey)) {
            log.warn("忽略重复解析消息: docId={}, msgVersion={}", docId, msgVersion);
            return;
        }
        // 原子标记（SETNX），TTL 24h 防止 Redis 内存泄漏
        Boolean marked = redisUtil.setIfAbsent(dedupKey, "1", 86400);
        if (marked == null || !marked) {
            log.warn("去重标记设置失败（并发冲突），跳过处理: docId={}, msgVersion={}", docId, msgVersion);
            return;
        }

        // 2. 版本比对，实现严格幂等（拦截乱序或过时消息）
        Document doc;
        try {
            doc = documentService.getById(docId);
        } catch (Exception e) {
            log.warn("文档不存在或已删除，跳过解析: docId={}", docId);
            return;
        }

        // 3. 比较消息版本与当前文档版本
        if (msgVersion != null && doc.getVersion() != null && msgVersion < doc.getVersion()) {
            log.warn("忽略历史过时解析消息: docId={}, msgVersion={}, currentVersion={}",
                    docId, msgVersion, doc.getVersion());
            return;
        }

        boolean parseSuccess = false;
        int chunkCount = 0;

        try {
            // ---------------- 阶段一：解析与分块 ----------------
            List<Chunk> chunks;

            if (doc.getParseStatus() == ParseStatusEnum.SUCCESS) {
                // 重试场景：分块已完成，从 DB 加载分块，跳过文件解析
                log.info("分块已存在，跳过解析阶段，从 DB 加载: docId={}", docId);
                chunks = chunkService.findChunksByDocId(docId);
                if (chunks.isEmpty()) {
                    throw new IllegalStateException("分块数据缺失：parseStatus=SUCCESS 但 chunk 表无数据");
                }
                chunkCount = chunks.size();
                parseSuccess = true;

            } else {
                // 正常流程：重新解析文件
                documentService.updateParseStatus(docId, ParseStatusEnum.PARSING, 0, null); // 1-解析中

                // 流式读取文件（输入流），防止大文件压爆 JVM 堆内存
                try (InputStream inputStream = getFileInputStream(doc.getFilePath())) {
                    chunks = fileParseService.parseAndChunk(
                            inputStream,
                            doc.getFileName(),
                            doc.getFileType(),
                            "",
                            doc.getId(),
                            doc.getKbId(),
                            doc.getTitle()
                    );
                }

                chunkCount = chunks.size();
                documentService.updateParseStatus(docId, ParseStatusEnum.SUCCESS, chunkCount, null); // 2-解析成功
                parseSuccess = true;
                log.info("文档解析成功: docId={}, chunks={}", docId, chunkCount);

                // 持久化分块（向量化前的恢复点）
                if (!chunks.isEmpty()) {
                    chunkService.saveChunks(docId, doc.getKbId(), doc.getVersion(), chunks);
                }
            }

            // ---------------- 阶段二：向量化与入库 ----------------
            if (!chunks.isEmpty()) {
                documentService.updateVectorStatus(docId, VectorStatusEnum.VECTORIZING, 0, null); // 1-向量化中
                int savedCount = embeddingService.embedAndSave(doc, chunks);
                documentService.updateVectorStatus(docId, VectorStatusEnum.SUCCESS, savedCount, null); // 2-向量化完成
                log.info("文档向量化成功: docId={}, vectors={}", docId, savedCount);
            } else {
                documentService.updateVectorStatus(docId, VectorStatusEnum.SUCCESS, 0, null);
                log.info("文档内容为空，跳过向量化阶段: docId={}", docId);
            }

        } catch (Exception e) {
            log.error("文档处理发生异常: docId={}, parseSuccess={}", docId, parseSuccess, e);
            String safeError = truncateErrorMsg(e.getMessage());

            // 基于本地标记精确定位失败阶段
            if (!parseSuccess) {
                documentService.updateParseStatus(docId, ParseStatusEnum.FAILED, 0, "解析阶段失败: " + safeError);
            } else {
                // 向量化失败：清理已写入 PGVector 的向量，保留已解析的分块数
                embeddingService.deleteVectorsByDocId(docId);
                documentService.updateVectorStatus(docId, VectorStatusEnum.FAILED,
                        chunkCount, "向量化阶段失败: " + safeError);
            }
        } finally {
            // 处理完成后清理去重键，避免残留阻塞后续重试
            redisUtil.delete(dedupKey);
        }
    }

    /**
     * 获取文件输入流（支持 S3 和本地）
     */
    private InputStream getFileInputStream(String filePath) throws Exception {
        if ("cloud".equalsIgnoreCase(storageType)) {
            return s3Client.getObject(GetObjectRequest.builder().key(filePath).build());
        } else {
            Path path = Paths.get(getLocalUploadPath(), filePath);
            if (!Files.exists(path)) {
                throw new FileNotFoundException("本地文件不存在: " + path.toAbsolutePath());
            }
            return Files.newInputStream(path);
        }
    }

    private String truncateErrorMsg(String msg) {
        if (msg == null) return "未知错误";
        return msg.length() > 500 ? msg.substring(0, 500) + "..." : msg;
    }
}