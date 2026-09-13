-- =====================================================
-- 文档分块表 DDL（PostgreSQL + pgvector）
-- Database: vector_db
-- Schema: rag
-- 对应 MySQL 废弃表：kb_document_chunk
-- =====================================================

-- 切换 schema
SET search_path TO rag;

-- 确保 pgvector extension 存在
CREATE EXTENSION IF NOT EXISTS vector;

-- 删除旧表（重建用）
DROP TABLE IF EXISTS document_chunk;

-- 创建表
CREATE TABLE document_chunk (
    -- 主键
    id              BIGSERIAL      NOT NULL PRIMARY KEY,

    -- 业务关系
    kb_id           BIGINT         NOT NULL,
    doc_id          BIGINT         NOT NULL,

    -- Chunk 信息
    chunk_index     INT            NOT NULL,
    chunk_total     INT            NOT NULL DEFAULT 0,

    -- Chunk 内容
    content         TEXT           NOT NULL,
    content_hash    VARCHAR(64),

    -- 向量
    embedding       VECTOR(1536)   NOT NULL,
    embedding_model VARCHAR(100)   NOT NULL DEFAULT 'text-embedding-v2',

    -- 扩展元数据
    metadata        JSONB,

    -- 创建时间
    created_time    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- HNSW 索引（余弦相似度）
CREATE INDEX idx_chunk_embedding_hnsw
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 业务过滤索引
CREATE INDEX idx_chunk_kb_id    ON document_chunk (kb_id);
CREATE INDEX idx_chunk_doc_id   ON document_chunk (doc_id);
CREATE INDEX idx_chunk_kb_doc   ON document_chunk (kb_id, doc_id);

-- 业务幂等唯一索引（同一个文档+分块序号唯一）
CREATE UNIQUE INDEX ux_doc_chunk_index
    ON document_chunk (doc_id, chunk_index);

-- =====================================================
-- 验证
-- =====================================================

-- 确认表结构
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'rag'
  AND table_name = 'document_chunk'
ORDER BY ordinal_position;

-- 确认索引
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'rag'
  AND tablename = 'document_chunk';
