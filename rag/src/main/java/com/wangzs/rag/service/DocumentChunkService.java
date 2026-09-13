package com.wangzs.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wangzs.rag.chunk.Chunk;
import com.wangzs.rag.model.entity.DocumentChunk;

import java.util.List;

/**
 * 文档分块 Service 接口
 */
public interface DocumentChunkService extends IService<DocumentChunk> {

    /**
     * 持久化文档分块（先删旧分块再插入，保证幂等）
     *
     * @param docId   文档 ID
     * @param kbId    知识库 ID
     * @param version 文档版本号
     * @param chunks  分块列表
     */
    void saveChunks(Long docId, Long kbId, Integer version, List<Chunk> chunks);

    /**
     * 查询文档的所有分块（转为业务 Chunk 对象）
     *
     * @param docId 文档 ID
     * @return 分块列表
     */
    List<Chunk> findChunksByDocId(Long docId);

    /**
     * 删除文档的所有分块
     *
     * @param docId 文档 ID
     */
    void deleteByDocId(Long docId);
}
