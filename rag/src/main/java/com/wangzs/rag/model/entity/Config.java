package com.wangzs.rag.model.entity;

import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.enums.SystemConfigFlagEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 */
@Data
@TableName("sys_config")
public class Config {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置键（如 rag.chunk.size）
     */
    private String configKey;

    /**
     * 配置值（字符串存储）
     */
    private String configValue;

    /**
     * 值类型：string / int / long / double / boolean / json
     */
    private String valueType;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否系统配置（1=不可删除）
     */
    private SystemConfigFlagEnum isSystem;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private DeletedEnum deleted;
}
