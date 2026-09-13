package com.wangzs.rag.chunk.docx;

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
 * DOCX 切片策略
 * <p>
 * 识别 DOCX 文档中的结构化元素：
 * <pre>
 * Heading 1 / Heading 2 / Heading 3
 * Paragraph
 * Table
 * </pre>
 * <p>
 * 按 Heading 层级构建 sectionPath，每个 Heading 下的内容作为一个 Section。
 */
@Component
@Order(3)
public class DocxChunkingStrategy implements ChunkingStrategy {

    @Override
    public boolean supports(String fileType) {
        return "docx".equalsIgnoreCase(fileType);
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        if (document == null || document.getElements() == null || document.getElements().isEmpty()) {
            return List.of();
        }

        List<DocumentElement> elements = document.getElements();

        // 构建 Section 列表
        List<Section> sections = new ArrayList<>();

        // 标题栈：维护当前各级标题 {level → title}
        Map<Integer, String> headingStack = new LinkedHashMap<>();
        StringBuilder currentContent = new StringBuilder();

        for (DocumentElement element : elements) {
            ElementType type = element.getType();

            if (type == ElementType.HEADING) {
                // 遇到新标题，先保存之前的 Section
                if (currentContent.length() > 0) {
                    sections.add(new Section(buildPath(headingStack), currentContent.toString()));
                    currentContent.setLength(0);
                }

                // 更新标题栈
                int level = element.getLevel();
                headingStack.keySet().removeIf(l -> l >= level);
                headingStack.put(level, element.getContent());
            } else if (type == ElementType.PARAGRAPH) {
                appendContent(currentContent, element.getContent());
            } else if (type == ElementType.TABLE) {
                // 表格作为一个整体 Section
                if (currentContent.length() > 0) {
                    sections.add(new Section(buildPath(headingStack), currentContent.toString()));
                    currentContent.setLength(0);
                }
                String tableContent = element.getContent();
                if (tableContent != null && !tableContent.isBlank()) {
                    sections.add(new Section(
                            buildPath(headingStack),
                            "[表格]\n" + tableContent.trim()
                    ));
                }
            } else if (type == ElementType.CODE_BLOCK) {
                appendContent(currentContent, element.getContent());
            } else if (type == ElementType.LIST_ITEM) {
                appendContent(currentContent, element.getContent());
            }
        }

        // 最后一个 Section
        if (currentContent.length() > 0) {
            sections.add(new Section(buildPath(headingStack), currentContent.toString()));
        }

        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Section section : sections) {
            if (section.content == null || section.content.isBlank()) {
                continue;
            }

            Chunk chunk = Chunk.builder()
                    .index(chunkIndex++)
                    .content(section.content.trim())
                    .length(section.content.trim().length())
                    .documentId(document.getDocumentId())
                    .kbId(document.getKbId())
                    .title(document.getTitle())
                    .sectionPath(section.path)
                    .build();

            chunks.add(chunk);
        }

        return chunks;
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

    private String buildPath(Map<Integer, String> headingStack) {
        if (headingStack.isEmpty()) {
            return "";
        }
        return String.join(" > ", headingStack.values());
    }

    private record Section(String path, String content) {
    }
}
