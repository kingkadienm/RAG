package com.wangzs.rag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.enums.ParseStatusEnum;
import com.wangzs.rag.enums.VectorStatusEnum;
import com.wangzs.rag.model.dto.ChunkVO;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.service.DocumentService;
import com.wangzs.rag.service.FileParseService;
import com.wangzs.rag.service.FileStorageService;
import com.wangzs.rag.service.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.wangzs.rag.chunk.Chunk;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档管理 Controller
 */
@Tag(name = "文档管理", description = "文档的详情查询、分块查看、删除、重试解析等操作")
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class DocumentController {

    private final DocumentService documentService;
    private final FileParseService fileParseService;
    private final FileStorageService fileStorageService;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    /** 校验文档归属：返回文档实体，非归属用户抛出 NOT_FOUND */
    private Document verifyDocumentOwnership(Long docId) {
        Document doc = documentService.getById(docId);
        if (!AuthUtil.getLoginUserId().equals(doc.getCreatorId())) {
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return doc;
    }


    // ==================== 查询接口 ====================

    /**
     * 查询文档详情
     */
    @Operation(summary = "查询文档详情")
    @GetMapping("/{id}")
    public ApiResult<Document> getById(@PathVariable Long id) {
        Document doc = verifyDocumentOwnership(id);
        return ApiResult.success(doc);
    }

    /**
     * 查询文档分块内容（基于原始文件实时分块，不依赖数据库持久化）
     */
    @Operation(summary = "查询文档分块内容")
    @GetMapping("/{id}/chunks")
    public ApiResult<List<ChunkVO>> getChunks(@PathVariable Long id) {
        Document doc = verifyDocumentOwnership(id);

        if (doc.getParseStatus() != ParseStatusEnum.SUCCESS) {
            throw BizException.of(ErrorCode.DOCUMENT_PARSE_FAILED);
        }

        try (InputStream inputStream = fileStorageService.getInputStream(doc.getFilePath())) {
            List<Chunk> chunks = fileParseService.parseAndChunk(
                    inputStream, doc.getFileName(), doc.getFileType(), ""
            );
            List<ChunkVO> result = chunks.stream()
                    .map(c -> ChunkVO.builder()
                            .index(c.getIndex())
                            .content(c.getContent())
                            .length(c.getLength())
                            .build())
                    .collect(Collectors.toList());
            return ApiResult.success(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取文件分块失败: docId={}", id, e);
            throw BizException.of(ErrorCode.DOCUMENT_CHUNK_FAILED);
        }
    }

    /**
     * 查询文档解析进度（基于当前状态估算进度百分比）
     */
    @Operation(summary = "查询文档解析进度")
    @GetMapping("/{id}/parse-status")
    public ApiResult<Map<String, Object>> getParseStatus(@PathVariable Long id) {
        Document doc = verifyDocumentOwnership(id);

        String stage;
        int progress;
        String message;

        switch (doc.getParseStatus()) {
            case INIT -> {
                stage = "pending";
                progress = 0;
                message = "等待解析";
            }
            case PARSING -> {
                stage = "parsing";
                progress = 30;
                message = "正在解析文档内容...";
            }
            case SUCCESS -> {
                switch (doc.getVectorStatus()) {
                    case INIT -> {
                        stage = "vectorizing";
                        progress = 60;
                        message = "文档解析完成，正在向量化...";
                    }
                    case VECTORIZING -> {
                        stage = "vectorizing";
                        progress = 80;
                        message = "正在向量化入库...";
                    }
                    case SUCCESS -> {
                        stage = "completed";
                        progress = 100;
                        message = "处理完成";
                    }
                    case FAILED -> {
                        stage = "vector_failed";
                        progress = 60;
                        message = doc.getErrorMsg() != null ? doc.getErrorMsg() : "向量化失败";
                    }
                    default -> {
                        stage = "unknown";
                        progress = 50;
                        message = "状态异常";
                    }
                }
            }
            case FAILED -> {
                stage = "parse_failed";
                progress = 0;
                message = doc.getErrorMsg() != null ? doc.getErrorMsg() : "解析失败";
            }
            default -> {
                stage = "unknown";
                progress = 0;
                message = "未知状态";
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("stage", stage);
        data.put("progress", progress);
        data.put("message", message);
        data.put("parseStatus", doc.getParseStatus().getCode());
        data.put("vectorStatus", doc.getVectorStatus() != null ? doc.getVectorStatus().getCode() : 0);
        data.put("chunkCount", doc.getChunkCount());
        data.put("vectorCount", doc.getVectorCount());
        data.put("errorMsg", doc.getErrorMsg());

        return ApiResult.success(data);
    }

    /**
     * 查询文档解析进度（SSE 流式推送，状态变化时自动推送）
     */
    @Operation(summary = "查询文档解析进度（SSE 流式）")
    @GetMapping("/{id}/parse-status/stream")
    public SseEmitter streamParseStatus(@PathVariable Long id) {
        Document doc = verifyDocumentOwnership(id);

        SseEmitter emitter = new SseEmitter(300000L); // 5 分钟超时
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                Document currentDoc = documentService.getById(id);
                if (currentDoc == null) {
                    emitter.complete();
                    return;
                }

                // 检查状态是否变化
                String stage;
                int progress;
                String message;

                switch (currentDoc.getParseStatus()) {
                    case INIT -> {
                        stage = "pending";
                        progress = 0;
                        message = "等待解析";
                    }
                    case PARSING -> {
                        stage = "parsing";
                        progress = 30;
                        message = "正在解析文档内容...";
                    }
                    case SUCCESS -> {
                        switch (currentDoc.getVectorStatus()) {
                            case INIT -> {
                                stage = "vectorizing";
                                progress = 60;
                                message = "文档解析完成，正在向量化...";
                            }
                            case VECTORIZING -> {
                                stage = "vectorizing";
                                progress = 80;
                                message = "正在向量化入库...";
                            }
                            case SUCCESS -> {
                                stage = "completed";
                                progress = 100;
                                message = "处理完成";
                            }
                            case FAILED -> {
                                stage = "vector_failed";
                                progress = 60;
                                message = currentDoc.getErrorMsg() != null ? currentDoc.getErrorMsg() : "向量化失败";
                            }
                            default -> {
                                stage = "unknown";
                                progress = 50;
                                message = "状态异常";
                            }
                        }
                    }
                    case FAILED -> {
                        stage = "parse_failed";
                        progress = 0;
                        message = currentDoc.getErrorMsg() != null ? currentDoc.getErrorMsg() : "解析失败";
                    }
                    default -> {
                        stage = "unknown";
                        progress = 0;
                        message = "未知状态";
                    }
                }

                Map<String, Object> data = new HashMap<>();
                data.put("stage", stage);
                data.put("progress", progress);
                data.put("message", message);
                data.put("parseStatus", currentDoc.getParseStatus().getCode());
                data.put("vectorStatus", currentDoc.getVectorStatus() != null ? currentDoc.getVectorStatus().getCode() : 0);
                data.put("chunkCount", currentDoc.getChunkCount());
                data.put("vectorCount", currentDoc.getVectorCount());
                data.put("errorMsg", currentDoc.getErrorMsg());

                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(data));

                // 解析完成或失败时结束流
                if (stage.equals("completed") || stage.equals("parse_failed") || stage.equals("vector_failed")) {
                    emitter.complete();
                    scheduler.shutdown();
                }
            } catch (Exception e) {
                log.debug("SSE 流结束或发送失败: docId={}", id, e);
                emitter.complete();
                scheduler.shutdown();
            }
        }, 1000); // 每秒推送一次

        emitter.onCompletion(() -> scheduler.shutdown());
        emitter.onError((e) -> scheduler.shutdown());
        emitter.onTimeout(() -> scheduler.shutdown());

        return emitter;
    }

    // ==================== 操作接口 ====================

    /**
     * 手动重试解析（解析失败或向量化失败的文档）
     */
    @Operation(summary = "重试解析文档")
    @PostMapping("/{id}/retry")
    public ApiResult<Void> retryParse(@PathVariable Long id) {
        Document doc = verifyDocumentOwnership(id);

        // 重置状态为待解析
        documentService.updateParseStatus(id, ParseStatusEnum.INIT, 0, null);
        documentService.updateVectorStatus(id, VectorStatusEnum.INIT, 0, null);

        // 重新发送 MQ 消息
        documentService.sendParseMessage(doc);
        log.info("手动重试解析: docId={}, fileName={}", id, doc.getFileName());
        return ApiResult.success(null, "已重新发起解析");
    }

    /**
     * 删除文档
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        // 删除物理文件
        Document doc = verifyDocumentOwnership(id);
        if (doc.getFilePath() != null) {
            try {
                fileStorageService.delete(doc.getFilePath());
            } catch (Exception e) {
                log.warn("删除物理文件失败: {}", doc.getFilePath(), e);
            }
        }

        // 删除数据库记录
        documentService.delete(id);
        return ApiResult.success(null, "删除成功");
    }

}
