package com.wangzs.rag.controller;

import com.wangzs.rag.common.result.ApiResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @className: TestController
 * @description:
 * @author: wangzs
 * @date: 2026-09-11 12:29
 */
@RestController
@RequestMapping("/test")
public class TestController {

    private final VectorStore vectorStore;

    public TestController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 测试向量数据写入 PGVector
     */
    @PostMapping("/vector/import")
    public ApiResult<String> importDocument(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        String category = request.getOrDefault("category", "default");
        org.springframework.ai.document.Document rawDocument = new org.springframework.ai.document.Document(content, Map.of("category", category));
        // 使用标准的 Token 文本切片器 (如每片段 800 token，重叠 100 token)
        TokenTextSplitter splitter = new TokenTextSplitter(800, 100, 5, 10000, true);
        List<org.springframework.ai.document.Document> splitDocuments = splitter.apply(List.of(rawDocument));

        // 向量化并保存至 PGVector
        vectorStore.accept(splitDocuments);
        return ApiResult.success(
                "数据切片并写入 PGVector 成功，生成片段数量：" + splitDocuments.size()
        );
    }

    /**
     * 测试 PGVector 向量检索
     */
    @GetMapping("/vector/search")
    public ApiResult<List<Document>> searchVector(
            @RequestParam("query") String query) {

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .build()
        );
        return ApiResult.success(results);
    }
}

