-- ========================================
-- 文档分块表 DDL
-- Database: rag_v1 (MySQL 8.0+)
-- 说明：与 kb_document 表配合，持久化文档分块内容，
--       实现分块成功后的恢复点，支持向量化失败时的阶段级重试。
-- 执行时机：向量化阶段成功后 INSERT；重试时复用；文档删除时级联清理。
-- ========================================

DROP TABLE IF EXISTS kb_document_chunk;
CREATE TABLE kb_document_chunk (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    doc_id          BIGINT       NOT NULL COMMENT '所属文档 ID（关联 kb_document.id）',
    kb_id           BIGINT       NOT NULL COMMENT '所属知识库 ID（冗余，方便按 kb 清理）',
    chunk_index     INT          NOT NULL COMMENT '分块序号（从 0 开始）',
    title           VARCHAR(256) NULL COMMENT '文档标题',
    section_path    VARCHAR(512) NULL COMMENT '章节路径（如 "集团开票信息汇总 > 阿里巴巴"）',
    content         TEXT         NOT NULL COMMENT '分块文本内容',
    token_count     INT          NOT NULL DEFAULT 0 COMMENT 'Token 数量',
    char_length     INT          NOT NULL DEFAULT 0 COMMENT '字符长度',
    embedding_text  TEXT         NULL COMMENT '用于生成向量的文本（= title + sectionPath + content）',
    version         INT          NOT NULL DEFAULT 1 COMMENT '文档版本号（与 kb_document.version 同步）',
    created_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_doc_id (doc_id),
    INDEX idx_doc_version (doc_id, version),
    INDEX idx_kb_id (kb_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '文档分块表';
