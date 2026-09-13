package com.wangzs.rag.chunk;

import com.wangzs.rag.chunk.model.ParsedDocument;

import java.util.List;

/**
 * 切片策略接口
 * <p>
 * 每种文件类型对应一种策略，负责将 ParsedDocument 切分为 Chunk 列表。
 * <pre>
 * ChunkingStrategy
 *     │
 *     ├── MarkdownChunkingStrategy
 *     ├── PdfChunkingStrategy
 *     ├── DocxChunkingStrategy
 *     ├── TxtChunkingStrategy
 *     └── DefaultChunkingStrategy
 * </pre>
 */
public interface ChunkingStrategy {

    /**
     * 判断该策略是否支持指定文件类型
     *
     * @param fileType 文件扩展名（如 md / pdf / docx / txt）
     * @return true 表示支持
     */
    boolean supports(String fileType);

    /**
     * 将统一文档模型切分为 Chunk 列表
     *
     * @param document 解析后的统一文档
     * @return Chunk 列表
     */
    List<Chunk> chunk(ParsedDocument document);
}
