package com.wangzs.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 聊天请求
 */
@Data
public class ChatRequest {

    @Schema(description = "会话 ID", example = "abc-123-def")
    private String sessionId;

    @Schema(description = "用户问题", requiredMode = Schema.RequiredMode.REQUIRED, example = "如何配置数据源？")
    @NotBlank(message = "问题不能为空")
    private String question;

    @Schema(description = "知识库 ID 列表（可选，限定检索范围）", example = "[1, 2]")
    private java.util.List<Long> kbIds;
}
