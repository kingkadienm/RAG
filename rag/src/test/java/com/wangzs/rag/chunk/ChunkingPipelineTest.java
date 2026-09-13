package com.wangzs.rag.chunk;

import com.wangzs.rag.chunk.common.LengthSplitter;
import com.wangzs.rag.chunk.common.SpecialContentDetector;
import com.wangzs.rag.chunk.common.SpecialContentHandler;
import com.wangzs.rag.chunk.markdown.MarkdownChunkingStrategy;
import com.wangzs.rag.chunk.model.DocumentElement;
import com.wangzs.rag.chunk.model.ElementType;
import com.wangzs.rag.chunk.model.ParsedDocument;
import com.wangzs.rag.chunk.ChunkingPipeline;
import com.wangzs.rag.chunk.txt.TxtChunkingStrategy;
import com.wangzs.rag.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * ChunkingPipeline 单元测试
 * <p>
 * 覆盖：
 * <ul>
 *   <li>MarkdownChunkingStrategy → sectionPath 生成</li>
 *   <li>LengthSplitter → maxTokens 约束</li>
 *   <li>LengthSplitter → embeddingText + metadata 构建</li>
 *   <li>SpecialContentHandler → FAQ 问答对拆分</li>
 *   <li>DefaultChunkingStrategy → 未知类型兜底</li>
 *   <li>ChunkingPipeline → 端到端流程</li>
 * </ul>
 */
class ChunkingPipelineTest {

