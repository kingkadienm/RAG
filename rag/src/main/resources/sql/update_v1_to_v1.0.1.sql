-- ============================================================
-- RAG 数据库升级脚本
-- 版本: v1.0.0 -> v1.0.1
-- 日期: 2026-09-11
-- 说明: 修复上传流程中 file_path 先为 NULL 后回填的问题
-- ============================================================

-- 将 kb_document.file_path 从 NOT NULL 改为 NULL
-- 原因: 上传流程先 INSERT 文档记录（此时尚未上传文件，无存储路径），
--       文件上传成功后再 UPDATE 回填 file_path，因此创建阶段允许为空
ALTER TABLE kb_document
    MODIFY COLUMN file_path VARCHAR(512) NULL COMMENT '文件存储路径（S3 key 或本地相对路径）';
