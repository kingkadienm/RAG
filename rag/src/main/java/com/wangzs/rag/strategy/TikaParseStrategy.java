package com.wangzs.rag.strategy;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

/**
 * Tika 通用文件解析策略
 * 支持 PDF, Word, Excel, PPT, TXT, MD 等所有 Tika 支持的格式
 */
@Slf4j
@Component
public class TikaParseStrategy implements FileParseStrategy {

    private static final AutoDetectParser PARSER = new AutoDetectParser();

    /**
     * 最大文本长度限制（10MB 字符），防止内存溢出
     */
    private static final int MAX_TEXT_LENGTH = 10 * 1024 * 1024;

    @Override
    public boolean supports(String fileType) {
        // Tika 通用策略支持所有类型
        return true;
    }

    @Override
    public String parse(InputStream inputStream, String fileName, String mimeType) throws Exception {
        if (inputStream == null) {
            return "";
        }

        try {
            // 限制最大文本长度，防止大文件 OOM
            ContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
            Metadata metadata = new Metadata();
            PARSER.parse(inputStream, handler, metadata);
            String text = handler.toString();

            // 清洗文本
            text = cleanText(text);

            log.info("Tika 解析完成: fileName={}, length={} chars", fileName, text.length());
            return text;
        } catch (Exception e) {
            log.error("Tika 解析失败: fileName={}", fileName, e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清洗文本内容
     * - 将 \r\n 统一为 \n
     * - 将 \r 统一为 \n
     * - 去除每行首尾的空格
     * - 将 3 个及以上连续换行替换为 2 个换行（保留段落）
     * - 将多个连续空格/制表符替换为单个空格
     * - 去除首尾空白
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                // 将 \r\n 统一为 \n
                .replaceAll("\\r\\n", "\n")
                // 将 \r 统一为 \n
                .replaceAll("\\r", "\n")
                // 去除每行首尾的空格
                .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
                // 将 3 个及以上连续换行替换为 2 个换行
                .replaceAll("\\n{3,}", "\n\n")
                // 将多个连续空格/制表符替换为单个空格
                .replaceAll("[ \\t]+", " ")
                // 去除首尾空白
                .trim();
    }
}
