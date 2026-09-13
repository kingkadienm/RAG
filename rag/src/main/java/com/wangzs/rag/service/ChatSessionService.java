package com.wangzs.rag.service;

import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.model.entity.ChatSession;
import com.wangzs.rag.mapper.ChatSessionMapper;
import com.wangzs.rag.mapper.ChatMessageMapper;
import com.wangzs.rag.util.RedisUtil;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话会话服务（会话管理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RedisUtil redisUtil;

    /**
     * 创建会话
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatSession createSession(Long userId, Long kbId, String title) {
        ChatSession session = new ChatSession();
        session.setSessionId(com.wangzs.rag.util.IdUtil.generateId() + "");
        session.setUserId(userId);
        session.setKbId(kbId);
        session.setTitle(title != null && !title.isEmpty() ? title : "新对话");
        session.setMessageCount(0);
        session.setDeleted(DeletedEnum.NO);
        session.setCreatedTime(LocalDateTime.now());
        session.setUpdatedTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        log.info("创建会话成功: sessionId={}, userId={}", session.getSessionId(), userId);
        return session;
    }

    /**
     * 查询用户的会话列表
     *
     * @param userId 用户 ID
     * @param kbId   可选，按知识库 ID 过滤（null 则不过滤）
     */
    public List<ChatSession> listSessions(Long userId, Long kbId) {
        return chatSessionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(kbId != null, ChatSession::getKbId, kbId)
                        .eq(ChatSession::getDeleted, DeletedEnum.NO)
                        .orderByDesc(ChatSession::getUpdatedTime)
        );
    }

    /**
     * 删除会话（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getDeleted, DeletedEnum.NO)
        );
        if (session == null) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (!userId.equals(session.getUserId())) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        session.setDeleted(DeletedEnum.YES);
        session.setUpdatedTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        // 删除消息（物理删除，跟随会话生命周期）
        chatMessageMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.wangzs.rag.model.entity.ChatMessage>()
                        .eq(com.wangzs.rag.model.entity.ChatMessage::getSessionId, sessionId)
        );
        // 清除 Redis 缓存
        String redisKey = "chat:ctx:" + sessionId;
        redisUtil.delete(redisKey);
        log.info("删除会话成功: sessionId={}, userId={}", sessionId, userId);
    }

    /**
     * 直接创建会话（供 ChatService 内部使用，跳过权限校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void createSessionDirect(ChatSession session) {
        chatSessionMapper.insert(session);
    }

    /**
     * 根据 sessionId 获取会话（不校验用户归属，仅存在性校验）
     */
    public ChatSession getSession(String sessionId) {
        return chatSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getDeleted, DeletedEnum.NO)
        );
    }

    /**
     * 校验会话归属（非归属用户抛出 NOT_FOUND）
     */
    public ChatSession verifySessionOwnership(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session == null || !AuthUtil.getLoginUserId().equals(session.getUserId())) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        return session;
    }
}
