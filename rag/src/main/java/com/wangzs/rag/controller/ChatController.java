package com.wangzs.rag.controller;

import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.model.dto.ChatRequest;
import com.wangzs.rag.model.dto.ChatResponse;
import com.wangzs.rag.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 对话 Controller
 */
@Tag(name = "RAG 对话", description = "基于知识库的问答对话")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
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
        Flux<String> flux = chatService.chatStream(request);

        // 包装为 SSE 格式：data: <content>\n\n，增加超时与错误事件
        return flux
                .timeout(java.time.Duration.ofSeconds(120))
                .map(content -> "data: " + content + "\n\n")
                .doOnComplete(() -> log.debug("SSE 流正常结束"))
                .doOnError(e -> {
                    log.error("SSE 流异常", e);
                })
                .onErrorResume(e -> {
                    String errorMsg = switch (e) {
                        case java.util.concurrent.TimeoutException t ->
                                "data: {\"error\":\"请求超时，请重试\"}\n\n";
                        default -> "data: {\"error\":\"流式回复异常: " + e.getMessage() + "\"}\n\n";
                    };
                    return Flux.just(errorMsg);
                })
                .doFinally(signal -> log.debug("SSE 流结束: signal={}", signal));
    }
}
