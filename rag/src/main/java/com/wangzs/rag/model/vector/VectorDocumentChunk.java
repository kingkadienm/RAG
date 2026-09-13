package com.wangzs.rag.model.vector;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PostgreSQL document_chunk 表实体
 * <p>
 * 对应 PostgreSQL 表：rag.document_chunk
 * </p>
 * <p>
 * 不使用 MyBatis-Plus 注解，由 Repository 层通过 JdbcTemplate 直接操作。
 * </p>
 */
@Data
public class VectorDocumentChunk {

    /**
     * 主键 ID（PostgreSQL BIGSERIAL）
     */
    private Long id;

    /**
     * 知识库 ID
     */
    private Long kbId;

    /**
     * 文档 ID（关联 MySQL kb_document.id）
     */
    private Long docId;

    /**
     * 分块序号（从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 整个文档的分块总数
     */
    private Integer chunkTotal;

    /**
     * 分块文本内容
     */
    private String content;

    /**
     * 内容 MD5 Hash（用于检测内容变化）
     */
    private String contentHash;

    /**
     * 1536 维向量
     */
    private float[] embedding;

    /**
     * 向量生成模型名称
     */
    private String embeddingModel;

    /**
     * 扩展元数据（JSONB）
     * 包含：title, section_path, token_count, char_length, embedding_text, version 等
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
