-- ============================================================
-- 系统配置表 (sys_config)
-- 用于存储可动态修改的运行时配置
-- ============================================================

DROP TABLE IF EXISTS sys_config;

CREATE TABLE sys_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key  VARCHAR(128)  NOT NULL COMMENT '配置键（支持点号分隔，如 rag.chunk.size）',
    config_value VARCHAR(2000) NOT NULL COMMENT '配置值（字符串存储）',
    value_type  VARCHAR(32)   NOT NULL DEFAULT 'string' COMMENT '值类型：string/int/long/double/boolean/json',
    description VARCHAR(255)  NULL COMMENT '配置描述（中文说明）',
    is_system   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否系统配置（1=不可删除，0=可删除）',
    created_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除（1=已删除，0=未删除）',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================================
-- 初始化默认配置数据
-- ============================================================

INSERT INTO sys_config (config_key, config_value, value_type, description, is_system) VALUES
-- RAG 分块配置
('rag.chunk.size',              '500',    'int',    '文本分块大小（字符数）', 1),
('rag.chunk.overlap',           '50',     'int',    '分块重叠大小（字符数）', 1),

-- RAG 检索配置
('rag.retrieval.top-k',         '5',      'int',    '检索返回 Top-K 条数',    1),
('rag.retrieval.similarity-threshold', '0.7', 'double', '向量相似度阈值（0-1）', 1),

-- 文件上传配置
('rag.file.max-size',           '52428800', 'long',  '文件上传最大大小（字节，默认 50MB）', 1),
('rag.file.allowed-extensions', 'pdf,docx,txt,md,xlsx,pptx', 'string', '允许上传的文件扩展名（逗号分隔）', 1),

-- 重试配置
('rag.retry.max-attempts',      '3',      'int',    '文档解析失败最大重试次数',  1),
('rag.retry.interval-minutes',  '5',      'int',    '自动重试扫描间隔（分钟）',  1),

-- 聊天配置
('rag.chat.system-prompt',      '你是一个智能助手，请基于提供的参考文档回答用户问题。如果参考文档中没有相关信息，请诚实告知用户。', 'string', '系统提示词', 0),

-- 存储配置
('file.storage.type',           'local',  'string', '文件存储类型：local=本地, cloud=云存储（MinIO/S3）', 0);
