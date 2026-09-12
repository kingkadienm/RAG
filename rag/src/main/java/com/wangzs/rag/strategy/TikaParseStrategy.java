package com.wangzs.rag.strategy;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Tika 通用文件解析策略
 * 支持 PDF, Word, Excel, PPT, TXT, MD 等所有 Tika 支持的格式
 */
@Slf4j
@Component
public class TikaParseStrategy implements FileParseStrategy {

    private static final Tika TIKA = new Tika();
    private static final AutoDetectParser PARSER = new AutoDetectParser();

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
            ContentHandler handler = new BodyContentHandler(-1); // -1 = 无限制
            Metadata metadata = new Metadata();
            PARSER.parse(inputStream, handler, metadata);
            String text = handler.toString();

            // 清理多余空白
            text = text.replaceAll("\\r\\n", "\n")
                    .replaceAll("\\r", "\n")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();

            log.info("Tika 解析完成: fileName={}, length={} chars", fileName, text.length());
            return text;
        } catch (Exception e) {
            log.error("Tika 解析失败: fileName={}", fileName, e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }
}
