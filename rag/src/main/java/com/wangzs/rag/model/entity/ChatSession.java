package com.wangzs.rag.model.entity;

import com.wangzs.rag.enums.DeletedEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体类
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务会话 ID（服务端生成 UUID。勿使用 Sa-Token token，token 会过期轮换导致历史失联）
     */
    private String sessionId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 会话绑定的知识库 ID
     */
    private Long kbId;

    /**
     * 会话标题（默认取首条提问截断）
     */
    private String title;

    /**
     * 消息条数（冗余计数，避免每次 COUNT）
     */
    private Integer messageCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除：0-未删除 1-已删除
     */
    @TableLogic
    private DeletedEnum deleted;
}
