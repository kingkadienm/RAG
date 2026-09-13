package com.wangzs.rag.chunk.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档内部结构化元素
 * <p>
 * 对应架构文档中的 DocumentElement：
 * <pre>
 * Markdown:  Heading → Paragraph → Paragraph
 * PDF:       Page → Paragraph → Paragraph
 * DOCX:      Heading → Paragraph → Table
 * TXT:       TEXT → (flat)
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentElement {

    /**
     * 元素类型
     */
    private ElementType type;

    /**
     * 标题级别（仅 HEADING 有效，1-6）
     */
    private int level;

    /**
     * 元素内容
     */
    private String content;

    /**
     * 页码（PDF 专用，其余类型为 null）
     */
    private Integer pageNumber;

    /**
     * 扩展元数据（表格行列数、代码语言等）
     */
    private java.util.Map<String, Object> metadata;
}
