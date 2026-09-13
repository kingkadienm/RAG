package com.wangzs.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wangzs.rag.enums.ChatRoleEnum;
import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.model.entity.ChatMessage;
import com.wangzs.rag.model.entity.ChatSession;
import com.wangzs.rag.mapper.ChatMessageMapper;
import com.wangzs.rag.mapper.ChatSessionMapper;
import com.wangzs.rag.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话消息服务（消息保存、历史查询）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper; // Jackson JSON 序列化

    /**
     * 保存对话消息（用户提问 + 助手回复）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessages(String sessionId, String question, String answer,
                             List<RetrievalService.SearchResult> results) {
        LocalDateTime now = LocalDateTime.now();

        // 构建参考文档片段的 JSON（ref_chunks 字段）
        String refChunksJson = null;
        if (results != null && !results.isEmpty()) {
            try {
                ArrayNode refChunksArray = objectMapper.createArrayNode();
                for (RetrievalService.SearchResult r : results) {
                    ObjectNode chunkNode = objectMapper.createObjectNode();
                    chunkNode.put("docId", r.docId());
                    chunkNode.put("fileName", r.fileName());
                    // 截断过长的 content（最多 100 字符）
                    String truncatedContent = r.content();
                    if (truncatedContent != null && truncatedContent.length() > 100) {
                        truncatedContent = truncatedContent.substring(0, 100) + "...";
                    }
                    chunkNode.put("content", truncatedContent);
                    chunkNode.put("score", r.score() != null ? r.score() : 0.0);
                    refChunksArray.add(chunkNode);
                }
                refChunksJson = objectMapper.writeValueAsString(refChunksArray);
            } catch (Exception e) {
                log.warn("构建 ref_chunks JSON 失败，将保存为空数组", e);
                refChunksJson = "[]";
            }
        }

        // 粗略估算 token 数
        int questionTokens = estimateTokenCount(question);
        int answerTokens = estimateTokenCount(answer);
        int totalTokens = questionTokens + answerTokens;

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole(ChatRoleEnum.USER);
        userMsg.setContent(question);
        userMsg.setTokenCount(questionTokens);
        userMsg.setCreatedTime(now);
        chatMessageMapper.insert(userMsg);

        // 保存助手回复
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole(ChatRoleEnum.ASSISTANT);
        assistantMsg.setContent(answer);
        assistantMsg.setRefChunks(refChunksJson);
        assistantMsg.setTokenCount(answerTokens);
        assistantMsg.setCreatedTime(now);
        chatMessageMapper.insert(assistantMsg);

        // 更新会话消息计数（冗余字段，避免每次 COUNT）
        chatSessionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .setSql("message_count = message_count + 2")
                        .set(ChatSession::getUpdatedTime, now));

        // 缓存最近消息到 Redis
        String redisKey = "chat:ctx:" + sessionId;
        List<ChatMessage> ctx = new ArrayList<>();
        ctx.add(userMsg);
        ctx.add(assistantMsg);
        redisUtil.set(redisKey, ctx, 1800); // 30 分钟
    }

    /**
     * 查询会话的历史消息
     */
    public List<ChatMessage> listMessages(String sessionId) {
        // 先验证会话是否存在
        ChatSession session = chatSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getDeleted, DeletedEnum.NO)
        );
        if (session == null) {
            throw new com.wangzs.rag.common.exception.BizException(
                    com.wangzs.rag.common.exception.ErrorCode.CHAT_SESSION_NOT_FOUND);
        }

        return chatMessageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedTime)
        );
    }

    /**
     * 粗略估算 token 数
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        long chineseChars = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        long otherChars = text.length() - chineseChars;
        return (int) (chineseChars * 1.5 + otherChars * 0.25);
    }
}
