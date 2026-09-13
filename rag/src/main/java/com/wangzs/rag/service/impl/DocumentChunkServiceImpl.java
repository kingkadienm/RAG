package com.wangzs.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.mapper.DocumentChunkMapper;
import com.wangzs.rag.model.entity.DocumentChunk;
import com.wangzs.rag.service.DocumentChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档分块 Service 实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkServiceImpl extends ServiceImpl<DocumentChunkMapper, DocumentChunk> implements DocumentChunkService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChunks(Long docId, Long kbId, Integer version, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        // 先删除旧分块（幂等：同一 version 的重复调用不会重复插入）
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getDocId, docId)
               .eq(DocumentChunk::getVersion, version);
        baseMapper.delete(wrapper);

        // 批量插入新分块
        List<DocumentChunk> entities = chunks.stream()
                .map(c -> {
                    DocumentChunk dc = new DocumentChunk();
                    dc.setDocId(docId);
                    dc.setKbId(kbId);
                    dc.setChunkIndex(c.getIndex());
                    dc.setTitle(c.getTitle());
                    dc.setSectionPath(c.getSectionPath());
                    dc.setContent(c.getContent());
                    dc.setTokenCount(c.getTokenCount());
                    dc.setCharLength(c.getLength());
                    dc.setEmbeddingText(c.getEmbeddingText());
                    dc.setVersion(version);
                    return dc;
                })
                .collect(Collectors.toList());

        saveBatch(entities, 500);
        log.info("持久化分块完成: docId={}, version={}, count={}", docId, version, entities.size());
    }

    @Override
    public List<Chunk> findChunksByDocId(Long docId) {
        List<DocumentChunk> entities = baseMapper.selectByDocId(docId);
        if (entities.isEmpty()) {
            return List.of();
        }

        return entities.stream()
                .map(this::toChunk)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByDocId(Long docId) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getDocId, docId);
        baseMapper.delete(wrapper);
        log.info("删除文档分块: docId={}", docId);
    }

    /**
     * DocumentChunk 转为业务 Chunk 对象
     */
    private Chunk toChunk(DocumentChunk dc) {
        return Chunk.builder()
                .index(dc.getChunkIndex())
                .content(dc.getContent())
                .length(dc.getCharLength() != null ? dc.getCharLength() : 0)
                .documentId(dc.getDocId())
                .kbId(dc.getKbId())
                .title(dc.getTitle())
                .sectionPath(dc.getSectionPath())
                .tokenCount(dc.getTokenCount() != null ? dc.getTokenCount() : 0)
                .embeddingText(dc.getEmbeddingText())
                .build();
    }
}
