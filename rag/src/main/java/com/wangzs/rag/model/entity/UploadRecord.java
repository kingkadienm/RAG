package com.wangzs.rag.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件上传记录实体类
 */
@Data
@TableName("kb_upload_record")
public class UploadRecord {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 上传目标知识库 ID
     */
    private Long kbId;

    /**
     * 校验通过并建档后回填的文档 ID
     */
    private Long docId;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 存储文件名（UUID+时间戳+扩展名）
     */
    private String storedFilename;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件 MD5 哈希（定长 32 位）
     */
    private String fileMd5;

    /**
     * MIME 类型
     */
    private String mimeType;

    /**
     * 存储方式：local / cloud（排查文件位置用）
     */
    private String storageType;

    /**
     * 校验状态：1-通过 2-拒绝
     */
    private Integer checkStatus;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 上传人 ID
     */
    private Long uploaderId;

    /**
     * 上传时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 逻辑删除：0-未删除 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
