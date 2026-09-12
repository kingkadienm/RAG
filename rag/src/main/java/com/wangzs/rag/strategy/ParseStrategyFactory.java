package com.wangzs.rag.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 解析策略工厂
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseStrategyFactory {

    private final List<FileParseStrategy> strategies;

    private Map<Class<? extends FileParseStrategy>, FileParseStrategy> strategyMap;

    /**
     * 根据文件扩展名获取对应的解析策略
     */
    public FileParseStrategy getStrategy(String fileType) {
        if (strategyMap == null) {
            synchronized (this) {
                if (strategyMap == null) {
                    strategyMap = new java.util.HashMap<>();
                    for (FileParseStrategy strategy : strategies) {
                        strategyMap.put(strategy.getClass(), strategy);
                    }
                }
            }
        }

        return Optional.ofNullable(strategyMap.get(TikaParseStrategy.class))
                .or(() -> strategies.stream().filter(s -> s.supports(fileType)).findFirst())
                .orElseGet(() -> {
                    log.warn("未找到匹配的解析策略，使用默认 Tika 策略: fileType={}", fileType);
                    return strategyMap.get(TikaParseStrategy.class);
                });
    }
}
