-- ========================================
-- RAG 系统 MySQL 建表脚本
-- Database: rag_v1  (MySQL 8.0+)
-- 说明：不建物理外键，级联删除/关联由应用层处理
-- ========================================

-- ------------------------
-- 对话消息表（先删子表）
-- ------------------------
DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
                              id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                              session_id   VARCHAR(64)  NOT NULL COMMENT '业务会话 ID（对应 chat_session.session_id）',
                              role         TINYINT      NOT NULL COMMENT '角色：1-用户 2-助手 3-系统',
                              content      TEXT         NOT NULL COMMENT '消息内容',
                              ref_chunks   JSON         NULL COMMENT '回答引用的文档片段：[{"docId":1,"chunkIndex":3,"score":0.87}]',
                              token_count  INT          NULL COMMENT '该条消息 token 数（可选统计用）',
                              created_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              PRIMARY KEY (id),
                              INDEX idx_session_time (session_id, created_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话消息表';

-- ------------------------
-- 对话会话表
-- ------------------------
DROP TABLE IF EXISTS chat_session;
CREATE TABLE chat_session (
                              id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                              session_id    VARCHAR(64)  NOT NULL COMMENT '业务会话 ID（服务端生成 UUID。勿使用 Sa-Token token，token 会过期轮换导致历史失联）',
                              user_id       BIGINT       NOT NULL COMMENT '用户 ID',
                              kb_id         BIGINT       NULL COMMENT '会话绑定的知识库 ID',
                              title         VARCHAR(128) NULL COMMENT '会话标题（默认取首条提问截断）',
                              message_count INT          NOT NULL DEFAULT 0 COMMENT '消息条数（冗余计数，避免每次 COUNT）',
                              created_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              updated_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
                              PRIMARY KEY (id),
                              UNIQUE KEY uk_session_id (session_id),
                              INDEX idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话会话表';

-- ------------------------
-- 文件上传记录表
-- ------------------------
DROP TABLE IF EXISTS kb_upload_record;
CREATE TABLE kb_upload_record (
                                  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                                  kb_id             BIGINT       NOT NULL COMMENT '上传目标知识库 ID',
                                  doc_id            BIGINT       NULL COMMENT '校验通过并建档后回填的文档 ID',
                                  original_filename VARCHAR(256) NOT NULL COMMENT '原始文件名',
                                  stored_filename   VARCHAR(256) NOT NULL COMMENT '存储文件名（UUID+时间戳+扩展名）',
                                  file_size         BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
                                  file_md5          CHAR(32)     NOT NULL COMMENT '文件 MD5 哈希（定长 32 位）',
                                  mime_type         VARCHAR(128) NULL COMMENT 'Tika 探测出的真实 MIME 类型',
                                  storage_type      VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '存储方式：local / cloud（排查文件位置用）',
                                  check_status      TINYINT      NOT NULL DEFAULT 1 COMMENT '校验状态：1-通过 2-拒绝',
                                  reject_reason     VARCHAR(512) NULL COMMENT '拒绝原因',
                                  uploader_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '上传人 ID',
                                  deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
                                  created_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_md5_kb (file_md5, kb_id),
                                  INDEX idx_kb_id (kb_id),
                                  INDEX idx_doc_id (doc_id),
                                  INDEX idx_uploader_id (uploader_id),
                                  INDEX idx_deleted (deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '文件上传记录表';

-- ------------------------
-- 文档表
-- ------------------------
DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
                             id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                             kb_id         BIGINT        NOT NULL COMMENT '所属知识库 ID',
                             title         VARCHAR(256)  NULL COMMENT '文档标题',
                             file_name     VARCHAR(256)  NOT NULL COMMENT '原始文件名',
                             file_type     VARCHAR(32)   NULL COMMENT '文件类型（扩展名）',
                             file_size     BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
                             file_path     VARCHAR(512)  NULL COMMENT '文件存储路径（S3 key 或本地相对路径）',
                             file_md5      CHAR(32)      NOT NULL COMMENT '文件 MD5（冗余自上传记录，用于幂等与级联清理）',
                             parse_status  TINYINT       NOT NULL DEFAULT 0 COMMENT '解析状态：0-待解析 1-解析中 2-解析成功 3-解析失败',
                             vector_status TINYINT       NOT NULL DEFAULT 0 COMMENT '向量化状态：0-待向量化 1-向量化中 2-向量化完成 3-向量化失败',
                             chunk_count   INT           NOT NULL DEFAULT 0 COMMENT '分块数量',
                             vector_count  INT           NOT NULL DEFAULT 0 COMMENT '已入库向量数量',
                             error_msg     VARCHAR(1024) NULL COMMENT '失败原因（解析或向量化阶段）',
                             version       INT           NOT NULL DEFAULT 1 COMMENT '文档版本，重新上传/重新解析时 +1（用于缓存失效与向量重建）',
                             creator_id    BIGINT        NOT NULL DEFAULT 0 COMMENT '上传人 ID',
                             created_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             updated_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
                             PRIMARY KEY (id),
                             INDEX idx_kb_id (kb_id),
                             INDEX idx_status (parse_status, vector_status),
                             INDEX idx_file_md5 (file_md5)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '文档表';

-- ------------------------
-- 知识库表
-- ------------------------
DROP TABLE IF EXISTS kb_knowledge_base;
CREATE TABLE kb_knowledge_base (
                                   id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                                   name         VARCHAR(128) NOT NULL COMMENT '知识库名称',
                                   description  TEXT         NULL COMMENT '知识库描述',
                                   creator_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人 ID',
                                   status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用 2-禁用（删除统一走 deleted，避免双重软删除）',
                                   created_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   updated_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
                                   PRIMARY KEY (id),
                                   INDEX idx_creator_id (creator_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '知识库表';

-- ------------------------
-- 用户表
-- ------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
                          id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
                          username     VARCHAR(64)  NOT NULL COMMENT '用户名',
                          password     VARCHAR(256) NOT NULL COMMENT '密码（BCrypt 密文）',
                          nickname     VARCHAR(64)  NULL COMMENT '昵称',
                          role         TINYINT      NOT NULL DEFAULT 2 COMMENT '角色：1-管理员 2-普通用户',
                          status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常 2-禁用',
                          created_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          updated_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统用户表';

-- ------------------------
-- 初始化数据
-- ------------------------
-- 密码请替换为实际 BCrypt 密文（如 123456 对应的 hash）
INSERT INTO sys_user (username, password, nickname, role)
VALUES ('admin', '$2b$10$ClHeN8wmkX7NlsvT.CyYCe95C6DtznP0rRQzktbmB6AKbvUmpZTlW', '管理员', 1);

-- ========================================
-- 文档分块表
-- ========================================
-- 执行时机：向量化阶段成功后 INSERT；重试时复用；文档删除时级联清理
-- 来源：schema_document_chunk.sql
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

