package com.wangzs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangzs.rag.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档分块 Mapper
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 按文档 ID 查询所有分块（按 chunk_index 排序）
     */
    List<DocumentChunk> selectByDocId(@Param("docId") Long docId);

    /**
     * 按文档 ID + 版本查询分块
     */
    List<DocumentChunk> selectByDocIdAndVersion(@Param("docId") Long docId, @Param("version") Integer version);

    /**
     * 按文档 ID 删除所有分块
     */
    int deleteByDocId(@Param("docId") Long docId);

    /**
     * 按文档 ID + 版本删除分块
     */
    int deleteByDocIdAndVersion(@Param("docId") Long docId, @Param("version") Integer version);
}
