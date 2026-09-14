package com.wangzs.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.mapper.DocumentMapper;
import com.wangzs.rag.repository.VectorDocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档解析自动重试定时任务
 *
 * <p>配置来源：ConfigService（数据库 sys_config 表），支持动态调整重试次数和间隔
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentRetryScheduler {

    private final DocumentMapper documentMapper;
    private final DocumentService documentService;
    private final ConfigService configService;
    private final VectorDocumentChunkRepository vectorDocumentChunkRepository;


    /**
     * 自动重试失败/卡住的文档（每 5 分钟执行一次）
     */
    @Scheduled(fixedRate = 300000L)
    public void autoRetryFailedDocuments() {
        log.debug("开始扫描待重试文档...");

        // 查询解析失败（parseStatus=3）或向量化失败（vectorStatus=3）的文档
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getDeleted, DeletedEnum.NO)
                .and(q -> q
                        .eq(Document::getParseStatus, 3)
                        .or()
                        .eq(Document::getVectorStatus, 3)
                );

        List<Document> failedDocs = documentMapper.selectList(wrapper);
        if (failedDocs.isEmpty()) {
            log.debug("没有需要重试的文档");
            return;
        }

        log.info("发现 {} 个需要重试的文档", failedDocs.size());

        for (Document doc : failedDocs) {
            int retryCount = doc.getRetryCount() != null ? doc.getRetryCount() : 0;
            int maxAttempts = configService.getInt("rag.retry.max-attempts", 3);
            if (retryCount >= maxAttempts) {
                log.warn("文档已达最大重试次数，跳过: docId={}, retries={}/{}",
                        doc.getId(), retryCount, maxAttempts);
                continue;
            }

            // 根据失败阶段决定重试策略
            boolean parseFailed = doc.getParseStatus() == ParseStatusEnum.FAILED;
            boolean vectorFailed = doc.getVectorStatus() == VectorStatusEnum.FAILED;

            if (parseFailed) {
                // 解析失败：全量重置 + 清理 PostgreSQL 分块
                vectorDocumentChunkRepository.deleteByDocId(doc.getId());
                documentService.updateParseStatus(doc.getId(), ParseStatusEnum.INIT, 0, null);
                documentService.updateVectorStatus(doc.getId(), VectorStatusEnum.INIT, 0, null);
                documentService.incrementRetryCount(doc.getId());
                documentService.sendParseMessage(doc);
                log.info("自动重试（解析失败）: docId={}, attempt={}", doc.getId(), retryCount + 1);

            } else if (vectorFailed) {
                // 向量化失败：只重置向量化阶段，保留分块
                documentService.updateVectorStatus(doc.getId(), VectorStatusEnum.INIT, 0, null);
                documentService.incrementRetryCount(doc.getId());
                documentService.sendParseMessage(doc);
                log.info("自动重试（向量化失败）: docId={}, attempt={}", doc.getId(), retryCount + 1);
            }
        }
    }

    /**
     * 扫描长时间处于"解析中"状态的文档（可能是消费者崩溃导致的消息丢失）
     */
    @Scheduled(fixedRate = 300000L)
    public void retryStuckDocuments() {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getDeleted, DeletedEnum.NO)
                .eq(Document::getParseStatus, 1);

        List<Document> stuckDocs = documentMapper.selectList(wrapper);
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
                documentService.sendParseMessage(doc);
            }
        }
    }
}
