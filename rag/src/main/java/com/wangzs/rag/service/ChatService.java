package com.wangzs.rag.service;

import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.model.dto.ChatRequest;
import com.wangzs.rag.model.dto.ChatResponse;
import com.wangzs.rag.model.entity.ChatMessage;
import com.wangzs.rag.model.entity.ChatSession;
import com.wangzs.rag.mapper.ChatMessageMapper;
import com.wangzs.rag.mapper.ChatSessionMapper;
import com.wangzs.rag.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 对话服务
 *
 * <p>systemPrompt、topK 从 ConfigService（数据库）读取，支持动态调整
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient.Builder chatClientBuilder;
    private final RetrievalService retrievalService;
    private final RedisUtil redisUtil;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ConfigService configService;

    /** 惰性加载 */
    private volatile String systemPrompt;
    private volatile int topK;
    private volatile boolean configLoaded = false;

    private void ensureConfigLoaded() {
        if (!configLoaded) {
            synchronized (this) {
                if (!configLoaded) {
                    systemPrompt = configService.getString("rag.chat.system-prompt",
                            "你是一个智能助手，请基于提供的参考文档回答用户问题。如果参考文档中没有相关信息，请诚实告知用户。");
                    topK = configService.getInt("rag.retrieval.top-k", 5);
                    configLoaded = true;
                    log.info("聊天配置已加载: topK={}", topK);
                }
            }
        }
    }

    /**
     * RAG 对话（非流式）
     */
    public ChatResponse chat(ChatRequest request) {
        ensureConfigLoaded();

        // 1. 确保会话存在（绑定知识库 ID）
        Long kbId = request.getKbIds() != null && !request.getKbIds().isEmpty()
                ? request.getKbIds().get(0) : null;
        String sessionId = ensureSession(request.getSessionId(), kbId);

        // 2. 检索相关文档
        List<RetrievalService.SearchResult> searchResults = retrievalService.search(
                request.getQuestion(), topK, request.getKbIds());

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
                    .chatResponse()
                    ;

            answer = response != null && response.getResult() != null
                    ? response.getResult().getOutput().getText()
                    : "抱歉，未能获取到回复。";
        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            answer = "抱歉，处理过程中出现错误：" + e.getMessage();
        }

        // 5. 保存对话记录（包含参考文档片段）
        saveMessages(sessionId, request.getQuestion(), answer, searchResults);

        // 6. 构建响应
        return buildResponse(sessionId, answer, searchResults);
    }

    /**
     * RAG 对话（流式 SSE）
     */
    public Flux<String> chatStream(ChatRequest request) {
        ensureConfigLoaded();

        Long kbId = request.getKbIds() != null && !request.getKbIds().isEmpty()
                ? request.getKbIds().get(0) : null;
        String sessionId = ensureSession(request.getSessionId(), kbId);

        List<RetrievalService.SearchResult> searchResults = retrievalService.search(
                request.getQuestion(), topK, request.getKbIds());

        // TODO【RAG 调试用】向量库空结果保护
        if (searchResults.isEmpty()) {
            log.warn("向量检索无结果，跳过 LLM 流式调用: kbId={}, question={}", kbId, request.getQuestion());
            return Flux.just("未在知识库中找到相关内容，请检查：\n1. 文档是否已上传并解析完成\n2. 向量化是否成功\n3. 相似度阈值是否过高（当前默认阈值：0.7）\n\n（调试提示：kbId=" + kbId + "，检索返回 0 条结果）");
        }

        String context = buildContext(searchResults);
        List<Message> messages = buildMessages(request.getQuestion(), context, sessionId);

        ChatClient chatClient = chatClientBuilder.build();

        return chatClient.prompt()
                .messages(messages.toArray(new Message[0]))
                .stream()
                .chatResponse()
                .concatMap(response -> {
                    String content = response.getResult().getOutput().getText();
                    return Flux.just(content);
                })
                .doOnComplete(() -> {
                    // 流结束后保存对话记录（简化处理）
                    String fullAnswer = "流式回复（请查看前端完整内容）";
                    saveMessages(sessionId, request.getQuestion(), fullAnswer, searchResults);
                })
                .doOnError(e -> log.error("流式对话出错", e));
    }

    /**
     * 确保会话存在
     */
    private String ensureSession(String sessionId, Long kbId) {
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
            chatSessionMapper.insert(session);
            return session.getSessionId();
        }

        // 验证会话是否存在
        ChatSession session = chatSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
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
        String ragPrompt = systemPrompt + "\n\n参考文档：\n" + context;
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
     * 保存对话消息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessages(String sessionId, String question, String answer,
                             List<RetrievalService.SearchResult> results) {
        LocalDateTime now = LocalDateTime.now();

        // 构建参考文档片段的 JSON（ref_chunks 字段）
        String refChunksJson = null;
        if (results != null && !results.isEmpty()) {
            refChunksJson = "[" + results.stream()
                    .map(r -> String.format(
                            "{\"docId\":%d,\"fileName\":\"%s\",\"content\":\"%s\",\"score\":%.4f}",
                            r.docId(), r.fileName(),
                            r.content().length() > 100 ? r.content().substring(0, 100) + "..." : r.content(),
                            r.score() != null ? r.score() : 0.0))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("[]") + "]";
        }

        // 粗略估算 token 数（中文按 1.5 字符/token，英文按 4 字符/token）
        int questionTokens = estimateTokenCount(question);
        int answerTokens = estimateTokenCount(answer);
        int totalTokens = questionTokens + answerTokens;

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole(1); // 用户
        userMsg.setContent(question);
        userMsg.setTokenCount(questionTokens);
        userMsg.setCreatedTime(now);
        chatMessageMapper.insert(userMsg);

        // 保存助手回复
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole(2); // 助手
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
     * 粗略估算 token 数
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 简单估算：中文字符按 1.5 token，其他按 0.25 token（4字符=1 token）
        long chineseChars = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        long otherChars = text.length() - chineseChars;
        return (int) (chineseChars * 1.5 + otherChars * 0.25);
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

    // ==================== 会话管理 ====================

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
                        .eq(ChatSession::getDeleted, 0)
                        .orderByDesc(ChatSession::getUpdatedTime)
        );
    }

    /**
     * 查询会话的历史消息
     */
    public List<ChatMessage> listMessages(String sessionId) {
        // 先验证会话是否存在
        ChatSession session = chatSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }

        return chatMessageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedTime)
        );
    }

    /**
     * 删除会话（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getDeleted, 0)
        );
        if (session == null) {
            throw BizException.of(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }

        session.setDeleted(1);
        chatSessionMapper.updateById(session);
        log.info("删除会话: sessionId={}", sessionId);
    }
}
