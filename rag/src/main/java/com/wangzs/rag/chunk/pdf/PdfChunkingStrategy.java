package com.wangzs.rag.chunk.pdf;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.chunk.ChunkingStrategy;
import com.wangzs.rag.chunk.model.DocumentElement;
import com.wangzs.rag.chunk.model.ElementType;
import com.wangzs.rag.chunk.model.ParsedDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 切片策略
 * <p>
 * PDF 切片按以下优先级：
 * <pre>
 * Page → Title → Paragraph
 * </pre>
 * 尽可能保留页码（pageNumber）信息。
 * <p>
 * 每个 Page 内部按标题分段，sectionPath 中可携带页码标记。
 */
@Component
@Order(4)
public class PdfChunkingStrategy implements ChunkingStrategy {

    @Override
    public boolean supports(String fileType) {
        return "pdf".equalsIgnoreCase(fileType);
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        if (document == null || document.getElements() == null || document.getElements().isEmpty()) {
            return List.of();
        }

        List<DocumentElement> elements = document.getElements();

        // 按页码分组
        Map<Integer, List<DocumentElement>> pages = new LinkedHashMap<>();
        for (DocumentElement element : elements) {
            int pageNum = element.getPageNumber() != null ? element.getPageNumber() : 0;
            pages.computeIfAbsent(pageNum, k -> new ArrayList<>()).add(element);
        }

        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        // 全局标题栈（跨页保持）
        Map<Integer, String> globalHeadingStack = new LinkedHashMap<>();

        for (Map.Entry<Integer, List<DocumentElement>> entry : pages.entrySet()) {
            Integer pageNumber = entry.getKey();
            List<DocumentElement> pageElements = entry.getValue();

            // 页面内标题栈
            Map<Integer, String> pageHeadingStack = new LinkedHashMap<>();

            StringBuilder currentContent = new StringBuilder();

            for (DocumentElement element : pageElements) {
                ElementType type = element.getType();

                if (type == ElementType.HEADING) {
                    // 先保存之前的 Section
                    if (currentContent.length() > 0) {
                        String path = buildPath(globalHeadingStack, pageHeadingStack, pageNumber);
                        chunks.add(createChunk(document, chunkIndex++, path, currentContent.toString()));
                        currentContent.setLength(0);
                    }

                    int level = element.getLevel();
                    pageHeadingStack.keySet().removeIf(l -> l >= level);
                    pageHeadingStack.put(level, element.getContent());

                    // 同步到全局栈
                    globalHeadingStack.keySet().removeIf(l -> l >= level);
                    globalHeadingStack.put(level, element.getContent());
                } else if (type == ElementType.PARAGRAPH) {
                    appendContent(currentContent, element.getContent());
                } else if (type == ElementType.TABLE) {
                    if (currentContent.length() > 0) {
                        String path = buildPath(globalHeadingStack, pageHeadingStack, pageNumber);
                        chunks.add(createChunk(document, chunkIndex++, path, currentContent.toString()));
                        currentContent.setLength(0);
                    }
                    String tableContent = element.getContent();
                    if (tableContent != null && !tableContent.isBlank()) {
                        String path = buildPath(globalHeadingStack, pageHeadingStack, pageNumber);
                        chunks.add(createChunk(document, chunkIndex++, path, "[表格]\n" + tableContent.trim()));
                    }
                } else if (type == ElementType.CODE_BLOCK) {
                    appendContent(currentContent, element.getContent());
                } else if (type == ElementType.LIST_ITEM) {
                    appendContent(currentContent, element.getContent());
                }
            }

            // 页面最后一个 Section
            if (currentContent.length() > 0) {
                String path = buildPath(globalHeadingStack, pageHeadingStack, pageNumber);
                chunks.add(createChunk(document, chunkIndex++, path, currentContent.toString()));
            }
        }

        return chunks;
    }

    /**
     * 构建 sectionPath，可携带页码信息
     */
    private String buildPath(
            Map<Integer, String> globalStack,
            Map<Integer, String> pageStack,
            Integer pageNumber
    ) {
        List<String> parts = new ArrayList<>();

        // 优先使用页面级标题栈
        if (!pageStack.isEmpty()) {
            parts.addAll(pageStack.values());
        } else if (!globalStack.isEmpty()) {
            parts.addAll(globalStack.values());
        }

        if (pageNumber != null && pageNumber > 0) {
            parts.add("(第" + pageNumber + "页)");
        }

        return String.join(" > ", parts);
    }

    private void appendContent(StringBuilder sb, String content) {
        if (content == null) {
            return;
        }
        sb.append(content);
        if (!content.endsWith("\n")) {
            sb.append("\n");
        }
    }

    private Chunk createChunk(ParsedDocument document, int index, String sectionPath, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        return Chunk.builder()
                .index(index)
                .content(content.trim())
                .length(content.trim().length())
                .documentId(document.getDocumentId())
                .kbId(document.getKbId())
                .title(document.getTitle())
                .sectionPath(sectionPath)
                .build();
    }
}
