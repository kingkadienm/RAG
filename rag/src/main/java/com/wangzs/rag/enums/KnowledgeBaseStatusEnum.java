package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 知识库状态枚举
 */
@Getter
@AllArgsConstructor
public enum KnowledgeBaseStatusEnum {

    ENABLED(1, "启用"),
    DISABLED(2, "禁用"),
    DELETED(3, "已删除");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static KnowledgeBaseStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
