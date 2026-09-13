package com.wangzs.rag.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.model.vector.VectorDocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL document_chunk 表数据访问层
 * <p>
 * 使用 pgVectorJdbcTemplate 直接 JDBC 操作，不经过 MyBatis。
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VectorDocumentChunkRepository {

    private final JdbcTemplate pgVectorJdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
            INSERT INTO rag.document_chunk
                (kb_id, doc_id, chunk_index, chunk_total, content, content_hash, metadata, created_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String FIND_BY_DOC_ID_SQL = """
            SELECT id, kb_id, doc_id, chunk_index, chunk_total,
                   content, content_hash, embedding, embedding_model, metadata, created_time
            FROM rag.document_chunk
            WHERE doc_id = ?
            ORDER BY chunk_index ASC
            """;

    private static final String DELETE_BY_DOC_ID_SQL = """
            DELETE FROM rag.document_chunk WHERE doc_id = ?
            """;

    private final RowMapper<VectorDocumentChunk> rowMapper = new RowMapper<>() {
        @Override
        public VectorDocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            VectorDocumentChunk chunk = new VectorDocumentChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setKbId(rs.getLong("kb_id"));
            chunk.setDocId(rs.getLong("doc_id"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setChunkTotal(rs.getInt("chunk_total"));
            chunk.setContent(rs.getString("content"));
            chunk.setContentHash(rs.getString("content_hash"));
            chunk.setEmbeddingModel(rs.getString("embedding_model"));
            chunk.setCreatedTime(rs.getTimestamp("created_time").toLocalDateTime());

            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                try {
                    chunk.setMetadata(objectMapper.readValue(metadataJson, Map.class));
                } catch (JsonProcessingException e) {
                    log.warn("解析 metadata JSON 失败", e);
                    chunk.setMetadata(new HashMap<>());
                }
            }

            return chunk;
        }
    };

    // ============================================================
    //  写操作
    // ============================================================

    /**
     * 批量保存文档分块（先删旧分块，再插入新分块）
     */
    public void saveBatch(Long docId, Long kbId, Integer version, int chunkTotal,
                          List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        // 幂等：先删除旧分块
        deleteByDocId(docId);

        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batchArgs = new ArrayList<>(chunks.size());

        for (Chunk chunk : chunks) {
            String content = chunk.getContent();
            Map<String, Object> metadata = buildMetadata(chunk, version);

            batchArgs.add(new Object[]{
                    kbId,
                    docId,
                    chunk.getIndex(),
                    chunkTotal,
                    content,
                    computeContentHash(content),
                    toJson(metadata),
                    now
            });
        }

        pgVectorJdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
        log.info("持久化分块完成: docId={}, version={}, count={}", docId, version, chunks.size());
    }

    // ============================================================
    //  读操作
    // ============================================================

    /**
     * 查询文档的所有分块
     */
    public List<VectorDocumentChunk> findByDocId(Long docId) {
        List<VectorDocumentChunk> result = pgVectorJdbcTemplate.query(
                FIND_BY_DOC_ID_SQL, rowMapper, docId);
        return result != null ? result : List.of();
    }

    /**
     * 查询文档的分块数量
     */
    public int countByDocId(Long docId) {
        Integer count = pgVectorJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag.document_chunk WHERE doc_id = ?",
                Integer.class, docId);
        return count != null ? count : 0;
    }

    // ============================================================
    //  删除操作
    // ============================================================

    /**
     * 删除文档的所有分块
     */
    public int deleteByDocId(Long docId) {
        int deleted = pgVectorJdbcTemplate.update(DELETE_BY_DOC_ID_SQL, docId);
        log.info("删除文档分块: docId={}, deleted={}", docId, deleted);
        return deleted;
    }

    // ============================================================
    //  辅助方法
    // ============================================================

    /**
     * 构建 metadata JSON（仅存辅助字段，核心过滤字段独立为列）
     */
    private Map<String, Object> buildMetadata(Chunk chunk, Integer version) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", chunk.getTitle());
        metadata.put("section_path", chunk.getSectionPath());
        metadata.put("token_count", chunk.getTokenCount());
        metadata.put("char_length", chunk.getLength());
        metadata.put("embedding_text", chunk.getEmbeddingText());
        metadata.put("version", version);
        return metadata;
    }

    /**
     * 计算内容 MD5
     */
    private static String computeContentHash(String content) {
        if (content == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * 对象转 JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize metadata to JSON", e);
        }
    }
}
