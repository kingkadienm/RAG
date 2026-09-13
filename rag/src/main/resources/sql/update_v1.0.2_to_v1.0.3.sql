-- =====================================================
-- 数据库迁移脚本：为 kb_upload_record 添加 deleted 字段
-- 版本：v1.0.2 → v1.0.3
-- 日期：2026-09-13
-- 说明：统一逻辑删除支持
-- =====================================================

-- ----------------------------------------------
-- MySQL：为 kb_upload_record 添加 deleted 字段
-- ----------------------------------------------
USE rag_v1;

-- 添加 deleted 字段
ALTER TABLE kb_upload_record
ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除'
    AFTER uploader_id;

-- 添加索引（加速 deleted 过滤）
ALTER TABLE kb_upload_record
ADD INDEX idx_deleted (deleted);


-- ----------------------------------------------
-- 验证修改
-- ----------------------------------------------
SELECT
    '迁移验证' AS check_type,
    COUNT(*) AS total_records,
    SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS active_records,
    SUM(CASE WHEN deleted = 1 THEN 1 ELSE 0 END) AS deleted_records
FROM kb_upload_record;

-- 查看表结构
SHOW COLUMNS FROM kb_upload_record;
