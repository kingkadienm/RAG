package com.wangzs.rag.service;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
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

    /**
     * 对文档分块进行向量化并保存
     *
     * @param doc    文档实体
     * @param chunks 文本分块列表
     * @return 成功向量化的分块数量
     */
    public int embedAndSave(Document doc, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        try {
            // 1. 批量向量化
            List<String> contents = chunks.stream()
                    .map(Chunk::getContent)
                    .collect(Collectors.toList());

            List<float[]> embeddings = embeddingModel.embed(contents);

            // 2. 构造 Spring AI Document 并保存到 PGVector
            List<org.springframework.ai.document.Document> vectorDocs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                float[] embedding = embeddings.get(i);

                org.springframework.ai.document.Document vectorDoc = new org.springframework.ai.document.Document(
                        buildContent(chunk, doc),
                        buildMetadata(doc, chunk, embedding)
                );
                vectorDocs.add(vectorDoc);
            }

            vectorStore.add(vectorDocs);
            log.info("向量化+入库完成: docId={}, chunks={}", doc.getId(), chunks.size());
            return chunks.size();

        } catch (Exception e) {
            log.error("向量化失败: docId={}", doc.getId(), e);
            throw BizException.of(ErrorCode.EMBEDDING_FAILED);
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
     */
    private Map<String, Object> buildMetadata(Document doc, Chunk chunk, float[] embedding) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("doc_id", doc.getId());
        metadata.put("kb_id", doc.getKbId());
        metadata.put("chunk_index", chunk.getIndex());
        metadata.put("file_name", doc.getFileName());
        metadata.put("file_type", doc.getFileType());
        metadata.put("creator_id", doc.getCreatorId());
        // Spring AI PGVector 会自动保存 embedding
        return metadata;
    }
}
