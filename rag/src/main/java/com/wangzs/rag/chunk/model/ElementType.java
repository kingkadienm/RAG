package com.wangzs.rag.chunk.model;

/**
 * 文档元素类型
 */
public enum ElementType {

    /** 标题（Markdown # / ## / ### 等） */
    HEADING,

    /** 段落 */
    PARAGRAPH,

    /** 页面（PDF 专用，内部包含多个 PARAGRAPH） */
    PAGE,

    /** 表格 */
    TABLE,

    /** 代码块 */
    CODE_BLOCK,

    /** 列表项 */
    LIST_ITEM,

    /** 纯文本（TXT 等无结构文件） */
    TEXT
}
