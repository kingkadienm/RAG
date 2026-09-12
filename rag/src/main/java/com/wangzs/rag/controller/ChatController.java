package com.wangzs.rag.controller;

import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.model.dto.ChatRequest;
import com.wangzs.rag.model.dto.ChatResponse;
import com.wangzs.rag.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话 Controller
 */
@Tag(name = "RAG 对话", description = "基于知识库的问答对话")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "RAG 对话（非流式）")
    @PostMapping("/completions")
    public ApiResult<ChatResponse> completions(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(request);
        return ApiResult.success(response);
    }

    @Operation(summary = "RAG 对话（流式 SSE）")
    @PostMapping(value = "/completions/stream", produces = "text/event-stream")
    public Flux<String> completionsStream(@Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }
}
