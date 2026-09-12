package com.wangzs.rag.service;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.chunk.TextSplitter;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.strategy.FileParseStrategy;
import com.wangzs.rag.strategy.ParseStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * 文件解析服务
 * 负责文件内容提取 + 文本分块
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseService {

    private final ParseStrategyFactory parseStrategyFactory;
    private final TextSplitter textSplitter;

    /**
     * 解析文件并分块（推荐：基于 InputStream 流式解析，防止大文件 OOM）
     *
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @param fileType    文件类型/扩展名
     * @param mimeType    MIME类型
     * @return 分块列表
     */
    public List<Chunk> parseAndChunk(InputStream inputStream, String fileName, String fileType, String mimeType) {
        if (inputStream == null) {
            log.warn("文件输入流为空，跳过解析: fileName={}", fileName);
            return List.of();
        }

        // 1. 提取文本内容
        String textContent = parseOnly(inputStream, fileName, fileType, mimeType);

        if (!StringUtils.hasText(textContent)) {
            log.warn("解析文本内容为空: fileName={}, fileType={}", fileName, fileType);
            return List.of();
        }

        log.info("文件文本提取完成: fileName={}, textLength={}", fileName, textContent.length());

        // 2. 文本分块
        try {
            List<Chunk> chunks = textSplitter.split(textContent);
            log.info("文件解析与分块成功: fileName={}, chunksCount={}", fileName, chunks.size());
            return chunks;
        } catch (Exception e) {
            log.error("文本分块失败: fileName={}", fileName, e);
            throw BizException.of(ErrorCode.DOCUMENT_CHUNK_FAILED);
        }
    }

    /**
     * 兼容重载：解析字节数组（自动转为 InputStream 统一处理）
     */
    public List<Chunk> parseAndChunk(byte[] fileBytes, String fileName, String fileType, String mimeType) {
        if (fileBytes == null || fileBytes.length == 0) {
            log.warn("文件字节为空，跳过解析: fileName={}", fileName);
            return List.of();
        }
        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return parseAndChunk(inputStream, fileName, fileType, mimeType);
        } catch (Exception e) {
            log.error("字节数组解析失败: fileName={}", fileName, e);
            throw BizException.of(ErrorCode.PARSE_FAILED);
        }
    }

    /**
     * 仅解析文件内容，不进行分块（流式）
     */
    public String parseOnly(InputStream inputStream, String fileName, String fileType, String mimeType) {
        if (inputStream == null) {
            return "";
        }

        // 防御式补充 fileType
        String effectiveFileType = StringUtils.hasText(fileType) ? fileType : getFileExtension(fileName);

        // 1. 选择解析策略
        FileParseStrategy strategy = parseStrategyFactory.getStrategy(effectiveFileType);
        if (strategy == null) {
            log.error("未找到对应的文件解析策略: fileType={}, fileName={}", effectiveFileType, fileName);
            throw BizException.of(ErrorCode.PARSE_UNSUPPORTED_TYPE);
        }

        // 2. 执行解析
        try {
            return strategy.parse(inputStream, fileName, mimeType);
        } catch (Exception e) {
            log.error("文件内容提取失败: fileName={}, fileType={}", fileName, effectiveFileType, e);
            throw BizException.of(ErrorCode.DOCUMENT_CONTENT_EXTRACTION_FAILED);
        }
    }

    /**
     * 兼容重载：仅解析字节数组内容（不分块）
     */
    public String parseOnly(byte[] fileBytes, String fileName, String fileType, String mimeType) {
        if (fileBytes == null || fileBytes.length == 0) {
            return "";
        }
        try (InputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            return parseOnly(inputStream, fileName, fileType, mimeType);
        } catch (Exception e) {
            log.error("文件内容提取失败: fileName={}", fileName, e);
            throw BizException.of(ErrorCode.DOCUMENT_CONTENT_EXTRACTION_FAILED);
        }
    }

    /**
     * 获取文件扩展名（统一返回小写）
     */
    public String getFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}