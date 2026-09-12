package com.wangzs.rag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.model.entity.ChatMessage;
import com.wangzs.rag.model.entity.ChatSession;
import com.wangzs.rag.service.ChatSessionService;
import com.wangzs.rag.service.ChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话会话管理 Controller
 */
@Tag(name = "对话会话管理", description = "会话列表查询、消息查询、会话删除")
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;

    /** 校验会话归属：返回会话实体，非归属用户抛出 NOT_FOUND */
    private ChatSession verifySessionOwnership(String sessionId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (!AuthUtil.getLoginUserId().equals(session.getUserId())) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        return session;
    }

    /**
     * 查询当前用户的会话列表
     *
     * @param kbId 可选，按知识库 ID 过滤
     */
    @Operation(summary = "查询会话列表")
    @GetMapping
    public ApiResult<List<ChatSession>> list(@RequestParam(name = "kbId", required = false) Long kbId) {
        Long userId = AuthUtil.getLoginUserId();
        List<ChatSession> sessions = chatSessionService.listSessions(userId, kbId);
        return ApiResult.success(sessions);
    }

    /**
     * 查询会话的历史消息
     */
    @Operation(summary = "查询会话消息")
    @GetMapping("/{sessionId}/messages")
    public ApiResult<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        verifySessionOwnership(sessionId);
        List<ChatMessage> messages = chatMessageService.listMessages(sessionId);
        return ApiResult.success(messages);
    }

    /**
     * 删除会话（逻辑删除）
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> delete(@PathVariable String sessionId) {
        verifySessionOwnership(sessionId);
        chatSessionService.deleteSession(AuthUtil.getLoginUserId(), sessionId);
        return ApiResult.success(null, "删除成功");
    }
}
