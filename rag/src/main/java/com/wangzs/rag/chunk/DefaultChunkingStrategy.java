package com.wangzs.rag.chunk;

import com.wangzs.rag.chunk.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认切片策略
 * <p>
 * 当没有匹配的特定文件类型策略时使用，直接按空行分段作为基础切片。
 */
@Slf4j
@Component
@Order(Integer.MAX_VALUE)
public class DefaultChunkingStrategy implements ChunkingStrategy {

    @Override
    public boolean supports(String fileType) {
        // 默认策略不支持任何特定类型，仅作为兜底
        return false;
    }

    @Override
    public List<Chunk> chunk(ParsedDocument document) {
        if (document == null || document.getContent() == null || document.getContent().isBlank()) {
            return Collections.emptyList();
        }

        String text = document.getContent();
        String[] paragraphs = text.split("\\n\\s*\\n");

        List<Chunk> chunks = new ArrayList<>();
        int index = 0;

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(Chunk.builder()
                        .index(index++)
                        .content(trimmed)
                        .length(trimmed.length())
                        .documentId(document.getDocumentId())
                        .kbId(document.getKbId())
                        .title(document.getTitle())
                        .sectionPath("")
                        .build());
            }
        }

        log.warn("使用默认切片策略（空行分段）：fileType={}, chunks={}", document.getFileType(), chunks.size());
        return chunks;
    }
}
