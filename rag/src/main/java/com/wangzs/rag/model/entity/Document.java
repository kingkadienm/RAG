package com.wangzs.rag.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档实体类
 */
@Data
@TableName("kb_document")
public class Document {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库 ID
     */
    private Long kbId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型（扩展名）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件存储路径（S3 key 或本地相对路径）
     */
    private String filePath;

    /**
     * 文件 MD5（冗余自上传记录，用于幂等与级联清理）
     */
    private String fileMd5;

//    /**
//     * 解析状态：0-待解析 1-解析中 2-解析成功 3-解析失败
//     */
//    private Integer parseStatus;
//    /**
//     * 向量化状态：0-待向量化 1-向量化中 2-向量化完成 3-向量化失败
//     */
//    private Integer vectorStatus;
    /**
     * 解析状态：0-待解析 1-解析中 2-解析成功 3-解析失败
     */
    private ParseStatusEnum parseStatus;

    /**
     * 向量化状态：0-待向量化 1-向量化中 2-向量化完成 3-向量化失败
     */
    private VectorStatusEnum vectorStatus;


    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 已入库向量数量
     */
    private Integer vectorCount;

    /**
     * 失败原因（解析或向量化阶段）
     */
    private String errorMsg;

    /**
     * 文档版本，重新上传/重新解析时 +1（用于缓存失效与向量重建）
     */
    private Integer version;

    /**
     * 上传人 ID
     */
    private Long creatorId;

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
    private Integer deleted;
}
