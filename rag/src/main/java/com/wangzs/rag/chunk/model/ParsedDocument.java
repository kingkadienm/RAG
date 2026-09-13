package com.wangzs.rag.chunk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 统一文档解析结果模型
 * <p>
 * 任何文件经过 Parser 解析后，都转换为该结构。
 * <pre>
 * ParsedDocument
 * ├── documentId
 * ├── kbId
 * ├── fileName
 * ├── fileType
 * ├── title
 * ├── content          (全文纯文本，备用)
 * └── elements         (结构化元素列表)
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ParsedDocument {

    /**
     * 文档 ID（对应 kb_document.id）
     */
    private Long documentId;

    /**
     * 所属知识库 ID
     */
    private Long kbId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型扩展名（md / pdf / docx / txt）
     */
    private String fileType;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 全文纯文本（由 elements 拼接生成，作为备用）
     */
    private String content;

    /**
     * 结构化元素列表
     */
    private List<DocumentElement> elements;
}
