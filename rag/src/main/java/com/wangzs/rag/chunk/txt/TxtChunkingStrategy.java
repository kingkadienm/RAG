package com.wangzs.rag.chunk.txt;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.chunk.ChunkingStrategy;
import com.wangzs.rag.chunk.model.DocumentElement;
import com.wangzs.rag.chunk.model.ElementType;
import com.wangzs.rag.chunk.model.ParsedDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TXT 切片策略
 * <p>
 * TXT 没有明确结构，按以下优先级切片：
 * <pre>
 * 空行 → 段落 → 句子
 * </pre>
 * 每个段落作为一个基础 Chunk，再由 LengthSplitter 做长度约束。
 */
@Component
@Order(2)
public class TxtChunkingStrategy implements ChunkingStrategy {

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType);
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        if (document == null || document.getElements() == null || document.getElements().isEmpty()) {
            return List.of();
        }

        // 如果 Parser 已经将 TXT 解析为 DocumentElement 列表，直接使用
        List<DocumentElement> elements = document.getElements();

        // 按空行分段：连续的非空行合并为一个段落
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (DocumentElement element : elements) {
            String text = element.getContent();
            if (text == null) {
                continue;
            }

            // 遇到空行或纯换行，结束当前段落
            if (text.trim().isEmpty() || text.equals("\n")) {
                if (current.length() > 0) {
                    paragraphs.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }

            current.append(text);
            if (!text.endsWith("\n")) {
                current.append("\n");
            }
        }

        if (current.length() > 0) {
            paragraphs.add(current.toString().trim());
        }

        // 如果元素列表都是 TEXT 类型的扁平结构，也可以直接按内容切分
        if (paragraphs.isEmpty() && document.getContent() != null) {
            String[] parts = document.getContent().split("\\n\\s*\\n");
            for (String part : parts) {
                if (!part.trim().isEmpty()) {
                    paragraphs.add(part.trim());
                }
            }
        }

        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }

            Chunk chunk = Chunk.builder()
                    .index(chunkIndex++)
                    .content(paragraph)
                    .length(paragraph.length())
                    .documentId(document.getDocumentId())
                    .kbId(document.getKbId())
                    .title(document.getTitle())
                    .sectionPath("")
                    .build();

            chunks.add(chunk);
        }

        return chunks;
    }
}
