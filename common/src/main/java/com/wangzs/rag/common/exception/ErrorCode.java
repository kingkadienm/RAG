package com.wangzs.rag.common.exception;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    // ==================== 通用错误 ====================
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统异常"),
    PARAM_ERROR(400, "参数错误"),

    // ==================== 用户相关 ====================
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ALREADY_EXISTS(1003, "用户已存在"),
    USER_DISABLED(1004, "用户已被禁用"),

    // ==================== 知识库相关 ====================
    KNOWLEDGE_BASE_NOT_FOUND(2001, "知识库不存在"),
    KNOWLEDGE_BASE_NAME_EXISTS(2002, "知识库名称已存在"),
    KNOWLEDGE_BASE_DELETE_FORBIDDEN(2003, "知识库无法删除"),

    // ==================== 文档相关 ====================
    DOCUMENT_NOT_FOUND(3001, "文档不存在"),
    DOCUMENT_ALREADY_PARSING(3002, "文档正在解析中"),
    DOCUMENT_PARSE_FAILED(3003, "文档解析失败"),
    DOCUMENT_CHUNK_FAILED(3004, "文档分块失败"),
    DOCUMENT_CONTENT_EXTRACTION_FAILED(3005, "文档内容提取失败"),


    // ==================== 文件上传相关 ====================
    FILE_EMPTY(4001, "上传文件为空"),
    FILE_TOO_LARGE(4002, "文件大小超出限制"),
    FILE_TYPE_NOT_ALLOWED(4003, "不支持的文件类型"),
    FILE_NAME_INVALID(4004, "文件名不合法"),
    FILE_MD5_DUPLICATE(4005, "文件已存在（MD5 重复）"),
    FILE_UPLOAD_FAILED(4006, "文件上传失败"),
    FILE_DELETE_FAILED(4007, "文件删除失败"),
    FILE_CHECK_FAILED(4008, "文件校验失败"),

    // ==================== 解析相关 ====================
    PARSE_FAILED(5001, "文件解析失败"),
    PARSE_UNSUPPORTED_TYPE(5002, "不支持的文件格式"),

    // ==================== 向量相关 ====================
    EMBEDDING_FAILED(6001, "向量化失败"),
    VECTOR_STORE_ERROR(6002, "向量存储错误"),

    // ==================== 对话相关 ====================
    CHAT_SESSION_NOT_FOUND(7001, "会话不存在"),
    CHAT_MESSAGE_SEND_FAILED(7002, "消息发送失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
