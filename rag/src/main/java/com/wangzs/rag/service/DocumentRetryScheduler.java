package com.wangzs.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;


    /**
     * 自动重试失败/卡住的文档（每 5 分钟执行一次）
     */
    @Scheduled(fixedRate = 300000L)
    public void autoRetryFailedDocuments() {
        log.debug("开始扫描待重试文档...");

        // 查询解析失败（parseStatus=3）或向量化失败（vectorStatus=3）的文档
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getDeleted, 0)
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
            if (retryCount >= configService.getInt("rag.retry.max-attempts", 3)) {
                log.warn("文档已达最大重试次数，跳过: docId={}, retries={}/{}",
                        doc.getId(), retryCount, configService.getInt("rag.retry.max-attempts", 3));
                continue;
            }

            // 递增重试次数并保存
            int nextAttempt = retryCount + 1;
            documentService.updateParseStatus(doc.getId(), ParseStatusEnum.INIT, 0, null);
            documentService.updateVectorStatus(doc.getId(), VectorStatusEnum.INIT, 0, null);
            documentService.incrementRetryCount(doc.getId());

            // 发送 MQ 消息
            sendParseMessage(doc, nextAttempt);
        }
    }

    /**
     * 扫描长时间处于"解析中"状态的文档（可能是消费者崩溃导致的消息丢失）
     */
    @Scheduled(fixedRate = 300000L)
    public void retryStuckDocuments() {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getDeleted, 0)
                .eq(Document::getParseStatus, 1);

        List<Document> stuckDocs = documentMapper.selectList(wrapper);
        for (Document doc : stuckDocs) {
            if (doc.getUpdatedTime() != null
                    && doc.getUpdatedTime().isBefore(java.time.LocalDateTime.now().minusMinutes(30))) {
                log.warn("文档解析超时，重置为待解析: docId={}", doc.getId());
                documentService.updateParseStatus(doc.getId(), ParseStatusEnum.INIT, 0, "解析超时，自动重试");
                documentService.incrementRetryCount(doc.getId());
                sendParseMessage(doc, (doc.getRetryCount() != null ? doc.getRetryCount() : 0) + 1);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  发送解析消息
    // -----------------------------------------------------------------------
    private void sendParseMessage(Document doc, int attempt) {
        String parseTopic = configService.getString("rag.rocketmq.topic", "rag-document-parse");
        String storageType = configService.getString("file.storage.type", "local");

        try {
            DefaultMQProducer producer = rocketMQTemplate.getProducer();

            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("docId", doc.getId());
            msgBody.put("kbId", doc.getKbId());
            msgBody.put("filePath", doc.getFilePath());
            msgBody.put("fileName", doc.getFileName());
            msgBody.put("fileType", doc.getFileType());
            msgBody.put("fileMd5", doc.getFileMd5());
            msgBody.put("storageType", storageType);
            msgBody.put("retryCount", attempt);

            Message msg = new Message(
                    parseTopic,
                    objectMapper.writeValueAsString(msgBody).getBytes(StandardCharsets.UTF_8)
            );
            msg.putUserProperty("docId", String.valueOf(doc.getId()));
            msg.putUserProperty("kbId", String.valueOf(doc.getKbId()));
            msg.putUserProperty("fileName", doc.getFileName());
            msg.putUserProperty("fileType", doc.getFileType());
            msg.putUserProperty("fileMd5", doc.getFileMd5());
            msg.putUserProperty("storageType", storageType);
            msg.putUserProperty("retryCount", String.valueOf(attempt));

            producer.send(msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("自动重试消息发送成功: docId={}, attempt={}, msgId={}",
                            doc.getId(), attempt,
                            sendResult != null ? sendResult.getMsgId() : "unknown");
                }

                @Override
                public void onException(Throwable ex) {
                    log.error("自动重试消息发送失败: docId={}, attempt={}", doc.getId(), attempt, ex);
                }
            });

        } catch (Exception e) {
            log.error("自动重试发送异常: docId={}", doc.getId(), e);
        }
    }

}
