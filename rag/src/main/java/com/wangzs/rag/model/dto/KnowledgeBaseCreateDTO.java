package com.wangzs.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建知识库请求
 */
@Data
public class KnowledgeBaseCreateDTO {

    @Schema(description = "知识库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "产品文档库")
    @NotBlank(message = "知识库名称不能为空")
    @Size(min = 1, max = 128, message = "知识库名称长度1-128")
    private String name;

    @Schema(description = "知识库描述", example = "存储产品相关的技术文档")
    @Size(max = 1000, message = "描述长度不能超过1000")
    private String description;
}
