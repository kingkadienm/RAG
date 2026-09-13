package com.wangzs.rag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.model.dto.UploadFileRequest;
import com.wangzs.rag.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传 Controller（纯编排层，无业务逻辑）
 */
@Tag(name = "文件上传", description = "上传知识库文件")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class UploadController {

    private final UploadService uploadService;

    @Operation(summary = "上传知识库文件")
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadFile(@RequestPart("file") MultipartFile file,
                                                     UploadFileRequest request) {
        Map<String, Object> data = uploadService.uploadFile(request.getKbId(), file, file.getOriginalFilename());
        return ApiResult.success(data, "文件上传成功");
    }
}
