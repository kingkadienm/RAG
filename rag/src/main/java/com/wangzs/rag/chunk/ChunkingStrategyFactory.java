package com.wangzs.rag.chunk;

import com.wangzs.rag.chunk.model.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 切片策略工厂
 * <p>
 * 根据文件类型选择合适的 ChunkingStrategy。
 * <p>
 * 职责：
 * <pre>
 * fileType → Strategy
 * </pre>
 */
@Slf4j
@Component
public class ChunkingStrategyFactory {

    private final List<ChunkingStrategy> strategies;

    public ChunkingStrategyFactory(List<ChunkingStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * 根据文件类型获取匹配的策略
     *
     * @param fileType 文件扩展名
     * @return 匹配的策略，找不到则返回 DefaultChunkingStrategy
     */
    public ChunkingStrategy getStrategy(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            log.warn("fileType 为空，使用默认策略");
            return new DefaultChunkingStrategy();
        }

        String normalized = fileType.toLowerCase().trim();

        Optional<ChunkingStrategy> matched = strategies.stream()
                .filter(s -> s.supports(normalized))
                .findFirst();

        ChunkingStrategy strategy = matched.orElse(new DefaultChunkingStrategy());

        log.debug("fileType={} → strategy={}", normalized, strategy.getClass().getSimpleName());
        return strategy;
    }
}
