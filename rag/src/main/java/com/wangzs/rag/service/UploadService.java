package com.wangzs.rag.service;

import com.wangzs.rag.model.entity.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传服务（编排层：校验 → 上传 → 创建记录）
 */
public interface UploadService {

    /**
     * 上传文件到知识库（含校验、存储、DB 记录创建）
     */
    Map<String, Object> uploadFile(Long kbId, MultipartFile file, String originalFilename);
}
