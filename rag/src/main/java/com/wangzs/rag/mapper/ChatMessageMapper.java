package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话的历史消息
     */
    List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);
}
