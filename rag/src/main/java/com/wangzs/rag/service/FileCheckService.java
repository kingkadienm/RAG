package com.wangzs.rag.service;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.model.entity.UploadRecord;
import com.wangzs.rag.mapper.UploadRecordMapper;
import com.wangzs.rag.util.FileUtil;
import com.wangzs.rag.util.MD5Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 文件校验服务
 * 负责文件名合法性、大小、扩展名、MD5 去重、Tika MIME 检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCheckService {

    private final FileUtil fileUtil;
    private final UploadRecordMapper uploadRecordMapper;

    /**
     * 全面校验上传文件（不含 MD5 计算，由调用方传入）
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ErrorCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw BizException.of(ErrorCode.FILE_NAME_INVALID);
        }

        // 1. 文件名合法性校验
        if (!fileUtil.isFileNameValid(originalFilename)) {
            throw BizException.of(ErrorCode.FILE_NAME_INVALID);
        }

        // 2. 文件大小校验
        if (!fileUtil.isFileSizeValid(file.getSize())) {
            throw BizException.of(ErrorCode.FILE_TOO_LARGE);
        }

        // 3. 扩展名校验
        String extension = fileUtil.getExtension(originalFilename);
        if (StringUtils.isBlank(extension) || !fileUtil.isExtensionAllowed(extension)) {
            throw BizException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    /**
     * 计算文件 MD5 并检查是否重复
     */
    public String calculateAndCheckMD5(MultipartFile file) {
        try {
            String md5 = MD5Util.calculateFileMD5(file);
            UploadRecord existRecord = uploadRecordMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadRecord>()
                            .eq(UploadRecord::getFileMd5, md5)
            );
            if (existRecord != null) {
                throw BizException.of(ErrorCode.FILE_MD5_DUPLICATE);
            }
            return md5;
        } catch (BizException be) {
            throw be;
        } catch (IOException e) {
            log.error("计算文件 MD5 失败", e);
            throw BizException.of(ErrorCode.FILE_CHECK_FAILED);
        }
    }

    /**
     * 使用 Tika 检测 MIME 类型并校验是否与扩展名匹配
     */
    public String detectAndValidateMimeType(MultipartFile file, String extension) {
        try {
            String mimeType = fileUtil.detectMimeType(file);
            if (StringUtils.isBlank(mimeType)) {
                log.warn("Tika 无法检测文件 MIME 类型: {}", file.getOriginalFilename());
                return "application/octet-stream";
            }

            if (!fileUtil.isMimeTypeMatchExtension(mimeType, extension)) {
                log.warn("MIME 类型不匹配: 文件名={}, MIME={}, 扩展名={}",
                        file.getOriginalFilename(), mimeType, extension);
                throw BizException.of(ErrorCode.FILE_TYPE_NOT_ALLOWED);
            }

            return mimeType;
        } catch (IOException e) {
            log.error("检测 MIME 类型失败", e);
            throw BizException.of(ErrorCode.FILE_CHECK_FAILED);
        }
    }

    /**
     * 创建上传记录
     */
    public UploadRecord createUploadRecord(MultipartFile file, String mimeType, String md5,
                                           String storageType, Long kbId, Long uploaderId) {
        UploadRecord record = new UploadRecord();
        record.setOriginalFilename(file.getOriginalFilename());
        record.setStoredFilename(fileUtil.generateStoredFileName(file.getOriginalFilename()));
        record.setFileSize(file.getSize());
        record.setFileMd5(md5);
        record.setMimeType(mimeType);
        record.setStorageType(storageType);
        record.setCheckStatus(1); // 通过
        record.setKbId(kbId);
        record.setUploaderId(uploaderId);
        record.setCreatedTime(LocalDateTime.now());
        return record;
    }
}
