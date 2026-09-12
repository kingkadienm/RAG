package com.wangzs.rag.common.enums;

import lombok.Getter;

/**
 * 文件存储类型枚举
 * 对应 kb_upload_record.storage_type
 */
@Getter
public enum FileStorageTypeEnum {

    LOCAL("local", "本地磁盘"),
    CLOUD("cloud", "S3 / OSS 云存储");

    private final String code;
    private final String desc;

    FileStorageTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static FileStorageTypeEnum getByCode(String code) {
        for (FileStorageTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return LOCAL; // 默认本地
    }
}
