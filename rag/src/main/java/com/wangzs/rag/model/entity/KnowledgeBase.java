package com.wangzs.rag.model.entity;

import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.enums.KnowledgeBaseStatusEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 */
@Data
@TableName("kb_knowledge_base")
public class KnowledgeBase {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 创建人 ID
     */
    private Long creatorId;

    /**
     * 状态：1-启用 2-禁用（删除统一走 deleted，避免双重软删除）
     */
    private KnowledgeBaseStatusEnum status;

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
