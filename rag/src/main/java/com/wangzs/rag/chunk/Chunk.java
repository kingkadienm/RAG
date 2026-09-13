package com.wangzs.rag.chunk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文本分块结果
 * <p>
 * 架构文档定义的核心数据模型：
 * <pre>
 * Chunk
 * ├── documentId
 * ├── kbId
 * ├── chunkIndex
 * ├── content
 * ├── title
 * ├── sectionPath
 * ├── tokenCount
 * ├── charCount
 * ├── metadata
 * └── embeddingText
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chunk {

    /**
     * 分块索引（从 0 开始）
     */
    private int index;

    /**
     * 分块内容（用于检索结果展示和 LLM 上下文）
     */
    private String content;

    /**
     * 分块字符长度（保留，向后兼容 TextSplitter 的 length 字段）
     */
    private int length;

    // ---- 以下为架构文档新增字段 ----

    /**
     * 文档 ID
     */
    private Long documentId;

    /**
     * 所属知识库 ID
     */
    private Long kbId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 章节路径（如 "集团开票信息汇总 > 阿里巴巴健康集团"）
     */
    private String sectionPath;

    /**
     * Token 数量（由后续步骤计算，初始可为 0）
     */
    private int tokenCount;

    /**
     * 扩展元数据（documentId/kbId/fileName/fileType/chunkIndex/sectionPath/pageNumber 等）
     */
    private Map<String, Object> metadata;

    /**
     * 用于生成向量的文本（= title + sectionPath + content）
     */
    private String embeddingText;
}
