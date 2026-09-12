package com.wangzs.rag.service.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储服务接口（策略模式）
 * 参考 smart-admin 实现
 */
public interface IFileStorageService {

    /**
     * 上传文件
     *
     * @param file 文件
     * @param path 存储路径子目录
     * @return 上传结果（包含 fileKey, fileUrl）
     */
    FileUploadVO upload(MultipartFile file, String path);

    /**
     * 获取文件访问 URL
     *
     * @param fileKey 文件 key（存储路径）
     * @return 可访问的 URL
     */
    String getFileUrl(String fileKey);

    /**
     * 删除文件
     *
     * @param fileKey 文件 key
     * @return 是否成功
     */
    boolean delete(String fileKey);

    /**
     * 读取文件内容为输入流（用于分块预览等场景）
     *
     * @param fileKey 文件 key（存储路径）
     * @return 文件输入流
     */
    InputStream getInputStream(String fileKey);
}