    // ===================================================================
    //  MarkdownChunkingStrategy: sectionPath 生成
    // ===================================================================
    @Test
    @DisplayName("Markdown: 标题栈 → sectionPath \"Java > 集合 > ArrayList\"")
    void markdown_sectionPath() {
        MarkdownChunkingStrategy strategy = new MarkdownChunkingStrategy();

        ParsedDocument doc = buildParsedDocument(1L, 1L, "Java指南.md", "md", "Java指南",
                List.of(
                        heading(1, "Java 基础"),
                        paragraph("Java 是一种编程语言。"),
                        heading(2, "集合框架"),
                        heading(3, "ArrayList"),
                        paragraph("ArrayList 是动态数组。"),
                        heading(3, "LinkedList"),
                        paragraph("LinkedList 是双向链表。"),
                        heading(2, "IO 流"),
                        paragraph("IO 流用于文件读写。")
                )
        );

        List<Chunk> chunks = strategy.chunk(doc);

        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0).getSectionPath()).isEqualTo("Java 基础");
        assertThat(chunks.get(0).getContent()).contains("Java 是一种编程语言");
        assertThat(chunks.get(1).getSectionPath()).isEqualTo("Java 基础 > 集合框架 > ArrayList");
        assertThat(chunks.get(1).getContent()).contains("ArrayList 是动态数组");
        assertThat(chunks.get(2).getSectionPath()).isEqualTo("Java 基础 > 集合框架 > LinkedList");
        assertThat(chunks.get(2).getContent()).contains("LinkedList 是双向链表");
        assertThat(chunks.get(3).getSectionPath()).isEqualTo("Java 基础 > IO 流");
        assertThat(chunks.get(3).getContent()).contains("IO 流用于文件读写");
    }

    // ===================================================================
    //  LengthSplitter: maxTokens 约束
    // ===================================================================
    @Test
    @DisplayName("LengthSplitter: 未超长 Chunk 直接保留，不产生 overlap")
    void lengthSplitter_withinLimit() {
        ConfigService configService = mockConfig("rag.chunk.max-tokens", "800",
                "rag.chunk.overlap-tokens", "100");
        LengthSplitter splitter = new LengthSplitter(configService, mock(com.wangzs.rag.chunk.TextSplitter.class), new SpecialContentHandler());

        Chunk base = Chunk.builder()
                .index(0)
                .content("短内容")
                .length("短内容".length())
                .documentId(1L)
                .kbId(1L)
                .title("标题")
                .sectionPath("章节")
                .build();

        List<Chunk> result = splitter.split(List.of(base), buildParsedDocument(1L, 1L, "t.md", "md", "标题", List.of()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("短内容");
        assertThat(result.get(0).getTokenCount()).isGreaterThan(0);
        assertThat(result.get(0).getMetadata()).isNotNull();
        assertThat(result.get(0).getEmbeddingText()).contains("文档：标题");
        assertThat(result.get(0).getEmbeddingText()).contains("章节：章节");
    }

    // ===================================================================
    //  LengthSplitter: embeddingText + metadata 构建
    // ===================================================================
    @Test
    @DisplayName("LengthSplitter: embeddingText = 文档 + 章节 + 内容，metadata 含 key 信息")
    void lengthSplitter_embeddingTextAndMetadata() {
        ConfigService configService = mockConfig("rag.chunk.max-tokens", "800",
                "rag.chunk.overlap-tokens", "100");
        LengthSplitter splitter = new LengthSplitter(configService, mock(com.wangzs.rag.chunk.TextSplitter.class), new SpecialContentHandler());

        Chunk base = Chunk.builder()
                .index(0)
                .content("纳税资质：小规模纳税人")
                .length("纳税资质：小规模纳税人".length())
                .documentId(1001L)
                .kbId(1L)
                .title("集团开票信息汇总")
                .sectionPath("集团开票信息汇总 > 阿里巴巴健康集团")
                .build();

        List<Chunk> result = splitter.split(List.of(base), buildParsedDocument(1001L, 1L, "开票信息.md", "md", "集团开票信息汇总", List.of()));

        assertThat(result).hasSize(1);
        Chunk chunk = result.get(0);

        // embeddingText 结构
        String embeddingText = chunk.getEmbeddingText();
        assertThat(embeddingText).contains("文档：集团开票信息汇总");
        assertThat(embeddingText).contains("章节：集团开票信息汇总 > 阿里巴巴健康集团");
        assertThat(embeddingText).contains("纳税资质：小规模纳税人");

        // metadata
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = chunk.getMetadata();
        assertThat(meta.get("documentId")).isEqualTo(1001L);
        assertThat(meta.get("kbId")).isEqualTo(1L);
        assertThat(meta.get("fileName")).isEqualTo("开票信息.md");
        assertThat(meta.get("fileType")).isEqualTo("md");
        assertThat(meta.get("chunkIndex")).isEqualTo(0);
        assertThat(meta.get("sectionPath")).isEqualTo("集团开票信息汇总 > 阿里巴巴健康集团");
    }

    // ===================================================================
    //  SpecialContentHandler: FAQ 问答对拆分
    // ===================================================================
    @Test
    @DisplayName("FAQ: Q+A 问答对作为一个 Chunk，不拆分")
    void specialContent_faqSplit() {
        SpecialContentDetector detector = new SpecialContentDetector();

        assertThat(detector.isFAQ("Q：如何申请发票？\nA：登录系统后进入...")).isTrue();
        assertThat(detector.isFAQ("普通文本")).isFalse();

        List<SpecialContentDetector.FAQPair> pairs = detector.splitFAQ(
                "Q：如何申请发票？\nA：登录系统后进入...\nQ：支持哪些发票类型？\nA：增值税普通发票..."
        );

        assertThat(pairs).hasSize(2);
        assertThat(pairs.get(0).question()).contains("如何申请发票");
        assertThat(pairs.get(0).answer()).contains("登录系统");
        assertThat(pairs.get(1).question()).contains("发票类型");
    }

    // ===================================================================
    //  DefaultChunkingStrategy: 未知类型兜底
    // ===================================================================
    @Test
    @DisplayName("Default: 未知类型按空行分段兜底")
    void defaultStrategy_fallback() {
        DefaultChunkingStrategy strategy = new DefaultChunkingStrategy();

        // supports 返回 false
        assertThat(strategy.supports("xml")).isFalse();

        // 空行分段
        List<Chunk> chunks = strategy.chunk(buildParsedDocument(1L, 1L, "t.unknown", "unknown", "标题",
                List.of(
                        new DocumentElement(ElementType.TEXT, 0, "第一段\n\n第二段\n\n第三段", null, null)
                )
        ));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getContent()).isEqualTo("第一段");
        assertThat(chunks.get(1).getContent()).isEqualTo("第二段");
        assertThat(chunks.get(2).getContent()).isEqualTo("第三段");
    }

    // ===================================================================
    //  ChunkingPipeline: 端到端流程（Markdown）
    // ===================================================================
    @Test
    @DisplayName("Pipeline: Markdown 端到端 — 结构切片 + 长度约束 + embeddingText + metadata")
    void pipeline_markdownEndToEnd() {
        ConfigService configService = mockConfig("rag.chunk.max-tokens", "800",
                "rag.chunk.overlap-tokens", "100");
        MarkdownChunkingStrategy markdownStrategy = new MarkdownChunkingStrategy();
        ChunkingStrategyFactory factory = new ChunkingStrategyFactory(List.of(markdownStrategy));
        LengthSplitter lengthSplitter = new LengthSplitter(configService, mock(com.wangzs.rag.chunk.TextSplitter.class), new SpecialContentHandler());
        ChunkingPipeline pipeline = new ChunkingPipeline(factory, lengthSplitter);

        ParsedDocument doc = buildParsedDocument(1L, 1L, "test.md", "md", "测试文档",
                List.of(
                        heading(1, "测试文档"),
                        heading(2, "第一章"),
                        paragraph("这是第一章的内容。"),
                        heading(2, "第二章"),
                        paragraph("这是第二章的内容。")
                )
        );

        List<Chunk> result = pipeline.execute(doc);

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        // First chunk: "测试文档 > 第一章"
        assertThat(result.get(0).getSectionPath()).isEqualTo("测试文档 > 第一章");
        assertThat(result.get(0).getContent()).contains("第一章");
        assertThat(result.get(0).getTitle()).isEqualTo("测试文档");
        assertThat(result.get(0).getDocumentId()).isEqualTo(1L);
        assertThat(result.get(0).getKbId()).isEqualTo(1L);
        assertThat(result.get(0).getIndex()).isEqualTo(0);

        // 所有 Chunk 都有 embeddingText 和 metadata
        for (Chunk chunk : result) {
            assertThat(chunk.getEmbeddingText()).contains("测试文档");
            assertThat(chunk.getMetadata()).isNotNull();
            assertThat(chunk.getMetadata().get("fileType")).isEqualTo("md");
        }
    }

    // ===================================================================
    //  ChunkingPipeline: 超长内容触发 TextSplitter 兜底
    // ===================================================================
    @Test
    @DisplayName("Pipeline: 超长 Chunk 触发 TextSplitter 兜底切分 + overlap")
    void pipeline_overLongContent_triggersTextSplitter() {
        ConfigService configService = mockConfig("rag.chunk.max-tokens", "10",
                "rag.chunk.overlap-tokens", "3");

        // 使用真实 TextSplitter（需要 ConfigService 构造参数）
        com.wangzs.rag.chunk.TextSplitter realSplitter = new com.wangzs.rag.chunk.TextSplitter(configService);

        MarkdownChunkingStrategy markdownStrategy = new MarkdownChunkingStrategy();
        ChunkingStrategyFactory factory = new ChunkingStrategyFactory(List.of(markdownStrategy));
        LengthSplitter lengthSplitter = new LengthSplitter(configService, realSplitter, new SpecialContentHandler());
        ChunkingPipeline pipeline = new ChunkingPipeline(factory, lengthSplitter);

        // 超长内容（估算 token > 10）
        String longContent = "第一段内容\n\n第二段内容\n\n第三段内容\n\n第四段内容";
        ParsedDocument doc = buildParsedDocument(1L, 1L, "test.md", "md", "测试",
                List.of(
                        heading(1, "测试"),
                        new DocumentElement(ElementType.PARAGRAPH, 0, longContent, null, null)
                )
        );

        List<Chunk> result = pipeline.execute(doc);

        // TextSplitter 被调用 → 结果切分为多个 Chunk
        assertThat(result).hasSizeGreaterThan(1);
    }

    // ===================================================================
    //  ChunkingPipeline: TXT 策略（无结构）
    // ===================================================================
    @Test
    @DisplayName("Pipeline: TXT 文件按空行分段")
    void pipeline_txtByEmptyLine() {
        ConfigService configService = mockConfig("rag.chunk.max-tokens", "800",
                "rag.chunk.overlap-tokens", "100");
        TxtChunkingStrategy txtStrategy = new TxtChunkingStrategy();
        ChunkingStrategyFactory factory = new ChunkingStrategyFactory(List.of(txtStrategy));
        LengthSplitter lengthSplitter = new LengthSplitter(configService, mock(com.wangzs.rag.chunk.TextSplitter.class), new SpecialContentHandler());
        ChunkingPipeline pipeline = new ChunkingPipeline(factory, lengthSplitter);

        // TXT 每个 TEXT 元素代表一个段落
        ParsedDocument doc = buildParsedDocument(1L, 1L, "test.txt", "txt", "文本文件",
                List.of(
                        new DocumentElement(ElementType.TEXT, 0, "第一段", null, null),
                        new DocumentElement(ElementType.TEXT, 0, "\n", null, null),
                        new DocumentElement(ElementType.TEXT, 0, "第二段", null, null),
                        new DocumentElement(ElementType.TEXT, 0, "\n", null, null),
                        new DocumentElement(ElementType.TEXT, 0, "第三段", null, null)
                )
        );

        List<Chunk> result = pipeline.execute(doc);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getSectionPath()).isEmpty();
        assertThat(result.get(0).getContent()).isEqualTo("第一段");
        assertThat(result.get(1).getContent()).isEqualTo("第二段");
        assertThat(result.get(2).getContent()).isEqualTo("第三段");
    }

    // ===================================================================
    //  动态文件测试：读取 samples 目录下的真实文件，跑通完整 Pipeline
    // ===================================================================

    @TestFactory
    @DisplayName("动态文件测试：读取 samples 目录下的文件，验证 Pipeline 输出")
    Stream<DynamicTest> dynamicFileTests() throws Exception {
        java.nio.file.Path samplesDir = java.nio.file.Paths.get(
                "src/test/resources/samples"
        ).toAbsolutePath().normalize();

        System.out.println(samplesDir.toAbsolutePath());
        if (!java.nio.file.Files.exists(samplesDir)) {
            return Stream.of(DynamicTest.dynamicTest(
                    "samples 目录不存在，跳过", () -> {
                    }
            ));
        }

        try (Stream<java.nio.file.Path> files = java.nio.file.Files.list(samplesDir)) {
            List<java.nio.file.Path> sampleFiles = files
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();

            if (sampleFiles.isEmpty()) {
                return Stream.of(DynamicTest.dynamicTest(
                        "samples 目录为空，跳过", () -> {
                        }
                ));
            }

            return sampleFiles.stream()
                    .map(file -> DynamicTest.dynamicTest(
                            "文件: " + file.getFileName(),
                            () -> {
                                String fileName = file.getFileName().toString();
                                String fileType = getFileExtension(fileName);
                                testFileThroughPipeline(file, fileType, fileName);
                            }
                    ));
        }
    }

    /**
     * 将单个文件通过完整 Pipeline 跑一遍，做基本校验
     */
    private void testFileThroughPipeline(java.nio.file.Path file, String fileType, String fileName) throws Exception {
        // 1. 读取文件内容
        String content = java.nio.file.Files.readString(file).trim();
        assertThat(content).isNotBlank();

        // 2. 构建 ParsedDocument
        ParsedDocument document = buildParsedDocument(
                999L, 1L, fileName, fileType, fileName, content
        );

        // 3. 选择策略并执行 Pipeline
        ConfigService configService = mockConfig(
                "rag.chunk.max-tokens", "800",
                "rag.chunk.overlap-tokens", "100",
                "rag.chunk.size", "500",
                "rag.chunk.overlap", "50"
        );
        ChunkingStrategyFactory factory = new ChunkingStrategyFactory(
                List.of(
                        new MarkdownChunkingStrategy(),
                        new TxtChunkingStrategy()
                )
        );
        // 使用真实 TextSplitter（需要 ConfigService 构造参数）
        com.wangzs.rag.chunk.TextSplitter textSplitter = new com.wangzs.rag.chunk.TextSplitter(configService);
        LengthSplitter lengthSplitter = new LengthSplitter(
                configService,
                textSplitter,
                new SpecialContentHandler()
        );
        ChunkingPipeline pipeline = new ChunkingPipeline(factory, lengthSplitter);

        List<Chunk> chunks = pipeline.execute(document);

        // 4. 基本断言
        assertThat(chunks).as("文件 " + fileName + " 应产出至少 1 个 Chunk").isNotEmpty();
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(1);

        // 5. 每个 Chunk 必须携带文档上下文
        for (Chunk chunk : chunks) {
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getTitle()).isEqualTo(fileName);
            assertThat(chunk.getDocumentId()).isEqualTo(999L);
            assertThat(chunk.getKbId()).isEqualTo(1L);
            assertThat(chunk.getMetadata()).isNotNull();
            assertThat(chunk.getMetadata().get("fileType")).isEqualTo(fileType);
            assertThat(chunk.getMetadata().get("fileName")).isEqualTo(fileName);
            assertThat(chunk.getEmbeddingText()).isNotNull();
            assertThat(chunk.getEmbeddingText()).contains(fileName);
        }

        // 6. chunkIndex 连续
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getIndex()).isEqualTo(i);
        }

        // 7. 保存分片结果到 test-output/chunks/ 目录
        saveChunksToFile(fileName, chunks);
    }

    /**
     * 将 Chunk 列表写入 target/test-output/chunks/{fileName}.chunks.txt，方便人工检查
     */
    private static void saveChunksToFile(String sourceFileName, List<Chunk> chunks) throws Exception {
        java.nio.file.Path outDir = java.nio.file.Paths.get("target/test-output/chunks");
        java.nio.file.Files.createDirectories(outDir);

        String baseName = sourceFileName;
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        java.nio.file.Path outFile = outDir.resolve(baseName + ".chunks.txt");
        StringBuilder sb = new StringBuilder();
        sb.append("来源文件: ").append(sourceFileName).append("\n");
        sb.append("Chunk 总数: ").append(chunks.size()).append("\n");
        sb.append("=".repeat(60)).append("\n\n");

        for (Chunk chunk : chunks) {
            sb.append("Chunk ").append(chunk.getIndex()).append("\n");
            sb.append("-".repeat(40)).append("\n");
            sb.append("sectionPath: ").append(chunk.getSectionPath()).append("\n");
            sb.append("tokenCount:   ").append(chunk.getTokenCount()).append("\n");
            sb.append("charCount:    ").append(chunk.getLength()).append("\n");
            sb.append("title:        ").append(chunk.getTitle()).append("\n");
            sb.append("documentId:   ").append(chunk.getDocumentId()).append("\n");
            sb.append("kbId:         ").append(chunk.getKbId()).append("\n");
            sb.append("metadata:     ").append(chunk.getMetadata()).append("\n");
            sb.append("\n--- embeddingText ---\n");
            sb.append(chunk.getEmbeddingText()).append("\n\n");
            sb.append("--- content ---\n");
            sb.append(chunk.getContent()).append("\n");
            sb.append("\n").append("=".repeat(60)).append("\n\n");
        }

        java.nio.file.Files.writeString(outFile, sb.toString());
    }

    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    // ===================================================================
    //  辅助方法
    // ===================================================================

    private static ParsedDocument buildParsedDocument(Long documentId, Long kbId, String fileName,
                                                      String fileType, String title,
                                                      List<DocumentElement> elements) {
        // 从 elements 拼接 content（供 DefaultChunkingStrategy 等使用）
        StringBuilder fullText = new StringBuilder();
        for (DocumentElement el : elements) {
            if (el.getContent() != null) {
                fullText.append(el.getContent());
                if (!el.getContent().endsWith("\n")) {
                    fullText.append("\n");
                }
            }
        }

        return ParsedDocument.builder()
                .documentId(documentId)
                .kbId(kbId)
                .fileName(fileName)
                .fileType(fileType)
                .title(title)
                .content(fullText.toString())
                .elements(elements)
                .build();
    }

    /**
     * 从纯文本构建 ParsedDocument（动态文件测试用）
     * 将整个文件内容作为一个 TEXT 元素，由 MarkdownChunkingStrategy 自行解析标题
     */
    private static ParsedDocument buildParsedDocument(Long documentId, Long kbId,
                                                      String fileName, String fileType,
                                                      String title, String content) {
        List<DocumentElement> elements = new java.util.ArrayList<>();
        if (content != null && !content.isBlank()) {
            // 将整个文件内容作为一个 TEXT 元素，由策略自行解析结构
            elements.add(new DocumentElement(ElementType.TEXT, 0, content, null, null));
        }

        return ParsedDocument.builder()
                .documentId(documentId)
                .kbId(kbId)
                .fileName(fileName)
                .fileType(fileType)
                .title(title)
                .content(content)
                .elements(elements)
                .build();
    }

    private static DocumentElement heading(int level, String content) {
        return new DocumentElement(ElementType.HEADING, level, content, null, null);
    }

    private static DocumentElement paragraph(String content) {
        return new DocumentElement(ElementType.PARAGRAPH, 0, content, null, null);
    }

    private static ConfigService mockConfig(String... keyValues) {
        ConfigService configService = mock(ConfigService.class);
        for (int i = 0; i < keyValues.length; i += 2) {
            int value = Integer.parseInt(keyValues[i + 1]);
            String key = keyValues[i];
            // stub 双参版本（rag.chunk.size / rag.chunk.max-tokens 均用此签名）
            when(configService.getInt(eq(key), anyInt())).thenReturn(value);
            // stub 单参版本（兜底）
            when(configService.getInt(eq(key))).thenReturn(value);
        }
        return configService;
    }
}
