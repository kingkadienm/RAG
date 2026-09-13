package com.wangzs.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.model.dto.ChatRequest;
import com.wangzs.rag.model.dto.ChatResponse;
import com.wangzs.rag.model.entity.ChatMessage;
import com.wangzs.rag.util.RedisUtil;
import com.wangzs.rag.model.entity.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RAG 对话服务（核心对话逻辑）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final RetrievalService retrievalService;
    private final RedisUtil redisUtil;
    private final ConfigService configService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ObjectMapper objectMapper;

    private String getSystemPrompt() {
        return configService.getString("rag.chat.system-prompt",
                "你是一个专业的知识库助手，你的任务是根据提供的参考文档片段回答用户问题。\n\n" +
                "回答要求：\n" +
                "1. **基于事实**：仅使用参考文档中的信息回答，不要编造内容\n" +
                "2. **自然流畅**：用口语化的中文表达，避免机械式复述\n" +
                "3. **结构化输出**：\n" +
                "   - 使用清晰的段落分隔\n" +
                "   - 可以用 Markdown 格式（**加粗**、列表、引用等）增强可读性\n" +
                "   - 必要时使用 `> 引用` 格式突出关键信息\n" +
                "4. **准确引用**：如果参考文档包含具体数据、条款或定义，请准确引用\n" +
                "5. **坦诚说明**：如果参考文档中没有相关信息，直接说\"根据提供的文档，我没有找到相关信息\"，不要猜测或扩展\n" +
                "6. **主动总结**：回答结尾可以简要总结要点（如果内容较长）");
    }

    private int getTopK() {
        return configService.getInt("rag.retrieval.top-k", 5);
    }

    /**
     * RAG 对话（非流式）
     */
    public ChatResponse chat(ChatRequest request) {
        // 1. 确保会话存在（绑定知识库 ID）
        Long kbId = request.getKbIds() != null && !request.getKbIds().isEmpty()
                ? request.getKbIds().get(0) : null;
        String sessionId = ensureSession(request.getSessionId(), kbId);

        // 2. 检索相关文档
        List<RetrievalService.SearchResult> searchResults = retrievalService.search(
                request.getQuestion(), getTopK(), request.getKbIds());

        // TODO【RAG 调试用】向量库空结果保护
        // 检索结果为空时，暂时不让大模型自动搜索回复
        // 此时直接返回提示，方便排查：向量未入库 / 切片未入库 / PGVector 表为空 / 相似度阈值过高等问题
        // 后续确认向量正常后，删除此 guard 即可恢复自动 RAG
        if (searchResults.isEmpty()) {
            log.warn("向量检索无结果，跳过 LLM 调用: kbId={}, question={}", kbId, request.getQuestion());
            ChatResponse response = new ChatResponse();
            response.setSessionId(sessionId);
            response.setAnswer("未在知识库中找到相关内容，请检查：\n1. 文档是否已上传并解析完成\n2. 向量化是否成功（查看文档的向量化状态）\n3. 相似度阈值是否过高（当前默认阈值：0.7）\n\n（调试提示：kbId=" + kbId + "，检索返回 0 条结果）");
            response.setReferences(List.of());
            return response;
        }

        // 3. 构建 Prompt
        String context = buildContext(searchResults);
        List<Message> messages = buildMessages(request.getQuestion(), context, sessionId);

        // 4. 调用 LLM
        String answer;
        try {
            ChatClient chatClient = chatClientBuilder.build();
            org.springframework.ai.chat.model.ChatResponse response = chatClient.prompt()
                    .messages(messages.toArray(new Message[0]))
                    .call()
                    .chatResponse();

            answer = response != null && response.getResult() != null
                    ? response.getResult().getOutput().getText()
                    : "抱歉，未能获取到回复。";
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            answer = "抱歉，处理过程中出现错误：" + e.getMessage();
        }

        // 5. 保存对话记录（包含参考文档片段）
        chatMessageService.saveMessages(sessionId, request.getQuestion(), answer, searchResults);

        // 6. 构建响应
        return buildResponse(sessionId, answer, searchResults);
    }

    /**
     * RAG 对话（流式 SSE）
     */
    public Flux<String> chatStream(ChatRequest request) {
        Long kbId = request.getKbIds() != null && !request.getKbIds().isEmpty()
                ? request.getKbIds().get(0) : null;
        String sessionId = ensureSession(request.getSessionId(), kbId);

        List<RetrievalService.SearchResult> searchResults = retrievalService.search(
                request.getQuestion(), getTopK(), request.getKbIds());

        // TODO【RAG 调试用】向量库空结果保护
        if (searchResults.isEmpty()) {
            log.warn("向量检索无结果，跳过 LLM 流式调用: kbId={}, question={}", kbId, request.getQuestion());
            // 返回错误信息 + sessionId标记
            String errorMsg = "未在知识库中找到相关内容，请检查：\n1. 文档是否已上传并解析完成\n2. 向量化是否成功\n3. 相似度阈值是否过高（当前默认阈值：0.7）\n\n（调试提示：kbId=" + kbId + "，检索返回 0 条结果）";
            return Flux.just(errorMsg + "\n\n[SESSION_ID:" + sessionId + "]\n\n[DONE]");
        }

        String context = buildContext(searchResults);
        List<Message> messages = buildMessages(request.getQuestion(), context, sessionId);

        ChatClient chatClient = chatClientBuilder.build();

        // 使用 AtomicReference 收集完整回复
        java.util.concurrent.atomic.AtomicReference<String> fullAnswer = new java.util.concurrent.atomic.AtomicReference<>("");

        // 构建参考文档数据并发送
        String refChunksData = buildRefChunksJson(searchResults);

        return Flux.concat(
                // 1. 首先发送参考文档数据（如果有）
                refChunksData != null ? Flux.just("[REF_CHUNKS:" + refChunksData + "]") : Flux.empty(),
                // 2. 发送 LLM 流式内容
                chatClient.prompt()
                        .messages(messages.toArray(new Message[0]))
                        .stream()
                        .content()
                        .map(chunk -> {
                            // 收集完整内容
                            fullAnswer.updateAndGet(current -> current + chunk);
                            // 返回文本块
                            return chunk;
                        })
                        .doOnComplete(() -> {
                            // 3. 流结束后保存对话记录
                            try {
                                chatMessageService.saveMessages(sessionId, request.getQuestion(),
                                        fullAnswer.get(), searchResults);
                                log.info("流式对话消息已保存: sessionId={}, answerLength={}", sessionId, fullAnswer.get().length());
                            } catch (Exception e) {
                                log.error("流式对话消息保存失败: sessionId={}", sessionId, e);
                            }
                        }),
                // 4. 发送 sessionId 标记
                Flux.just("\n\n[SESSION_ID:" + sessionId + "]"),
                // 5. 发送 DONE 事件
                Flux.just("[DONE]")
        );
    }

    /**
     * 确保会话存在（供Controller调用以获取sessionId）
     */
    public String ensureSession(String sessionId, Long kbId) {
        if (sessionId == null || sessionId.isBlank()) {
            // 新会话：从 Sa-Token 获取用户 ID
            Object loginId = cn.dev33.satoken.stp.StpUtil.getLoginId();
            Long userId = loginId instanceof Long ? (Long) loginId : Long.parseLong(loginId.toString());

            ChatSession session = new ChatSession();
            session.setSessionId(java.util.UUID.randomUUID().toString());
            session.setUserId(userId);
            session.setKbId(kbId);
            session.setTitle("新对话");
            session.setMessageCount(0);
            session.setDeleted(0);
            chatSessionService.createSessionDirect(session);
            return session.getSessionId();
        }

        // 验证会话是否存在
        if (!chatSessionService.verifySessionOwnership(sessionId)) {
            return ensureSession(null, kbId); // 创建新会话
        }

        return sessionId;
    }

    /**
     * 构建检索上下文
     */
    private String buildContext(List<RetrievalService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "（未找到相关参考文档）";
        }

        StringBuilder sb = new StringBuilder("参考文档片段：\n");
        for (int i = 0; i < results.size(); i++) {
            RetrievalService.SearchResult r = results.get(i);
            sb.append(String.format("[%d] %s (相似度: %.2f)\n%s\n\n",
                    i + 1, r.fileName(), r.score() != null ? r.score() : 0.0, r.content()));
        }
        return sb.toString();
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessages(String question, String context, String sessionId) {
        List<Message> messages = new ArrayList<>();

        // 系统提示词（role=3）
        String ragPrompt = getSystemPrompt() + "\n\n参考文档：\n" + context;
        messages.add(new SystemMessage(ragPrompt));

        // 从 Redis 获取历史上下文
        String redisKey = "chat:ctx:" + sessionId;
        List<ChatMessage> history = redisUtil.getObject(redisKey, List.class);
        if (history != null) {
            for (ChatMessage msg : history) {
                // role=1 用户, role=2 助手, role=3 系统
                if (msg.getRole() == 1) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if (msg.getRole() == 2) {
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                }
                // role=3 系统消息跳过（已由当前系统提示词覆盖）
            }
        }

        // 当前用户问题
        messages.add(new UserMessage(question));

        return messages;
    }

    /**
     * 构建响应
     */
    private ChatResponse buildResponse(String sessionId, String answer,
                                       List<RetrievalService.SearchResult> results) {
        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setAnswer(answer);

        if (results != null && !results.isEmpty()) {
            List<ChatResponse.ReferenceDoc> refs = results.stream()
                    .map(r -> {
                        ChatResponse.ReferenceDoc ref = new ChatResponse.ReferenceDoc();
                        ref.setDocId(r.docId());
                        ref.setFileName(r.fileName());
                        ref.setContent(r.content());
                        ref.setScore(r.score());
                        return ref;
                    })
                    .toList();
            response.setReferences(refs);
        }

        return response;
    }

    /**
     * 构建参考文档 JSON 数据（用于流式响应）
     */
    private String buildRefChunksJson(List<RetrievalService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        // 转换为 ChatResponse.ReferenceDoc 列表
        List<ChatResponse.ReferenceDoc> refs = results.stream()
                .map(r -> {
                    ChatResponse.ReferenceDoc ref = new ChatResponse.ReferenceDoc();
                    ref.setDocId(r.docId());
                    ref.setFileName(r.fileName());
                    ref.setContent(r.content());
                    ref.setScore(r.score());
                    return ref;
                })
                .toList();

        try {
            return objectMapper.writeValueAsString(refs);
        } catch (Exception e) {
            log.error("序列化参考文档失败", e);
            return null;
        }
    }
}
