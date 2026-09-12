package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.Config;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统配置 Mapper
 */
@Mapper
public interface ConfigMapper extends BaseMapper<Config> {

    /**
     * 批量查询（按 key 前缀过滤，用于刷新缓存）
     */
    List<Config> selectAllActive();

    /**
     * 按 key 查询
     */
    Config selectByKey(@Param("configKey") String configKey);
}
