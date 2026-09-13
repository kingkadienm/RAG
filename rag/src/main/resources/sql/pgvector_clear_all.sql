-- =====================================================
-- PGVector 完全清除脚本
-- 用途：清空向量库中所有向量数据
-- 数据库：vector_db
-- Schema：rag
-- 警告：此脚本会删除所有向量，请谨慎使用！
-- =====================================================

-- 切换到目标 schema
SET search_path TO rag;


-- ----------------------------------------------
-- 前置准备：确认数据量（删除前记录日志）
-- ----------------------------------------------
SELECT
    '删除前统计' AS action,
    COUNT(*) AS total_vectors,
    COUNT(DISTINCT (metadata->>'doc_id')::bigint) AS unique_docs,
    COUNT(DISTINCT (metadata->>'kb_id')::bigint) AS unique_kbs,
    MIN(created_at) AS earliest_vector,
    MAX(created_at) AS latest_vector
FROM rag.vector_store;


-- ----------------------------------------------
-- 方案一：清空整个表（推荐，速度最快）
-- ----------------------------------------------
-- TRUNCATE TABLE rag.vector_store;


-- ----------------------------------------------
-- 方案二：按条件删除（保留特定知识库）
-- ----------------------------------------------
-- DELETE FROM rag.vector_store
-- WHERE (metadata->>'kb_id')::bigint NOT IN (<保留的kb_id>);


-- ----------------------------------------------
-- 方案三：级联删除（按知识库删除）
-- ----------------------------------------------
-- -- 删除知识库 1 的所有向量
-- DELETE FROM rag.vector_store
-- WHERE (metadata->>'kb_id')::bigint = 1;
--
-- -- 删除知识库 2 的所有向量
-- DELETE FROM rag.vector_store
-- WHERE (metadata->>'kb_id')::bigint = 2;
--
-- -- 批量删除多个知识库
-- DELETE FROM rag.vector_store
-- WHERE (metadata->>'kb_id')::bigint IN (1, 2, 3);


-- ----------------------------------------------
-- 方案四：按文档删除
-- ----------------------------------------------
-- -- 删除单个文档的所有向量
-- DELETE FROM rag.vector_store
-- WHERE (metadata->>'doc_id')::bigint = <doc_id>;
--
-- -- 批量删除多个文档
-- DELETE FROM rag.vector_store
-- WHERE (metadata->>'doc_id')::bigint IN (1, 2, 3);


-- ----------------------------------------------
-- 方案五：按版本删除（清理旧版本）
-- ----------------------------------------------
-- -- 仅保留最新版本，删除所有旧版本
-- DELETE FROM rag.vector_store vs
-- WHERE (vs.metadata->>'doc_id')::bigint = ANY(ARRAY[1,2,3])
--   AND (vs.metadata->>'version')::int < (
--       SELECT MAX((vs2.metadata->>'version')::int)
--       FROM rag.vector_store vs2
--       WHERE (vs2.metadata->>'doc_id')::bigint = (vs.metadata->>'doc_id')::bigint
--   );


-- ----------------------------------------------
-- 方案六：删除孤立向量（MySQL 中已不存在的文档）
-- 注意：需要在 MySQL 中先查询确认
-- ----------------------------------------------
-- -- 第一步：在 MySQL 中查询孤立的 doc_id
-- -- SELECT DISTINCT (metadata->>'doc_id')::bigint AS doc_id FROM rag.vector_store vs
-- -- WHERE NOT EXISTS (SELECT 1 FROM kb_document d WHERE d.id = (vs.metadata->>'doc_id')::bigint);
--
-- -- 第二步：在 PGVector 中删除这些孤立向量（替换 <doc_id_list>）
-- -- DELETE FROM rag.vector_store vs
-- -- WHERE (vs.metadata->>'doc_id')::bigint IN (<doc_id_list>);



-- ----------------------------------------------
-- 删除后验证
-- ----------------------------------------------
SELECT
    '删除后验证' AS action,
    COUNT(*) AS remaining_vectors,
    COUNT(DISTINCT (metadata->>'doc_id')::bigint) AS remaining_docs,
    COUNT(DISTINCT (metadata->>'kb_id')::bigint) AS remaining_kbs
FROM rag.vector_store;


-- ----------------------------------------------
-- 可选：重建索引（删除大量数据后建议执行）
-- ----------------------------------------------
-- REINDEX TABLE rag.vector_store;
-- VACUUM ANALYZE rag.vector_store;


-- =====================================================
-- 使用说明
-- =====================================================
--
-- 【清空整个向量库】
-- 1. 执行"删除前统计"确认数据量
-- 2. 取消"方案一"的注释
-- 3. 执行 TRUNCATE TABLE rag.vector_store;
-- 4. 执行"删除后验证"确认清空成功
--
-- 【按知识库删除】
-- 1. 查询知识库 ID：SELECT * FROM kb_knowledge_base;
-- 2. 取消"方案三"的注释
-- 3. 替换 <kb_id> 为实际 ID
-- 4. 执行删除
--
-- 【按文档删除】
-- 1. 查询文档 ID：SELECT id, title FROM kb_document WHERE kb_id = <kb_id>;
-- 2. 取消"方案四"的注释
-- 3. 替换 <doc_id> 为实际 ID
-- 4. 执行删除
--
-- 【清理孤立向量】
-- 1. 先查询孤立向量（在 MySQL 中执行）：
--    SELECT COUNT(*) FROM kb_document d
--    WHERE NOT EXISTS (SELECT 1 FROM kb_knowledge_base kb WHERE kb.id = d.kb_id);
-- 2. 确认后取消"方案六"注释
-- 3. 执行删除
--
-- 【注意事项】
-- - TRUNCATE 是最快的清空方式，但不可回滚
-- - DELETE 可以回滚，但速度较慢
-- - 删除大量数据后建议执行 REINDEX/VACUUM
-- - 建议配合 mysql_clear_all.sql 同步清理 MySQL 数据
