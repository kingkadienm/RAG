package com.wangzs.rag.chunk;

import com.wangzs.rag.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文本分块器
 * 使用递归字符分块策略（RecursiveCharacterTextSplitter）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TextSplitter {

    private final ConfigService configService;

    // 分隔符优先级（从高到低）
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", " ", ""};

    /**
     * 获取配置的 chunkSize（每次计算以支持动态配置）
     */
    private int getChunkSize() {
        return Math.max(10, configService.getInt("rag.chunk.size", 500));
    }

    /**
     * 获取配置的 chunkOverlap（确保小于 chunkSize）
     */
    private int getChunkOverlap(int chunkSize) {
        int overlap = configService.getInt("rag.chunk.overlap", 50);
        return Math.min(Math.max(0, overlap), chunkSize - 1);
    }

    /**
     * 对文本进行分块
     */
    public List<Chunk> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSize = getChunkSize();
        int chunkOverlap = getChunkOverlap(chunkSize);

        // 1. 递归拆分成不超过 chunkSize 的基础文本片段
        List<String> rawSplits = recursiveSplit(text, SEPARATORS, 0, chunkSize);

        // 2. 将基础片段按 overlap 合并成最终 Chunk
        List<Chunk> chunks = mergeSplits(rawSplits, chunkSize, chunkOverlap);

        log.info("文本分块完成: total={}, chunks={}, chunkSize={}, chunkOverlap={}",
                text.length(), chunks.size(), chunkSize, chunkOverlap);
        return chunks;
    }

    /**
     * 递归切分：根据分隔符逐级拆分文本，直到片段长度 <= chunkSize
     */
    private List<String> recursiveSplit(String text, String[] separators, int sepIndex, int chunkSize) {
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        // 如果分隔符穷尽，直接硬切
        if (sepIndex >= separators.length) {
            List<String> fixed = new ArrayList<>();
            for (int i = 0; i < text.length(); i += chunkSize) {
                fixed.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return fixed;
        }

        String sep = separators[sepIndex];
        List<String> result = new ArrayList<>();

        if (sep.isEmpty()) {
            // 按字符强行按 chunkSize 切分
            for (int i = 0; i < text.length(); i += chunkSize) {
                result.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return result;
        }

        String[] parts = text.split(java.util.regex.Pattern.quote(sep));
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            // 补回被 split 移除的分隔符（非末尾元素）
            String piece = (i < parts.length - 1) ? part + sep : part;

            if (piece.length() > chunkSize) {
                // 当前片段仍然超长，使用降低一级的分隔符继续拆分
                result.addAll(recursiveSplit(piece, separators, sepIndex + 1, chunkSize));
            } else {
                result.add(piece);
            }
        }

        return result;
    }

    /**
     * 合并拆分后的文本片段，并应用 overlap 重叠逻辑
     */
    private List<Chunk> mergeSplits(List<String> splits, int chunkSize, int chunkOverlap) {
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int index = 0;

        for (String piece : splits) {
            if (currentChunk.length() + piece.length() > chunkSize && currentChunk.length() > 0) {
                // 提交当前构建好的 Chunk
                String content = currentChunk.toString().trim();
                if (!content.isEmpty()) {
                    chunks.add(createChunk(index++, content));
                }

                // 处理 Overlap：保留 currentChunk 尾部长度为 chunkOverlap 的内容
                int keepLength = Math.min(currentChunk.length(), chunkOverlap);
                String overlapText = currentChunk.substring(currentChunk.length() - keepLength);

                currentChunk.setLength(0);
                currentChunk.append(overlapText);
            }
            currentChunk.append(piece);
        }

        if (currentChunk.length() > 0) {
            String content = currentChunk.toString().trim();
            if (!content.isEmpty()) {
                chunks.add(createChunk(index, content));
            }
        }

        return chunks;
    }

    private Chunk createChunk(int index, String content) {
        Chunk chunk = new Chunk();
        chunk.setIndex(index);
        chunk.setContent(content);
        chunk.setLength(content.length());
        return chunk;
    }
}