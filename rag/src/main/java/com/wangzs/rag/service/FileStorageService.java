package com.wangzs.rag.service;

import com.wangzs.rag.service.file.FileStorageFactory;
import com.wangzs.rag.service.file.IFileStorageService;
import com.wangzs.rag.service.file.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * 文件存储服务门面
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageFactory fileStorageFactory;

    public FileUploadVO upload(MultipartFile file, String path) {
        return fileStorageFactory.getStorageService().upload(file, path);
    }

    public String getFileUrl(String fileKey) {
        return fileStorageFactory.getStorageService().getFileUrl(fileKey);
    }

    public boolean delete(String fileKey) {
        return fileStorageFactory.getStorageService().delete(fileKey);
    }

    public InputStream getInputStream(String fileKey) {
        return fileStorageFactory.getStorageService().getInputStream(fileKey);
    }
}
