package com.wangzs.rag.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息实体类
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 角色：1-用户 2-助手 3-系统
     */
    private Integer role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 回答引用的文档片段：[{"docId":1,"chunkIndex":3,"score":0.87}]
     */
    private String refChunks;

    /**
     * 该条消息 token 数（可选统计用）
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
