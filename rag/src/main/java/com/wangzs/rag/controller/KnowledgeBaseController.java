package com.wangzs.rag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.common.result.ApiResult;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.model.dto.KnowledgeBaseCreateDTO;
import com.wangzs.rag.model.entity.KnowledgeBase;
import com.wangzs.rag.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 Controller
 */
@Tag(name = "知识库管理", description = "知识库的 CRUD 操作")
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;

    @Operation(summary = "创建知识库")
    @PostMapping
    public ApiResult<KnowledgeBase> create(@RequestBody KnowledgeBaseCreateDTO dto) {
        Long userId = AuthUtil.getLoginUserId();
        KnowledgeBase kb = knowledgeBaseService.create(dto.getName(), dto.getDescription(), userId);
        return ApiResult.success(kb, "创建成功");
    }

    @Operation(summary = "查询知识库详情")
    @GetMapping("/{id}")
    public ApiResult<KnowledgeBase> getById(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (!AuthUtil.getLoginUserId().equals(kb.getCreatorId())) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return ApiResult.success(kb);
    }

    @Operation(summary = "分页查询知识库列表")
    @GetMapping("/page")
    public ApiResult<Page<KnowledgeBase>> page(
            @RequestParam(name = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        Long userId = AuthUtil.getLoginUserId();
        Page<KnowledgeBase> page = knowledgeBaseService.page(userId, pageNum, pageSize);
        return ApiResult.success(page);
    }

    @Operation(summary = "查询用户的所有知识库")
    @GetMapping("/list")
    public ApiResult<List<KnowledgeBase>> list() {
        Long userId = AuthUtil.getLoginUserId();
        List<KnowledgeBase> list = knowledgeBaseService.listByUserId(userId);
        return ApiResult.success(list);
    }

    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public ApiResult<KnowledgeBase> update(@PathVariable Long id,
                                           @RequestBody KnowledgeBaseCreateDTO dto) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (!AuthUtil.getLoginUserId().equals(kb.getCreatorId())) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        kb = knowledgeBaseService.update(id, dto.getName(), dto.getDescription());
        return ApiResult.success(kb, "更新成功");
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (!AuthUtil.getLoginUserId().equals(kb.getCreatorId())) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        knowledgeBaseService.delete(id);
        return ApiResult.success(null, "删除成功");
    }

    @Operation(summary = "查询知识库下的文档列表")
    @GetMapping("/documents/{kbId}")
    public ApiResult<Page<?>> listDocuments(@PathVariable Long kbId,
                                            @RequestParam(name = "pageNum", defaultValue = "1") int pageNum,
                                            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (!AuthUtil.getLoginUserId().equals(kb.getCreatorId())) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        Page<?> page = documentService.pageByKbId(kbId, pageNum, pageSize);
        return ApiResult.success(page);
    }
}
