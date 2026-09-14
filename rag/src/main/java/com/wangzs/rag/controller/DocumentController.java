package com.wangzs.rag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.enums.UserRoleEnum;
import com.wangzs.rag.mapper.UserMapper;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.model.entity.User;
import com.wangzs.rag.service.DocumentService;
import com.wangzs.rag.chunk.Chunk;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 文档管理 Controller（纯编排层，无业务逻辑）
 */
@Tag(name = "文档管理", description = "文档的详情查询、分块查看、删除、重试解析等操作")
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
@Slf4j
@SaCheckLogin
public class DocumentController {

    private final DocumentService documentService;
    private final UserMapper userMapper;

    // ==================== 查询接口 ====================

    /**
     * 查询文档详情
     */
    @Operation(summary = "查询文档详情")
    @GetMapping("/{id}")
    public ApiResult<Document> getById(@PathVariable Long id) {
        Document doc = documentService.getById(id);
        verifyDocumentAccess(doc);
        return ApiResult.success(doc);
    }

    /**
     * 查询文档分块内容（从 PostgreSQL 读取持久化分块）
     */
    @Operation(summary = "查询文档分块内容")
    @GetMapping("/{id}/chunks")
    public ApiResult<List<Chunk>> getChunks(@PathVariable Long id) {
        Document doc = documentService.getById(id);
        verifyDocumentAccess(doc);
        List<Chunk> chunks = documentService.getChunks(id);
        return ApiResult.success(chunks);
    }

    /**
     * 查询文档解析进度
     */
    @Operation(summary = "查询文档解析进度")
    @GetMapping("/{id}/parse-status")
    public ApiResult<Map<String, Object>> getParseStatus(@PathVariable Long id) {
        Map<String, Object> progress = documentService.getParseProgress(id);
        Document doc = documentService.getById(id);
        progress.put("parseStatus", doc.getParseStatus().getCode());
        progress.put("vectorStatus", doc.getVectorStatus() != null ? doc.getVectorStatus().getCode() : 0);
        progress.put("chunkCount", doc.getChunkCount());
        progress.put("vectorCount", doc.getVectorCount());
        progress.put("errorMsg", doc.getErrorMsg());
        return ApiResult.success(progress);
    }

    /**
     * 查询文档解析进度（SSE 流式推送，状态变化时自动推送）
     */
    @Operation(summary = "查询文档解析进度（SSE 流式）")
    @GetMapping("/{id}/parse-status/stream")
    public SseEmitter streamParseStatus(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(300000L); // 5 分钟超时
        documentService.streamParseStatus(id, emitter);
        return emitter;
    }

    // ==================== 操作接口 ====================

    /**
     * 手动重试解析（解析失败或向量化失败的文档）
     */
    @Operation(summary = "重试解析文档")
    @PostMapping("/{id}/retry")
    public ApiResult<Void> retryParse(@PathVariable Long id) {
        documentService.retryParse(id);
        return ApiResult.success(null, "已重新发起解析");
    }

    /**
     * 删除文档
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        documentService.deleteWithFile(id);
        return ApiResult.success(null, "删除成功");
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验当前用户是否有权限访问该文档（Admin 可访问所有，普通用户只能访问自己的）
     */
    private void verifyDocumentAccess(Document doc) {
        Long currentUserId = AuthUtil.getLoginUserId();
        User currentUser = userMapper.selectById(currentUserId);
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRoleEnum.ADMIN;
        if (!isAdmin && !currentUserId.equals(doc.getCreatorId())) {
            throw BizException.of(ErrorCode.DOCUMENT_NOT_FOUND);
        }
    }
}
