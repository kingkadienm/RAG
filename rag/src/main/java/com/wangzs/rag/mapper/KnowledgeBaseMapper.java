package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库 Mapper
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /**
     * 查询用户的知识库列表（未删除）
     */
    List<KnowledgeBase> selectByUserId(@Param("userId") Long userId);
}
