package com.wangzs.rag.chunk.common;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.chunk.common.SpecialContentDetector;
import com.wangzs.rag.chunk.model.DocumentElement;
import com.wangzs.rag.chunk.model.ElementType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 特殊内容处理器
 * <p>
 * 对基础 Chunk 进行特殊内容识别和拆分：
 * <pre>
 * FAQ（问答对作为一个 Chunk）
 * 表格（整表作为一个 Chunk，保留表头）
 * 代码块（按类/方法/代码块整体保留）
 * JSON（按 Object / Array Item 拆分，保持结构完整）
 * </pre>
 * <p>
 * 调用时机：结构切片之后，长度约束之前。
 */
@Slf4j
@Component
public class SpecialContentHandler {

    /**
     * 处理特殊内容，返回处理后的 Chunk 列表
     */
    public List<Chunk> handle(List<Chunk> baseChunks, List<DocumentElement> elements) {
        if (baseChunks == null || baseChunks.isEmpty()) {
            return List.of();
        }

        List<Chunk> result = new ArrayList<>();

        for (Chunk chunk : baseChunks) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            // 优先检查 FAQ
            if (SpecialContentDetector.isFAQ(content)) {
                result.addAll(handleFAQ(chunk, content));
                continue;
            }

            // 检查 JSON
            if (SpecialContentDetector.isJson(content)) {
                result.addAll(handleJson(chunk, content));
                continue;
            }

            // 检查代码块
            if (SpecialContentDetector.isCodeBlock(content)) {
                result.addAll(handleCodeBlock(chunk, content));
                continue;
            }

            // 默认：保留原 Chunk
            result.add(chunk);
        }

        return result;
    }

    /**
     * 处理 FAQ：Q+A 作为一个 Chunk
     */
    private List<Chunk> handleFAQ(Chunk baseChunk, String content) {
        List<SpecialContentDetector.FAQPair> pairs = SpecialContentDetector.splitFAQ(content);
        List<Chunk> result = new ArrayList<>();

        if (pairs.isEmpty()) {
            // 无法拆分，保留原文
            result.add(baseChunk);
            return result;
        }

        for (SpecialContentDetector.FAQPair pair : pairs) {
            String faqContent = "Q：" + pair.question() + "\n\nA：" + pair.answer();

            Chunk chunk = Chunk.builder()
                    .index(baseChunk.getIndex())
                    .content(faqContent)
                    .length(faqContent.length())
                    .documentId(baseChunk.getDocumentId())
                    .kbId(baseChunk.getKbId())
                    .title(baseChunk.getTitle())
                    .sectionPath(baseChunk.getSectionPath())
                    .build();

            result.add(chunk);
        }

        return result;
    }

    /**
     * 处理 JSON：按顶层 Object / Array Item 拆分
     */
    private List<Chunk> handleJson(Chunk baseChunk, String content) {
        List<Chunk> result = new ArrayList<>();

        // 尝试按顶层 JSON 结构拆分
        List<String> jsonItems = splitTopLevelJson(content);

        if (jsonItems.size() <= 1) {
            // 只有一个 JSON 或无法拆分，保留原文
            result.add(baseChunk);
            return result;
        }

        int index = baseChunk.getIndex();
        for (String item : jsonItems) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Chunk chunk = Chunk.builder()
                    .index(index++)
                    .content(trimmed)
                    .length(trimmed.length())
                    .documentId(baseChunk.getDocumentId())
                    .kbId(baseChunk.getKbId())
                    .title(baseChunk.getTitle())
                    .sectionPath(baseChunk.getSectionPath())
                    .build();

            result.add(chunk);
        }

        return result;
    }

    /**
     * 按顶层 JSON 结构拆分
     */
    private List<String> splitTopLevelJson(String content) {
        List<String> items = new ArrayList<>();
        String trimmed = content.trim();

        // JSON Array：按顶层元素拆分
        if (trimmed.startsWith("[")) {
            items.addAll(splitJsonArray(trimmed));
            return items;
        }

        // JSON Object：按顶层 key 拆分
        if (trimmed.startsWith("{")) {
            items.addAll(splitJsonObject(trimmed));
            return items;
        }

        // 无法识别，返回原文
        items.add(content);
        return items;
    }

    /**
     * 拆分 JSON Array
     */
    private List<String> splitJsonArray(String content) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        int start = 1; // 跳过 [

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String item = content.substring(start, i).trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
                start = i + 1;
            }
        }

        // 最后一个元素
        String last = content.substring(start, content.length() - 1).trim();
        if (!last.isEmpty()) {
            items.add(last);
        }

        return items;
    }

    /**
     * 拆分 JSON Object
     */
    private List<String> splitJsonObject(String content) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        int start = 1; // 跳过 {

        for (int i = start; i < content.length() - 1; i++) {
            char c = content.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String item = content.substring(start, i).trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
                start = i + 1;
            }
        }

        String last = content.substring(start, content.length() - 1).trim();
        if (!last.isEmpty()) {
            items.add(last);
        }

        return items.isEmpty() ? List.of(content) : items;
    }

    /**
     * 处理代码块：按代码块整体保留，不拆分
     */
    private List<Chunk> handleCodeBlock(Chunk baseChunk, String content) {
        List<Chunk> result = new ArrayList<>();

        List<String> parts = SpecialContentDetector.extractCodeBlocks(content);
        int index = baseChunk.getIndex();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Chunk chunk = Chunk.builder()
                    .index(index++)
                    .content(trimmed)
                    .length(trimmed.length())
                    .documentId(baseChunk.getDocumentId())
                    .kbId(baseChunk.getKbId())
                    .title(baseChunk.getTitle())
                    .sectionPath(baseChunk.getSectionPath())
                    .build();
            result.add(chunk);
        }

        if (result.isEmpty()) {
            result.add(baseChunk);
        }

        return result;
    }
}
