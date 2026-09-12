package com.wangzs.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分块视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkVO {

    /**
     * 分块索引（从 0 开始）
     */
    private Integer index;

    /**
     * 分块内容
     */
    private String content;

    /**
     * 分块字符长度
     */
    private Integer length;
}
