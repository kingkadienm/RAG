package com.wangzs.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.model.entity.Config;
import com.wangzs.rag.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置管理 Controller
 */
@Tag(name = "系统配置", description = "配置项的查询、新增、修改、删除")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
public class ConfigController {

    private final ConfigService configService;

    /**
     * 查询所有配置
     */
    @Operation(summary = "查询所有配置")
    @GetMapping
    public ApiResult<List<Config>> list() {
        List<Config> list = configService.listAll();
        return ApiResult.success(list);
    }

    /**
     * 查询单个配置
     */
    @Operation(summary = "按 key 查询配置")
    @GetMapping("/{key}")
    public ApiResult<Config> getByKey(@PathVariable String key) {
        Config config = configService.listAll().stream()
                .filter(c -> c.getConfigKey().equals(key))
                .findFirst()
                .orElse(null);
        if (config == null) {
            throw BizException.of(400, "配置项不存在: " + key);
        }
        return ApiResult.success(config);
    }

    /**
     * 修改配置值
     */
    @Operation(summary = "修改配置值")
    @PutMapping("/{key}")
    public ApiResult<Config> update(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("configValue");
        if (value == null) {
            throw BizException.of(400, "configValue 不能为空");
        }
        Config updated = configService.updateValue(key, value);
        return ApiResult.success(updated, "更新成功");
    }

    /**
     * 新增配置项
     */
    @Operation(summary = "新增配置项（仅非系统配置）")
    @PostMapping
    public ApiResult<Config> create(@RequestBody Config config) {
        Config created = configService.create(config);
        return ApiResult.success(created, "创建成功");
    }

    /**
     * 删除配置项（仅非系统配置）
     */
    @Operation(summary = "删除配置项（仅非系统配置）")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return ApiResult.success(null, "删除成功");
    }

    /**
     * 手动刷新配置缓存
     */
    @Operation(summary = "手动刷新配置缓存")
    @PostMapping("/refresh")
    public ApiResult<Void> refresh() {
        configService.refreshCache();
        return ApiResult.success(null, "缓存已刷新");
    }
}
