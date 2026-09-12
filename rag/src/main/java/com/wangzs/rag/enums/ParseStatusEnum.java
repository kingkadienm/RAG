package com.wangzs.rag.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @className: ParseStatusEnum
 * @description: 文档解析状态枚举
 * @author: wangzs
 * @date: 2026-09-11 22:08
 */
@Getter
@AllArgsConstructor
public enum ParseStatusEnum {

    INIT(0, "待解析"),
    PARSING(1, "解析中"),
    SUCCESS(2, "解析成功"),
    FAILED(3, "解析失败");

    /**
     * 标记存储到数据库的值（MyBatis-Plus 会自动将该枚举映射为 int）
     */
    @EnumValue
    @JsonValue
    private final int code;

    /**
     * 标记序列化为 JSON 时展示给前端的文本（如接口返回 "待解析" 或对应的 code）
     */
    private final String desc;

    /**
     * 根据 code 获取枚举对象
     */
    public static ParseStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
