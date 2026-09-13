package com.wangzs.rag.chunk.markdown;

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
 * Markdown 切片策略
 * <p>
 * 将 Markdown 文档按标题层级切片，生成 sectionPath。
 * <pre>
 * # Java 基础
 * ## 集合
 * ### ArrayList
 * 正文...
 *
 * → sectionPath: "Java 基础 > 集合 > ArrayList"
 * </pre>
 */
@Component
@Order(1)
public class MarkdownChunkingStrategy implements ChunkingStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(#{1,6})\\s+(.+)$", Pattern.MULTILINE
    );

    @Override
    public boolean supports(String fileType) {
        return "md".equalsIgnoreCase(fileType)
                || "markdown".equalsIgnoreCase(fileType);
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        if (document == null || document.getElements() == null || document.getElements().isEmpty()) {
            return List.of();
        }

        // 构建标题栈和 Section 列表
        List<Section> sections = buildSections(document.getElements());

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

    /**
     * 根据元素列表构建 Section
     */
    private List<Section> buildSections(List<DocumentElement> elements) {
        List<Section> sections = new ArrayList<>();

        // 标题栈：维护当前各级标题 {level → title}
        Map<Integer, String> headingStack = new LinkedHashMap<>();
        StringBuilder currentContent = new StringBuilder();

        for (DocumentElement element : elements) {
            if (element.getType() == ElementType.HEADING) {
                // 遇到预解析的 HEADING 元素，保存之前的 Section
                if (currentContent.length() > 0) {
                    sections.add(new Section(buildPath(headingStack), currentContent.toString()));
                    currentContent.setLength(0);
                }

                int level = element.getLevel();
                headingStack.keySet().removeIf(l -> l >= level);
                headingStack.put(level, element.getContent());

            } else if (element.getType() == ElementType.TEXT) {
                // TEXT 元素：尝试从中解析 Markdown 标题
                String text = element.getContent();
                if (text == null) {
                    continue;
                }

                // 检查是否包含 Markdown 标题
                Matcher matcher = HEADING_PATTERN.matcher(text);
                if (!matcher.find()) {
                    // 没有标题，作为普通内容追加
                    appendContent(currentContent, text);
                    continue;
                }

                // 包含标题：先处理标题之前的内容（如果有）
                matcher.reset();

                int lastEnd = 0;
                boolean first = true;

                while (matcher.find()) {
                    // 标题前的正文
                    if (matcher.start() > lastEnd) {
                        String before = text.substring(lastEnd, matcher.start()).trim();
                        if (!before.isEmpty()) {
                            if (first && currentContent.length() > 0) {
                                sections.add(new Section(buildPath(headingStack), currentContent.toString()));
                                currentContent.setLength(0);
                                first = false;
                            }
                            appendContent(currentContent, before);
                        }
                    }

                    // 标题前的内容先提交
                    if (currentContent.length() > 0) {
                        sections.add(new Section(buildPath(headingStack), currentContent.toString()));
                        currentContent.setLength(0);
                    }
                    first = false;

                    // 压入标题栈
                    String headingText = matcher.group(2).trim();
                    int level = matcher.group(1).length();
                    headingStack.keySet().removeIf(l -> l >= level);
                    headingStack.put(level, headingText);

                    lastEnd = matcher.end();
                }

                // 最后一个标题之后的内容
                if (lastEnd < text.length()) {
                    String after = text.substring(lastEnd).trim();
                    if (!after.isEmpty()) {
                        appendContent(currentContent, after);
                    }
                }

            } else {
                // 其他类型元素：追加到当前内容
                if (element.getContent() != null) {
                    appendContent(currentContent, element.getContent());
                }
            }
        }

        // 最后一个 Section
        if (currentContent.length() > 0) {
            sections.add(new Section(buildPath(headingStack), currentContent.toString()));
        }

        return sections;
    }

    private void appendContent(StringBuilder sb, String content) {
        if (content == null) {
            return;
        }

        // 过滤 Markdown 结构分隔符（:::, ---），避免生成无意义的微小 Chunk
        String[] lines = content.split("\n", -1);
        StringBuilder filtered = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals(":::") || trimmed.equals("---")) {
                continue;
            }
            filtered.append(line).append("\n");
        }
        String filteredContent = filtered.toString();
        if (filteredContent.isBlank()) {
            return;
        }

        sb.append(filteredContent);
    }

    /**
     * 根据标题栈构建 sectionPath
     */
    private String buildPath(Map<Integer, String> headingStack) {
        if (headingStack.isEmpty()) {
            return "";
        }
        return String.join(" > ", headingStack.values());
    }

    /**
     * 内部 Section 结构
     */
    private record Section(String path, String content) {
    }
}
