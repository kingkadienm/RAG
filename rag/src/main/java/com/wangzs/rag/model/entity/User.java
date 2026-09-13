package com.wangzs.rag.model.entity;

import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.enums.UserRoleEnum;
import com.wangzs.rag.enums.UserStatusEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体类
 */
@Data
@TableName("sys_user")
public class User {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（加密）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 角色：1-管理员 2-普通用户
     */
    private UserRoleEnum role;

    /**
     * 状态：1-正常 2-禁用
     */
    private UserStatusEnum status;

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
