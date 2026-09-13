package com.wangzs.rag.chunk;

import com.wangzs.rag.chunk.model.DocumentElement;
import com.wangzs.rag.chunk.model.ElementType;
import com.wangzs.rag.chunk.model.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一文档模型构建器
 * <p>
 * 将当前解析器返回的纯文本转换为 ParsedDocument。
 * <p>
 * 后续 Parser 直接输出 ParsedDocument 后，该类可逐步缩小职责范围或移除。
 */
@Component
public class ParsedDocumentBuilder {

    /**
     * 从纯文本构建 ParsedDocument
     *
     * @param text         全文纯文本
     * @param documentId   文档 ID（可为 null，后续补充）
     * @param kbId         知识库 ID（可为 null）
     * @param fileName     原始文件名
     * @param fileType     文件扩展名
     * @param title        文档标题（可为 null，取文件名作为默认值）
     * @return ParsedDocument
     */
    public ParsedDocument fromText(String text, Long documentId, Long kbId,
                                   String fileName, String fileType, String title) {
        String effectiveTitle = (title != null && !title.isBlank())
                ? title.trim()
                : (fileName != null ? fileName : "");

        // 将纯文本按空行分段为 DocumentElement 列表
        List<DocumentElement> elements = buildElements(text);

        return ParsedDocument.builder()
                .documentId(documentId)
                .kbId(kbId)
                .fileName(fileName)
                .fileType(fileType)
                .title(effectiveTitle)
                .content(text)
                .elements(elements)
                .build();
    }

    /**
     * 将纯文本按空行分段为 DocumentElement 列表
     */
    private List<DocumentElement> buildElements(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<DocumentElement> elements = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\s*\\n");

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                elements.add(new DocumentElement(
                        ElementType.TEXT, 0, trimmed, null, null
                ));
            }
        }

        return elements;
    }
}
