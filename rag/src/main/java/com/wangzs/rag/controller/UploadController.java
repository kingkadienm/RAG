package com.wangzs.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.model.dto.UploadFileRequest;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.model.entity.UploadRecord;
import com.wangzs.rag.service.*;
import com.wangzs.rag.service.file.FileUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传 Controller
 */
@Tag(name = "文件上传", description = "上传知识库文件")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final FileCheckService fileCheckService;
    private final FileParseService fileParseService;
    private final FileStorageService fileStorageService;
    private final DocumentService documentService;
    private final UploadRecordService uploadRecordService;
    private final ConfigService configService;

    @Operation(summary = "上传知识库文件")
    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadFile(@RequestPart("file") MultipartFile file,
                                                     UploadFileRequest request) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.FILE_EMPTY);
        }

        Long userId = getLoginUserId();
        String originalFilename = file.getOriginalFilename();
        String storageType = configService.getString("file.storage.type", "local");

        // 1. 文件基本校验（文件名、大小、扩展名）
        fileCheckService.validateFile(file);

        // 2. 获取扩展名
        String extension = fileParseService.getFileExtension(originalFilename);

        // 3. 计算 MD5 并检查是否重复
        String md5 = fileCheckService.calculateAndCheckMD5(file);

        // 4. Tika MIME 检测
        String mimeType = fileCheckService.detectAndValidateMimeType(file, extension);

        // 5. 创建上传记录
        UploadRecord record = fileCheckService.createUploadRecord(file, mimeType, md5, storageType, request.getKbId(), userId);
        uploadRecordService.save(record);

        // 6. 创建文档记录
        Document doc = documentService.create(
                request.getKbId(), originalFilename, originalFilename,
                extension, file.getSize(), null, md5, userId
        );

        // 7. 上传文件到存储
        String storageDir = "kb/" + request.getKbId() + "/";
        Object uploadResult;
        try {
            uploadResult = fileStorageService.upload(file, storageDir);
        } catch (Exception e) {
            log.error("文件上传到存储失败: docId={}, fileName={}", doc.getId(), doc.getFileName(), e);
            String rawMsg = e.getMessage();
            String safeMsg = (rawMsg != null && rawMsg.length() > 500) ? rawMsg.substring(0, 500) + "..." : rawMsg;
            String errorMsg = "文件上传失败: " + (safeMsg != null ? safeMsg : "未知错误");
            // 清理失败的数据库记录，允许用户重新上传同一文件
            try {
                uploadRecordService.removeById(record.getId());
                documentService.removeById(doc.getId());
                log.info("已清理失败的上传记录: uploadRecordId={}, docId={}", record.getId(), doc.getId());
            } catch (Exception cleanupEx) {
                log.error("清理失败记录时发生异常: uploadRecordId={}, docId={}", record.getId(), doc.getId(), cleanupEx);
            }
            throw BizException.of(ErrorCode.FILE_UPLOAD_FAILED.getCode(), errorMsg);
        }
        log.info("文件上传成功: docId={}, storageType={}", doc.getId(), storageType);

        // 8. 回填存储文件名
        String storedFileName = extractFileKey(uploadResult);
        record.setStoredFilename(storedFileName);
        uploadRecordService.updateById(record);
        documentService.updateFilePath(doc.getId(), storedFileName);

        // 9. 异步发送 MQ 消息触发解析（不阻塞 HTTP 响应）
        documentService.sendParseMessage(doc);

        // 10. 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("docId", doc.getId());
        data.put("fileName", originalFilename);
        data.put("fileSize", file.getSize());
        data.put("parseStatus", 0);
        data.put("message", "文件上传成功，正在后台解析");
        return ApiResult.success(data, "文件上传成功");
    }

    private Long getLoginUserId() {
        if (!StpUtil.isLogin()) {
            throw BizException.of(ErrorCode.PARAM_ERROR);
        }
        Object loginId = StpUtil.getLoginId();
        return loginId instanceof Long ? (Long) loginId : Long.parseLong(loginId.toString());
    }

    private String extractFileKey(Object uploadResult) {
        if (uploadResult instanceof FileUploadVO vo) {
            return vo.getFileKey();
        }
        if (uploadResult instanceof Map<?, ?> map) {
            Object key = map.get("fileKey");
            return key != null ? key.toString() : null;
        }
        return null;
    }
}