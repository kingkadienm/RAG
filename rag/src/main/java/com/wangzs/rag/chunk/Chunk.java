package com.wangzs.rag.chunk;

import lombok.Data;

/**
 * 文本分块结果
 */
@Data
public class Chunk {

    /**
     * 分块索引（从 0 开始）
     */
    private int index;

    /**
     * 分块内容
     */
    private String content;

    /**
     * 分块字符长度
     */
    private int length;
}
