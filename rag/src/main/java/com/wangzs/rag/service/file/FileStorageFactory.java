package com.wangzs.rag.service.file;

import com.wangzs.rag.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件存储工厂
 * 根据配置的 file.storage.type 选择对应的存储实现
 *
 * <p>storageType 从 ConfigService（数据库）读取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageFactory {

    private final FileStorageLocalServiceImpl localStorageService;
    private final FileStorageCloudServiceImpl cloudStorageService;
    private final ConfigService configService;


    /**
     * 根据配置获取存储服务
     */
    public IFileStorageService getStorageService() {
        String storageType = configService.getString("file.storage.type", "local");
        if (storageType == null || storageType.isBlank() || "local".equals(storageType)) {
            return localStorageService;
        }
        if ("cloud".equals(storageType)) {
            return cloudStorageService;
        }
        return localStorageService;
    }
}
