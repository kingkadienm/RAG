package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 查询用户的会话列表
     */
    List<ChatSession> selectByUserId(@Param("userId") Long userId);
}
