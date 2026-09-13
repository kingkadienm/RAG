package com.wangzs.rag.service.impl;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.service.*;
import com.wangzs.rag.service.file.FileUploadVO;
import com.wangzs.rag.service.file.IFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传服务实现（纯编排层）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private final FileCheckService fileCheckService;
    private final FileParseService fileParseService;
    private final IFileStorageService fileStorageService;
    private final DocumentService documentService;
    private final ConfigService configService;

    @Override
    public Map<String, Object> uploadFile(Long kbId, MultipartFile file, String originalFilename) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.FILE_EMPTY);
        }

        // 1. 文件基本校验（文件名、大小、扩展名）
        fileCheckService.validateFile(file);

        // 2. 获取扩展名
        String extension = fileParseService.getFileExtension(originalFilename);

        // 3. 计算 MD5 并检查是否重复
        String md5 = fileCheckService.calculateAndCheckMD5(file);

        // 4. Tika MIME 检测
        String mimeType = fileCheckService.detectAndValidateMimeType(file, extension);

        // 5. 先上传文件到存储（失败则无 DB 记录残留）
        String storageDir = "kb/" + kbId + "/";
        Object uploadResult;
        String storedFileName = null;
        try {
            uploadResult = fileStorageService.upload(file, storageDir);
            storedFileName = extractFileKey(uploadResult);
        } catch (Exception e) {
            log.error("文件上传到存储失败: fileName={}", originalFilename, e);
            String safeMsg = truncateErrorMsg(e.getMessage());
            throw BizException.of(ErrorCode.FILE_UPLOAD_FAILED.getCode(),
                    "文件上传失败: " + (safeMsg != null ? safeMsg : "未知错误"));
        }

        // 6. 创建上传记录 + 文档记录（同一事务，保证数据一致性）
        Document doc;
        try {
            String storageType = configService.getString("file.storage.type", "local");
            doc = documentService.createWithUploadRecord(
                    kbId, originalFilename, originalFilename,
                    extension, file.getSize(), storedFileName, md5,
                    0L, storedFileName, mimeType, storageType
            );
        } catch (Exception e) {
            // DB 创建失败，清理已上传的文件
            if (storedFileName != null) {
                try {
                    fileStorageService.delete(storedFileName);
                } catch (Exception deleteEx) {
                    log.warn("删除存储文件失败: storedFileName={}", storedFileName, deleteEx);
                }
            }
            log.error("创建数据库记录失败，已清理存储文件: fileName={}", originalFilename, e);
            String safeMsg = truncateErrorMsg(e.getMessage());
            throw BizException.of(ErrorCode.FILE_UPLOAD_FAILED.getCode(),
                    "创建记录失败: " + (safeMsg != null ? safeMsg : "未知错误"));
        }

        // 7. 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("docId", doc.getId());
        data.put("fileName", originalFilename);
        data.put("fileSize", file.getSize());
        data.put("parseStatus", 0);
        data.put("message", "文件上传成功，正在后台解析");
        return data;
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

    private static String truncateErrorMsg(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) + "..." : msg;
    }
}
