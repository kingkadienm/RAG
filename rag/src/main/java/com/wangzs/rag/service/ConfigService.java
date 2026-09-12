package com.wangzs.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangzs.rag.mapper.ConfigMapper;
import com.wangzs.rag.model.entity.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务（带本地缓存，定时从数据库刷新）
 *
 * <p>设计说明：
 * <ul>
 *   <li>启动时从数据库加载全部配置到内存 ConcurrentHashMap</li>
 *   <li>每 N 秒自动刷新一次缓存（默认 60s）</li>
 *   <li>提供类型安全的 getter，避免调用方重复写转换逻辑</li>
 *   <li>写操作（更新/保存）直接写 DB 并同步更新缓存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigMapper configMapper;

    /** 本地缓存：key → Config */
    private final Map<String, Config> cache = new ConcurrentHashMap<>();

    /** 缓存刷新间隔（秒），默认 60s */
    @Value("${rag.config.refresh-interval-seconds:60}")
    private long refreshIntervalSeconds;

    // ===================================================================
    //  生命周期：启动加载 + 定时刷新
    // ===================================================================

    /**
     * 应用启动完成后加载一次缓存
     */
    @EventListener
    public void onApplicationReady(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        refreshCache();
    }

    /**
     * 定时刷新缓存
     */
    @Scheduled(fixedRateString = "${rag.config.refresh-interval-seconds:60} * 1000")
    public void scheduledRefresh() {
        try {
            refreshCache();
        } catch (Exception e) {
            log.error("配置缓存刷新失败", e);
        }
    }

    /**
     * 从数据库全量刷新缓存
     */
    public synchronized void refreshCache() {
        List<Config> all = configMapper.selectList(
                new LambdaQueryWrapper<Config>().eq(Config::getDeleted, 0)
        );
        Map<String, Config> newCache = new ConcurrentHashMap<>();
        for (Config c : all) {
            newCache.put(c.getConfigKey(), c);
        }
        cache.clear();
        cache.putAll(newCache);
        log.info("配置缓存已刷新，共 {} 条记录", cache.size());
    }

    // ===================================================================
    //  读取（全部走缓存）
    // ===================================================================

    /**
     * 读取原始字符串值
     */
    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String defaultValue) {
        Config c = cache.get(key);
        return (c != null) ? c.getConfigValue() : defaultValue;
    }

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        String v = getString(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 值 '{}' 无法转为 int，使用默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        String v = getString(key);
        if (v == null) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 值 '{}' 无法转为 long，使用默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public double getDouble(String key, double defaultValue) {
        String v = getString(key);
        if (v == null) return defaultValue;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            log.warn("配置项 {} 值 '{}' 无法转为 double，使用默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key);
        if (v == null) return defaultValue;
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    // ===================================================================
    //  写操作（写 DB + 更新缓存）
    // ===================================================================

    /**
     * 更新配置值（写 DB 并同步更新缓存）
     */
    public Config updateValue(String key, String value) {
        Config config = configMapper.selectOne(
                new LambdaQueryWrapper<Config>()
                        .eq(Config::getConfigKey, key)
                        .eq(Config::getDeleted, 0)
        );
        if (config == null) {
            throw new IllegalArgumentException("配置项不存在: " + key);
        }
        config.setConfigValue(value);
        configMapper.updateById(config);
        cache.put(key, config);
        log.info("配置已更新: {} = {}", key, value);
        return config;
    }

    /**
     * 新增配置项
     */
    public Config create(Config config) {
        configMapper.insert(config);
        cache.put(config.getConfigKey(), config);
        log.info("配置已新增: {} = {}", config.getConfigKey(), config.getConfigValue());
        return config;
    }

    /**
     * 删除配置项（逻辑删除，系统配置不可删）
     */
    public void delete(Long id) {
        Config config = configMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("配置项不存在: id=" + id);
        }
        if (config.getIsSystem() == 1) {
            throw new IllegalArgumentException("系统配置项不可删除: " + config.getConfigKey());
        }
        config.setDeleted(1);
        configMapper.updateById(config);
        cache.remove(config.getConfigKey());
        log.info("配置已删除: id={}, key={}", id, config.getConfigKey());
    }

    // ===================================================================
    //  查询
    // ===================================================================

    public List<Config> listAll() {
        return configMapper.selectList(
                new LambdaQueryWrapper<Config>()
                        .eq(Config::getDeleted, 0)
                        .orderByAsc(Config::getId)
        );
    }

    public Config getById(Long id) {
        Config config = configMapper.selectById(id);
        if (config == null || config.getDeleted() == 1) {
            throw new IllegalArgumentException("配置项不存在: id=" + id);
        }
        return config;
    }
}
