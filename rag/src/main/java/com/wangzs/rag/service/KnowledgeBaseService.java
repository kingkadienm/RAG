package com.wangzs.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wangzs.rag.common.exception.BizException;
import com.wangzs.rag.common.exception.ErrorCode;
import com.wangzs.rag.enums.DeletedEnum;
import com.wangzs.rag.enums.KnowledgeBaseStatusEnum;
import com.wangzs.rag.common.util.AuthUtil;
import com.wangzs.rag.enums.UserRoleEnum;
import com.wangzs.rag.model.entity.Document;
import com.wangzs.rag.model.entity.KnowledgeBase;
import com.wangzs.rag.model.entity.UploadRecord;
import com.wangzs.rag.model.entity.User;
import com.wangzs.rag.mapper.DocumentMapper;
import com.wangzs.rag.mapper.KnowledgeBaseMapper;
import com.wangzs.rag.mapper.UploadRecordMapper;
import com.wangzs.rag.mapper.UserMapper;
import com.wangzs.rag.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;
    private final UploadRecordMapper uploadRecordMapper;
    private final FileStorageService fileStorageService;
    private final VectorStore vectorStore;
    private final UserMapper userMapper;

    /**
     * 创建知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase create(String name, String description, Long creatorId) {
        // 检查名称是否重复
        long count = knowledgeBaseMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getName, name)
                        .eq(KnowledgeBase::getDeleted, DeletedEnum.NO)
        );
        if (count > 0) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
        }

        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name);
        kb.setDescription(description);
        kb.setCreatorId(creatorId);
        kb.setStatus(KnowledgeBaseStatusEnum.ENABLED); // 启用
        kb.setDeleted(DeletedEnum.NO);

        knowledgeBaseMapper.insert(kb);
        log.info("创建知识库成功: id={}, name={}", kb.getId(), name);
        return kb;
    }

    /**
     * 查询知识库详情
     */
    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || kb.getDeleted() == DeletedEnum.YES) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return kb;
    }

    /**
     * 校验知识库归属（非归属用户抛出 NOT_FOUND）
     * 管理员角色跳过归属校验，直接放行
     */
    public KnowledgeBase verifyOwnership(Long id) {
        KnowledgeBase kb = getById(id);
        Long currentUserId = AuthUtil.getLoginUserId();

        // Admin bypass: role == ADMIN may access any knowledge base
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser != null && currentUser.getRole() == UserRoleEnum.ADMIN) {
            return kb;
        }

        if (!currentUserId.equals(kb.getCreatorId())) {
            throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return kb;
    }

    /**
     * 分页查询知识库列表
     */
    public Page<KnowledgeBase> page(Long creatorId, int pageNum, int pageSize) {
        Page<KnowledgeBase> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getDeleted, DeletedEnum.NO)
                .eq(creatorId != null, KnowledgeBase::getCreatorId, creatorId)
                .orderByDesc(KnowledgeBase::getCreatedTime);

        return knowledgeBaseMapper.selectPage(page, wrapper);
    }

    /**
     * 查询用户的所有知识库
     */
    public List<KnowledgeBase> listByUserId(Long userId) {
        // Use the mapper XML method for custom query
        // Fallback to standard query for now
        return knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getCreatorId, userId)
                        .eq(KnowledgeBase::getDeleted, DeletedEnum.NO)
                        .orderByDesc(KnowledgeBase::getCreatedTime)
        );
    }

    /**
     * 更新知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBase update(Long id, String name, String description) {
        KnowledgeBase kb = getById(id);

        // 如果名称变更，检查新名称是否与其他知识库重复
        if (!kb.getName().equals(name)) {
            long count = knowledgeBaseMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeBase>()
                            .eq(KnowledgeBase::getName, name)
                            .eq(KnowledgeBase::getDeleted, DeletedEnum.NO)
                            .ne(KnowledgeBase::getId, id)
            );
            if (count > 0) {
                throw BizException.of(ErrorCode.KNOWLEDGE_BASE_NAME_EXISTS);
            }
        }

        kb.setName(name);
        kb.setDescription(description);
        knowledgeBaseMapper.updateById(kb);
        return kb;
    }

    /**
     * 删除知识库（级联清理：文档 + 向量 + 上传文件）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBase kb = getById(id);

        // 1. 查询知识库下所有未删除的文档
        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, id)
                        .eq(Document::getDeleted, DeletedEnum.NO)
        );

        // 2. 批量删除 PGVector 向量（按 doc_id 过滤）
        if (!docs.isEmpty()) {
            List<Long> docIds = docs.stream()
                    .map(Document::getId)
                    .toList();
            log.info("开始删除知识库向量: kbId={}, docIds={}", id, docIds);

            // PgVectorStore 通过 metadata::jsonb 过滤删除
            // 使用 FilterExpressionBuilder 构造 metadata.doc_id IN (...) 条件
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            FilterExpressionBuilder.Op condition = builder.in("doc_id", docIds);
            vectorStore.delete(condition.build());

            log.info("知识库向量删除完成: kbId={}", id);
        }

        // 3. 删除物理文件 + 逻辑删除文档 + 删除上传记录
        for (Document doc : docs) {
            // 删除物理文件
            if (doc.getFilePath() != null) {
                try {
                    fileStorageService.delete(doc.getFilePath());
                } catch (Exception e) {
                    log.warn("删除物理文件失败: docId={}, filePath={}", doc.getId(), doc.getFilePath(), e);
                }
            }
            // 逻辑删除文档
            doc.setDeleted(DeletedEnum.YES);
            documentMapper.updateById(doc);
        }

        // 4. 逻辑删除该知识库的上传记录
        uploadRecordMapper.update(
                null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.wangzs.rag.model.entity.UploadRecord>()
                        .eq(com.wangzs.rag.model.entity.UploadRecord::getKbId, id)
                        .set(com.wangzs.rag.model.entity.UploadRecord::getDeleted, DeletedEnum.YES)
        );

        // 5. 标记知识库为已删除
        kb.setDeleted(DeletedEnum.YES);
        kb.setStatus(KnowledgeBaseStatusEnum.DELETED); // 已删除
        knowledgeBaseMapper.updateById(kb);
        log.info("删除知识库完成: id={}, docsCount={}", id, docs.size());
    }

    /**
     * 启用/禁用知识库
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, KnowledgeBaseStatusEnum status) {
        KnowledgeBase kb = getById(id);
        kb.setStatus(status);
        knowledgeBaseMapper.updateById(kb);
    }
}
