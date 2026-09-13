package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 逻辑删除标志枚举（所有实体的通用 deleted 字段）
 */
@Getter
@AllArgsConstructor
public enum DeletedEnum {

    NO(0, "未删除"),
    YES(1, "已删除");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;

    public static DeletedEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
