package com.wangzs.rag.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块实体类
 * <p>对应表：kb_document_chunk</p>
 */
@Data
@TableName("kb_document_chunk")
public class DocumentChunk {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属文档 ID
     */
    private Long docId;

    /**
     * 所属知识库 ID（冗余，方便按 kb 清理）
     */
    private Long kbId;

    /**
     * 分块序号（从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 章节路径（如 "集团开票信息汇总 > 阿里巴巴"）
     */
    private String sectionPath;

    /**
     * 分块文本内容
     */
    private String content;

    /**
     * Token 数量
     */
    private Integer tokenCount;

    /**
     * 字符长度
     */
    private Integer charLength;

    /**
     * 用于生成向量的文本（= title + sectionPath + content）
     */
    private String embeddingText;

    /**
     * 文档版本号（与 kb_document.version 同步）
     */
    private Integer version;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
