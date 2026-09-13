-- ============================================================
-- RAG 数据库升级脚本
-- 版本: v1.0.1 -> v1.0.2
-- 日期: 2026-09-12
-- 说明: 添加文档重试次数字段 retry_count
-- ============================================================

-- 为 kb_document 表添加 retry_count 字段
-- 原因: Document 实体类中已定义该字段，用于自动重试定时任务递增重试次数
ALTER TABLE kb_document
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数（自动重试定时任务递增）' AFTER version;

-- 添加索引以优化重试相关查询（兼容 MySQL 5.7+）
-- 注意：使用复合索引而非部分索引，确保兼容性
CREATE INDEX idx_retry_count ON kb_document (retry_count, parse_status, vector_status);
