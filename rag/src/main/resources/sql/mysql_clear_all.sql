-- =====================================================
-- MySQL 完全清除脚本（保留 sys_user）
-- 用途：清空所有业务数据，保留用户表
-- 数据库：rag_v1
-- 警告：此脚本会删除所有数据，请谨慎使用！
-- =====================================================

-- 切换到目标数据库
USE rag_v1;

-- ----------------------------------------------
-- 前置准备：开启事务
-- ----------------------------------------------
START TRANSACTION;

-- ----------------------------------------------
-- 1. 禁用外键检查（加速删除）
-- ----------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;


-- ----------------------------------------------
-- 2. 确认数据量（删除前记录日志）
-- ----------------------------------------------
SELECT
    '删除前统计' AS action,
    (SELECT COUNT(*) FROM chat_message) AS chat_message_count,
    (SELECT COUNT(*) FROM chat_session) AS chat_session_count,
    (SELECT COUNT(*) FROM kb_upload_record) AS upload_record_count,
    (SELECT COUNT(*) FROM kb_document) AS document_count,
    (SELECT COUNT(*) FROM kb_knowledge_base) AS knowledge_base_count,
    (SELECT COUNT(*) FROM sys_user) AS user_count;


-- ----------------------------------------------
-- 3. 清空业务数据表
-- 按照外键依赖顺序删除（子表 → 父表）
-- ----------------------------------------------

-- 3.1 清空聊天消息表（子表）
TRUNCATE TABLE chat_message;

-- 3.2 清空对话会话表（子表）
TRUNCATE TABLE chat_session;

-- 3.3 清空文件上传记录表（子表）
TRUNCATE TABLE kb_upload_record;

-- 3.4 清空文档表（子表，依赖 kb_knowledge_base）
TRUNCATE TABLE kb_document;

-- 3.5 清空知识库表（父表）
TRUNCATE TABLE kb_knowledge_base;


-- ----------------------------------------------
-- 4. 重置自增 ID（可选，恢复到初始状态）
-- ----------------------------------------------
ALTER TABLE chat_message AUTO_INCREMENT = 1;
ALTER TABLE chat_session AUTO_INCREMENT = 1;
ALTER TABLE kb_upload_record AUTO_INCREMENT = 1;
ALTER TABLE kb_document AUTO_INCREMENT = 1;
ALTER TABLE kb_knowledge_base AUTO_INCREMENT = 1;


-- ----------------------------------------------
-- 5. 重新开启外键检查
-- ----------------------------------------------
SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------------------------
-- 6. 确认删除结果
-- ----------------------------------------------
SELECT
    '删除后验证' AS action,
    (SELECT COUNT(*) FROM chat_message) AS chat_message_count,
    (SELECT COUNT(*) FROM chat_session) AS chat_session_count,
    (SELECT COUNT(*) FROM kb_upload_record) AS upload_record_count,
    (SELECT COUNT(*) FROM kb_document) AS document_count,
    (SELECT COUNT(*) FROM kb_knowledge_base) AS knowledge_base_count,
    (SELECT COUNT(*) FROM sys_user) AS user_count;


-- ----------------------------------------------
-- 7. 提交事务
-- ----------------------------------------------
COMMIT;

-- ----------------------------------------------
-- 如果出现问题，执行回滚
-- ROLLBACK;
-- ----------------------------------------------


-- =====================================================
-- 验证脚本：确认数据已清空
-- =====================================================

-- 查询所有业务表的数据量
SELECT
    '验证结果' AS check_type,
    (SELECT COUNT(*) FROM kb_knowledge_base) AS knowledge_base,
    (SELECT COUNT(*) FROM kb_document) AS document,
    (SELECT COUNT(*) FROM chat_session) AS chat_session,
    (SELECT COUNT(*) FROM chat_message) AS chat_message,
    (SELECT COUNT(*) FROM kb_upload_record) AS upload_record,
    (SELECT COUNT(*) FROM sys_user) AS sys_user;

-- 查询所有表结构
SHOW TABLES;

-- 验证 sys_user 数据未被删除
SELECT * FROM sys_user;


-- =====================================================
-- 使用说明
-- =====================================================
-- 1. 执行前确认：
--    - 已备份数据库
--    - 确定要删除所有业务数据
--    - sys_user 表数据将保留
--
-- 2. 执行步骤：
--    - 复制本脚本到数据库客户端
--    - 先执行 SELECT 查询确认数据量
--    - 确认无误后执行删除操作
--
-- 3. 回滚方法：
--    - 如果删除过程中出现问题
--    - 执行 ROLLBACK; 回滚事务
--
-- 4. 验证结果：
--    - 执行验证脚本
--    - 确认所有业务表为空
--    - 确认 sys_user 表数据完整
