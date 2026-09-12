package com.wangzs.rag.service.file;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.config.S3Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S3 云存储实现（支持 MinIO / 阿里云 OSS / AWS S3）
 */
@Slf4j
@Service
public class FileStorageCloudServiceImpl implements IFileStorageService {

    private static final String USER_METADATA_FILE_NAME = "file-name";
    private static final String USER_METADATA_FILE_FORMAT = "file-format";

    private final S3Client s3Client;
    private final S3Config cloudConfig;

    // 简单的日期格式化
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public FileStorageCloudServiceImpl(S3Client s3Client, S3Config cloudConfig) {
        this.s3Client = s3Client;
        this.cloudConfig = cloudConfig;
    }

    @Override
    public FileUploadVO upload(org.springframework.web.multipart.MultipartFile file, String path) {
        String originalFileName = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFileName)) {
            throw BizException.of(ErrorCode.FILE_NAME_INVALID);
        }

        // 生成文件 key: path + UUID + 时间戳 + 扩展名
        String fileType = org.apache.commons.io.FilenameUtils.getExtension(originalFileName);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        String fileKey = path + uuid + "_" + time + (StringUtils.isNotBlank(fileType) ? "." + fileType : "");

        // 设置元数据
        String urlEncodedName = URLEncoder.encode(originalFileName, StandardCharsets.UTF_8);
        Map<String, String> userMetadata = new HashMap<>();
        userMetadata.put(USER_METADATA_FILE_NAME, urlEncodedName);
        userMetadata.put(USER_METADATA_FILE_FORMAT, fileType);

        // 确定 ACL：如果路径包含 public 则公开，否则私有
        ObjectCannedACL acl = path != null && path.contains("public")
                ? ObjectCannedACL.PUBLIC_READ
                : ObjectCannedACL.PRIVATE;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(cloudConfig.getBucket())
                .key(fileKey)
                .metadata(userMetadata)
                .contentLength(file.getSize())
                .contentType(getContentType(fileType))
                .acl(acl)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            log.error("S3 文件上传失败", e);
            throw BizException.of(ErrorCode.FILE_UPLOAD_FAILED.getCode(), e.getMessage());
        }

        FileUploadVO vo = new FileUploadVO();
        vo.setFileName(originalFileName);
        vo.setFileKey(fileKey);
        vo.setFileSize(file.getSize());
        vo.setFileType(fileType);

        if (ObjectCannedACL.PRIVATE.equals(acl)) {
            vo.setFileUrl(getFileUrl(fileKey));
        } else {
            vo.setFileUrl(cloudConfig.getPublicUrlPrefix() + fileKey);
        }

        log.info("S3 上传成功: fileKey={}, url={}", fileKey, vo.getFileUrl());
        return vo;
    }

    @Override
    public String getFileUrl(String fileKey) {
        if (StringUtils.isBlank(fileKey)) {
            return StringUtils.EMPTY;
        }

        // 私有文件通过预签名 URL 访问
        // 简化处理：直接返回公开 URL 前缀 + key（适用于 MinIO 公开读场景）
        return cloudConfig.getPublicUrlPrefix() + fileKey;
    }

    @Override
    public boolean delete(String fileKey) {
        if (StringUtils.isBlank(fileKey)) {
            return false;
        }

        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(cloudConfig.getBucket())
                    .key(fileKey)
                    .build();
            s3Client.deleteObject(request);
            log.info("S3 文件删除成功: fileKey={}", fileKey);
            return true;
        } catch (Exception e) {
            log.error("S3 文件删除失败: fileKey={}", fileKey, e);
            return false;
        }
    }

    @Override
    public InputStream getInputStream(String fileKey) {
        if (StringUtils.isBlank(fileKey)) {
            throw BizException.of(ErrorCode.FILE_NAME_INVALID.getCode(), "文件 key 不能为空");
        }
        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(cloudConfig.getBucket())
                    .key(fileKey)
                    .build());
        } catch (Exception e) {
            throw BizException.of(ErrorCode.FILE_UPLOAD_FAILED.getCode(), "读取 S3 文件失败: " + e.getMessage());
        }
    }

    /**
     * 根据扩展名获取 Content-Type
     */
    private String getContentType(String fileType) {
        if (StringUtils.isBlank(fileType)) {
            return "application/octet-stream";
        }
        return switch (fileType.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "doc", "docx" -> "application/msword";
            case "xls", "xlsx" -> "application/vnd.ms-excel";
            case "ppt", "pptx" -> "application/vnd.ms-powerpoint";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }
}
