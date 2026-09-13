package com.wangzs.rag.chunk.common;

import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.chunk.TextSplitter;
import com.wangzs.rag.chunk.model.ParsedDocument;
import com.wangzs.rag.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 长度约束切片器
 * <p>
 * 在结构/语义切片之后，判断每个 Section 是否超过 maxTokens：
 * <pre>
 * Semantic Section
 *     │
 *     ├── <= maxTokens  → 直接作为 Chunk
 *     └── > maxTokens   → TokenTextSplitter 兜底切分
 * </pre>
 * <p>
 * 只有超长的 Section 才会产生 overlap，正常语义 Chunk 不叠加 overlap。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LengthSplitter {

    private final ConfigService configService;
    private final TextSplitter textSplitter;
    private final SpecialContentHandler specialContentHandler;

    /**
     * 对基础 Chunk 列表应用长度约束
     *
     * @param baseChunks  结构/语义切片产生的 Chunk 列表（已有 content/sectionPath/documentId 等）
     * @param document    原始文档（用于补充元数据）
     * @return 经过特殊内容处理 + 长度约束后的 Chunk 列表
     */
    public List<Chunk> split(List<Chunk> baseChunks, ParsedDocument document) {
        if (baseChunks == null || baseChunks.isEmpty()) {
            return List.of();
        }

        // Step 1: 特殊内容预处理（FAQ / 表格 / 代码块 / JSON）
        List<Chunk> preprocessed = specialContentHandler.handle(
                baseChunks,
                document != null ? document.getElements() : List.of()
        );

        int maxTokens = getMaxTokens();
        int overlapTokens = getOverlapTokens();

        List<Chunk> result = new ArrayList<>();
        int globalIndex = 0;

        for (Chunk base : preprocessed) {
            int tokenCount = estimateTokenCount(base.getContent());

            if (tokenCount <= maxTokens) {
                // 未超长，直接保留
                Chunk chunk = buildChunk(base, globalIndex++, tokenCount, document);
                result.add(chunk);
            } else {
                // 超长，使用 TextSplitter 兜底切分
                List<com.wangzs.rag.chunk.Chunk> splits =
                        textSplitter.split(base.getContent());

                String overlapPrefix = "";
                for (com.wangzs.rag.chunk.Chunk split : splits) {
                    String mergedContent = overlapPrefix + split.getContent();
                    int mergedTokenCount = estimateTokenCount(mergedContent);

                    Chunk chunk = buildChunk(base, globalIndex++, mergedTokenCount, document);
                    chunk.setContent(mergedContent);
                    chunk.setLength(mergedContent.length());
                    result.add(chunk);

                    // 为下一次准备 overlap 前缀
                    int keepLen = Math.min(mergedContent.length(), overlapTokens);
                    overlapPrefix = mergedContent.substring(mergedContent.length() - keepLen);
                }
            }
        }

        log.info("长度约束完成: baseChunks={}, resultChunks={}, maxTokens={}, overlapTokens={}",
                baseChunks.size(), result.size(), maxTokens, overlapTokens);

        return result;
    }

    /**
     * 构建最终 Chunk（补充 documentId/kbId/title/metadata/embeddingText）
     */
    private Chunk buildChunk(Chunk base, int index, int tokenCount, ParsedDocument document) {
        // 构建 embeddingText = title + sectionPath + content
        String embeddingText = buildEmbeddingText(
                document.getTitle(),
                base.getSectionPath(),
                base.getContent()
        );

        // 构建 metadata map
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (document.getDocumentId() != null) {
            metadata.put("documentId", document.getDocumentId());
        }
        if (document.getKbId() != null) {
            metadata.put("kbId", document.getKbId());
        }
        metadata.put("fileName", document.getFileName());
        metadata.put("fileType", document.getFileType());
        metadata.put("chunkIndex", index);
        if (base.getSectionPath() != null && !base.getSectionPath().isEmpty()) {
            metadata.put("sectionPath", base.getSectionPath());
        }

        return Chunk.builder()
                .index(index)
                .content(base.getContent())
                .length(base.getContent() != null ? base.getContent().length() : 0)
                .documentId(document.getDocumentId())
                .kbId(document.getKbId())
                .title(document.getTitle())
                .sectionPath(base.getSectionPath())
                .tokenCount(tokenCount)
                .metadata(metadata)
                .embeddingText(embeddingText)
                .build();
    }

    /**
     * 构建 embeddingText
     * <pre>
     * 文档：{title}
     * 章节：{sectionPath}
     *
     * {content}
     * </pre>
     */
    private String buildEmbeddingText(String title, String sectionPath, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("文档：").append(title.trim()).append("\n");
        }
        if (sectionPath != null && !sectionPath.isBlank()) {
            sb.append("章节：").append(sectionPath.trim()).append("\n");
        }
        sb.append("\n");
        if (content != null) {
            sb.append(content.trim());
        }
        return sb.toString();
    }

    /**
     * 估算 Token 数量（简单按字符数 / 1.5，可后续替换为真实 Tokenizer）
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 1.5);
    }

    private int getMaxTokens() {
        return Math.max(10, configService.getInt("rag.chunk.max-tokens", 800));
    }

    private int getOverlapTokens() {
        int maxTokens = getMaxTokens();
        int overlap = configService.getInt("rag.chunk.overlap-tokens", 100);
        return Math.min(Math.max(0, overlap), maxTokens - 1);
    }
}
