package com.wangzs.rag.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件上传请求
 */
@Data
public class UploadFileRequest {

    @Schema(description = "知识库 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long kbId;

    @Schema(description = "文件存储路径子目录", example = "docs/")
    private String path;
}
