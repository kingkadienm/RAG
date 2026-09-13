package com.wangzs.rag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.wangzs.rag.common.result.ApiResult;
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
 * 对话会话管理 Controller（纯编排层，无业务逻辑）
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

    /**
     * 查询当前用户的会话列表
     */
    @Operation(summary = "查询会话列表")
    @GetMapping
    public ApiResult<List<ChatSession>> list(@RequestParam(name = "kbId", required = false) Long kbId) {
        Long userId = com.wangzs.rag.common.util.AuthUtil.getLoginUserId();
        List<ChatSession> sessions = chatSessionService.listSessions(userId, kbId);
        return ApiResult.success(sessions);
    }

    /**
     * 查询会话的历史消息
     */
    @Operation(summary = "查询会话消息")
    @GetMapping("/{sessionId}/messages")
    public ApiResult<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        chatSessionService.verifySessionOwnership(sessionId);
        List<ChatMessage> messages = chatMessageService.listMessages(sessionId);
        return ApiResult.success(messages);
    }

    /**
     * 删除会话（软删除）
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> delete(@PathVariable String sessionId) {
        chatSessionService.verifySessionOwnership(sessionId);
        chatSessionService.deleteSession(
                com.wangzs.rag.common.util.AuthUtil.getLoginUserId(), sessionId);
        return ApiResult.success(null, "删除成功");
    }
}
