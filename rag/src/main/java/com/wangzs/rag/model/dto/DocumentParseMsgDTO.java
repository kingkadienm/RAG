package com.wangzs.rag.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @className: DocumentParseMsgDTO
 * @description: 文档解析 MQ 消息 DTO
 * @author: wangzs
 * @date: 2026-09-11 20:37
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseMsgDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long docId;
    private Long kbId;
    private String filePath;
    private String fileName;
    private String fileType;
    private String mimeType;
    private String fileMd5;
    private String storageType;
    /**
     * 文档版本号，用于消费端幂等与过时消息拦截
     */
    private Integer version;
}
