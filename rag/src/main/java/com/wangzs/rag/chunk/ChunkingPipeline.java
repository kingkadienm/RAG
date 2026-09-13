package com.wangzs.rag.chunk;

import com.wangzs.rag.chunk.common.LengthSplitter;
import com.wangzs.rag.chunk.model.ParsedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 切片 Pipeline
 * <p>
 * 串联完整切片流程：
 * <pre>
 * ParsedDocument
 *     ↓
 * ChunkingStrategyFactory（按 fileType 选择策略）
 *     ↓
 * MarkdownPdfDocxTxtStrategy（结构切片 → sectionPath）
 *     ↓
 * LengthSplitter（特殊内容 + 长度约束 + embeddingText + metadata）
 *     ↓
 * List<Chunk>
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkingPipeline {

    private final ChunkingStrategyFactory strategyFactory;
    private final LengthSplitter lengthSplitter;

    /**
     * 执行完整切片流程
     *
     * @param document  统一文档模型
     * @return Chunk 列表
     */
    public List<Chunk> execute(ParsedDocument document) {
        if (document == null) {
            return List.of();
        }

        String fileType = document.getFileType();

        // 1. 选择切片策略
        ChunkingStrategy strategy = strategyFactory.getStrategy(fileType);

        // 2. 结构/语义切片
        List<Chunk> baseChunks = strategy.chunk(document);

        log.info("结构切片完成: fileType={}, baseChunks={}", fileType, baseChunks.size());

        // 3. 长度约束 + embeddingText + metadata
        List<Chunk> result = lengthSplitter.split(baseChunks, document);

        log.info("切片 Pipeline 完成: fileType={}, finalChunks={}", fileType, result.size());

        return result;
    }
}
