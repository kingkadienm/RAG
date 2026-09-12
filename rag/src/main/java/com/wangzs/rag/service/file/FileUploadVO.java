package com.wangzs.rag.service.file;

import lombok.Data;

/**
 * 文件上传结果 VO
 */
@Data
public class FileUploadVO {

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 存储的文件 key（路径）
     */
    private String fileKey;

    /**
     * 访问 URL
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件扩展名
     */
    private String fileType;
}
