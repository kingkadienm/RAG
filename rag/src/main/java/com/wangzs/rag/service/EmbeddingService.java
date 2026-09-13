package com.wangzs.rag.service;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.mapper.DocumentMapper;
import com.wangzs.rag.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量化服务
 * 负责将文本分块向量化并存入 PGVector
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final DocumentMapper documentMapper;
    private final ConfigService configService;

    /**
     * 对文档分块进行向量化并保存（分批处理，失败回滚）
     *
     * @param doc    文档实体
     * @param chunks 文本分块列表
     * @return 成功向量化的分块数量
     */
    public int embedAndSave(Document doc, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        int batchSize = Math.max(1, configService.getInt("rag.embedding.batch-size", 25));
        long delayMs = configService.getLong("rag.embedding.batch-delay-ms", 200);

        int totalSaved = 0;

        try {
            for (int start = 0; start < chunks.size(); start += batchSize) {
                int end = Math.min(start + batchSize, chunks.size());
                List<Chunk> batch = chunks.subList(start, end);

                int saved = embedBatch(doc, batch);
                totalSaved += saved;

                // 批次间限流（最后一组无需等待）
                if (end < chunks.size()) {
                    Thread.sleep(delayMs);
                }
            }
        } catch (Exception e) {
            // 失败回滚：清理已入库的向量
            deleteVectorsByDocId(doc.getId());
            log.error("向量化失败，已回滚: docId={}, saved={}/{}", doc.getId(), totalSaved, chunks.size(), e);
            throw BizException.of(ErrorCode.EMBEDDING_FAILED);
        }

        log.info("向量化+入库完成: docId={}, chunks={}", doc.getId(), totalSaved);
        return totalSaved;
    }

    /**
     * 单批次向量化（embedding + 入库）
     */
    private int embedBatch(Document doc, List<Chunk> batch) {
        List<String> contents = batch.stream()
                .map(Chunk::getContent)
                .collect(Collectors.toList());

        List<float[]> embeddings = embeddingModel.embed(contents);

        List<org.springframework.ai.document.Document> vectorDocs = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Chunk chunk = batch.get(i);
            float[] embedding = embeddings.get(i);
            vectorDocs.add(new org.springframework.ai.document.Document(
                    buildContent(chunk, doc),
                    buildMetadata(doc, chunk)
            ));
        }

        vectorStore.add(vectorDocs);
        return batch.size();
    }

    /**
     * 删除文档的向量（清理 PGVector 中 doc_id 匹配的记录）
     * 用于向量化失败时的补偿清理，保证 MySQL 与 PGVector 一致
     */
    public void deleteVectorsByDocId(Long docId) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            vectorStore.delete(builder.in("doc_id", List.of(docId)).build());
            log.info("已清理文档向量: docId={}", docId);
        } catch (Exception e) {
            log.warn("清理文档向量失败（不影响主流程）: docId={}", docId, e);
        }
    }

    /**
     * 构建文档内容（文本 + 元信息）
     */
    private String buildContent(Chunk chunk, Document doc) {
        return "【知识库: " + doc.getKbId() + " | 文档: " + doc.getFileName() + "】\n" + chunk.getContent();
    }

    /**
     * 构建元数据
     * <p>Spring AI PGVector 自动保存 embedding，无需手动传入</p>
     */
    private Map<String, Object> buildMetadata(Document doc, Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        // ========== 溯源定位 ==========
        metadata.put("doc_id", doc.getId());
        metadata.put("kb_id", doc.getKbId());
        metadata.put("chunk_index", chunk.getIndex());
        metadata.put("chunk_total", doc.getChunkCount());
        // ========== 展示信息 ==========
        metadata.put("title", doc.getTitle());
        metadata.put("file_name", doc.getFileName());
        metadata.put("file_type", doc.getFileType());
        // ========== 版本控制（重建向量/缓存失效关键） ==========
        metadata.put("version", doc.getVersion());
        // ========== 权限与审计 ==========
        metadata.put("creator_id", doc.getCreatorId());
        metadata.put("created_time", doc.getCreatedTime());
        return metadata;
    }
}
