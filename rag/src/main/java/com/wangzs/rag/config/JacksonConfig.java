package com.wangzs.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置
 * 注册 Java 8 时间模块，解决 LocalDateTime 序列化问题
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule module = new JavaTimeModule();
        // LocalDateTime 序列化为字符串
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
        // LocalDateTime 反序列化
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(FORMATTER));
        mapper.registerModule(module);
        return mapper;
    }
}
