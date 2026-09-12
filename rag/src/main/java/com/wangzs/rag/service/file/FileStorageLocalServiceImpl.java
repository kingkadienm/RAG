package com.wangzs.rag.service.file;

import com.wangzs.rag.service.ConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地文件存储实现
 *
 * <p>uploadPath 和 urlPrefix 从 ConfigService（数据库）读取
 * 数据库值中的 ${user.dir} 等占位符需在加载后手动解析
 */
@Slf4j
@Service
public class FileStorageLocalServiceImpl implements IFileStorageService {

    private static final String FILE_SEPARATOR = "/";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String USER_DIR_PLACEHOLDER = "${user.dir}";
    private static final String USER_DIR_VALUE = System.getProperty("user.dir");

    private final ConfigService configService;
    private final Environment environment;

    public FileStorageLocalServiceImpl(ConfigService configService, Environment environment) {
        this.configService = configService;
        this.environment = environment;
    }
    
    private String getUploadPath() {
        String raw = configService.getString("file.storage.local.upload-path", "${user.dir}/rag-uploads/");
        return resolvePlaceholders(raw);
    }

    private String getUrlPrefix() {
        String raw = configService.getString("file.storage.local.url-prefix", "http://127.0.0.1:8080/upload/");
        return resolvePlaceholders(raw);
    }

    /**
     * 解析 Spring 风格占位符（如 ${user.dir}）
     * 优先走 Environment 解析，失败则手动替换 ${user.dir}
     */
    private String resolvePlaceholders(String value) {
        if (StringUtils.isBlank(value)) return value;
        try {
            return environment.resolveRequiredPlaceholders(value);
        } catch (Exception e) {
            log.warn("Environment 解析占位符失败，使用手动替换: value={}, error={}", value, e.getMessage());
        }
        // fallback：手动替换 ${user.dir}
        if (value.contains(USER_DIR_PLACEHOLDER)) {
            return value.replace(USER_DIR_PLACEHOLDER, USER_DIR_VALUE);
        }
        return value;
    }

    @Override
    public FileUploadVO upload(MultipartFile file, String path) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }

        String originalFileName = file.getOriginalFilename();
        String fileType = org.apache.commons.io.FilenameUtils.getExtension(originalFileName);

        // 生成存储文件名: UUID_时间戳.扩展名
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        String time = LocalDateTime.now().format(TIME_FORMATTER);
        String storedFileName = uuid + "_" + time + (StringUtils.isNotBlank(fileType) ? "." + fileType : "");

        // 构建完整存储路径
        String relativePath = path + storedFileName;
        String fullPath = getUploadPath() + relativePath;

        log.info("文件目录为：fullPath={}  relativePath={}",fullPath,relativePath);

        // 确保目录存在
        File directory = new File(getUploadPath() + path);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new RuntimeException("创建上传目录失败: " + directory.getAbsolutePath());
            }
        }

        File targetFile = new File(fullPath);
        try {
            file.transferTo(targetFile);
        } catch (IOException e) {
            // 清理失败的文件
            if (targetFile.exists()) {
                FileUtils.deleteQuietly(targetFile);
            }
            log.error("本地文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }

        FileUploadVO vo = new FileUploadVO();
        vo.setFileName(originalFileName);
        vo.setFileKey(relativePath);
        vo.setFileUrl(getUrlPrefix() + relativePath);
        vo.setFileSize(file.getSize());
        vo.setFileType(fileType);

        log.info("本地文件上传成功: path={}, url={}", relativePath, vo.getFileUrl());
        return vo;
    }

    @Override
    public String getFileUrl(String fileKey) {
        if (StringUtils.isBlank(fileKey)) {
            return StringUtils.EMPTY;
        }
        return getUrlPrefix() + fileKey;
    }

    @Override
    public boolean delete(String fileKey) {
        if (StringUtils.isBlank(fileKey)) {
            return false;
        }

        String fullPath = getUploadPath () + fileKey;
        File file = new File(fullPath);
        try {
            FileUtils.forceDelete(file);
            log.info("本地文件删除成功: fileKey={}", fileKey);
            return true;
        } catch (IOException e) {
            log.error("本地文件删除失败: fileKey={}", fileKey, e);
            return false;
        }
    }

    @Override
    public InputStream getInputStream(String fileKey) {
        if (StringUtils.isBlank(fileKey)) {
            throw new IllegalArgumentException("文件 key 不能为空");
        }
        String fullPath = getUploadPath() + fileKey;
        File file = new File(fullPath);
        if (!file.exists() || !file.isFile()) {
            throw BizException.of(3001, "文件不存在: " + fullPath);
        }
        try {
            return new FileInputStream(file);
        } catch (IOException e) {
            throw BizException.of(4006, "读取文件失败: " + e.getMessage());
        }
    }
}
