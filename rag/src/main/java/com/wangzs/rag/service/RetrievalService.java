package com.wangzs.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


/**
 * 向量检索服务
 * 基于 PGVector 进行向量相似度搜索
 *
 * <p>similarityThreshold 从 ConfigService（数据库）读取，支持动态调整
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final VectorStore vectorStore;
    private final ConfigService configService;

    /**
     * 检索与查询相关的 Top-K 文档片段
     *
     * @param queryText 查询文本
     * @param topK      返回 Top-K 结果
     * @param kbIds     限定知识库 ID 列表（可选）
     * @return 检索到的文档片段
     */
    public List<SearchResult> search(String queryText, int topK, List<Long> kbIds) {
        double similarityThreshold = configService.getDouble("rag.retrieval.similarity-threshold", 0.6);

        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        // 关键修复：将List转换为数组
        if (kbIds != null && !kbIds.isEmpty()) {
            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();

            // 单元素和多元素分别处理
            if (kbIds.size() == 1) {
                builder.filterExpression(filterBuilder.eq("kb_id", kbIds.get(0)).build());
            } else {
                // 转换为Object数组，让FilterExpressionBuilder正确解析
                builder.filterExpression(
                        filterBuilder.in("kb_id", kbIds.toArray()).build()
                );
            }
        }

        SearchRequest request = builder.build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.info("向量检索完成: query={}, results={}", queryText, results.size());
        return results.stream()
                .map(SearchResult::from)
                .toList();
    }

    /**
     * 检索结果
     */
    public record SearchResult(
            Long docId,
            Long kbId,
            Integer chunkIndex,
            Integer chunkTotal,
            String title,
            String fileName,
            String content,
            Double score
    ) {
        public static SearchResult from(Document doc) {
            Map<String, Object> metadata = doc.getMetadata();

            return new SearchResult(
                    metadata != null ? ((Number) metadata.get("doc_id")).longValue() : null,
                    metadata != null ? ((Number) metadata.get("kb_id")).longValue() : null,
                    metadata != null ? ((Number) metadata.get("chunk_index")).intValue() : 0,
                    metadata != null ? ((Number) metadata.get("chunk_total")).intValue() : 0,
                    metadata != null ? (String) metadata.get("title") : null,
                    metadata != null ? (String) metadata.get("file_name") : null,
                    doc.getText(),
                    doc.getScore()
            );
        }
    }
}