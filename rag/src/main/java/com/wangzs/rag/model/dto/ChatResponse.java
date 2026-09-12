package com.wangzs.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 聊天响应
 */
@Data
public class ChatResponse {

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "助手回复内容")
    private String answer;

    @Schema(description = "引用的文档片段")
    private List<ReferenceDoc> references;

    @Data
    public static class ReferenceDoc {
        @Schema(description = "文档 ID")
        private Long docId;

        @Schema(description = "文档名称")
        private String fileName;

        @Schema(description = "文本片段")
        private String content;

        @Schema(description = "相似度分数")
        private Double score;
    }
}
