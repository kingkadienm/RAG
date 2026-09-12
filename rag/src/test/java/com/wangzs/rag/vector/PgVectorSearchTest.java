package com.wangzs.rag.vector;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * @className: PgVectorSearchTest
 * @description:
 * @author: wangzs
 * @date: 2026-09-12 12:49
 */
@SpringBootTest
class PgVectorSearchTest {

    @Resource
    private VectorStore vectorStore;

    @Test
    void searchTest() {

        String queryText = "阿里巴巴发票抬头";

        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(5)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        System.out.println("=================================");
        System.out.println("查询：" + queryText);
        System.out.println("结果数量：" + results.size());
        System.out.println("=================================");

        for (Document document : results) {
            System.out.println("score    = " + document.getScore());
            System.out.println("id       = " + document.getId());
            System.out.println("metadata = " + document.getMetadata());
//            System.out.println("content  = " + document.getText());
            System.out.println("---------------------------------");
        }
    }
}