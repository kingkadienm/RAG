package com.wangzs.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * SSE 相关配置
 *
 * <p>声明共享的 {@link ScheduledExecutorService}，用于 SSE 解析状态轮询，
 * 避免每次请求创建新的调度器导致线程泄漏。
 * Spring 容器销毁时会自动调用 {@code shutdown()} 清理线程池。
 */
@Configuration
public class SseConfig {

    /**
     * 应用级共享调度线程池，供 {@code DocumentServiceImpl.streamParseStatus()} 使用。
     *
     * @param poolSize 线程池大小，从配置项 {@code rag.sse.status-pool-size} 读取，默认 4
     * @return 有界调度线程池
     */
    @Bean(name = "sseStatusExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService sseStatusExecutor(
            @Value("${rag.sse.status-pool-size:4}") int poolSize) {
        return Executors.newScheduledThreadPool(poolSize);
    }
}
